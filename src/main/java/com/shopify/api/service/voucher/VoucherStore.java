package com.shopify.api.service.voucher;

import com.shopify.api.model.voucher.VoucherClaim;
import com.shopify.api.model.voucher.VoucherLedgerEntry;
import com.shopify.api.model.voucher.VoucherOffer;
import com.shopify.api.model.voucher.VoucherPoolDay;
import com.shopify.api.model.voucher.VoucherStatus;
import com.shopify.api.repository.voucher.VoucherClaimRepository;
import com.shopify.api.repository.voucher.VoucherLedgerRepository;
import com.shopify.api.repository.voucher.VoucherOfferRepository;
import com.shopify.api.repository.voucher.VoucherPoolDayRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Every voucher DB write, each in its own transaction. The pool is only ever
 * changed together with a ledger status transition (compare-and-set), so a value
 * is reserved once and returned at most once.
 */
@Component
@Slf4j
public class VoucherStore {

    public enum ReserveStatus { OK, SESSION_USED, CUSTOMER_USED, IP_LIMIT, POOL_EMPTY }

    public record ReserveRequest(String code, BigDecimal desiredAmount, BigDecimal poolLimit, LocalDate day,
                                 String sessionId, List<String> customerKeys, String ipHash, int maxPerIp,
                                 BigDecimal eligibleSubtotal, BigDecimal pctForMinimum, BigDecimal minSubtotal,
                                 String reason, Instant issuedAt, Instant expiresAt) {}

    public record Reservation(ReserveStatus status, VoucherLedgerEntry entry) {}

    private final VoucherPoolDayRepository poolRepository;
    private final VoucherLedgerRepository ledgerRepository;
    private final VoucherClaimRepository claimRepository;
    private final VoucherOfferRepository offerRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public VoucherStore(VoucherPoolDayRepository poolRepository, VoucherLedgerRepository ledgerRepository,
                        VoucherClaimRepository claimRepository, VoucherOfferRepository offerRepository) {
        this.poolRepository = poolRepository;
        this.ledgerRepository = ledgerRepository;
        this.claimRepository = claimRepository;
        this.offerRepository = offerRepository;
    }

    static String sessionClaim(String sessionId) {
        return "s:" + VoucherRules.sha256(sessionId);
    }

    static String customerClaim(LocalDate day, String customerKey) {
        return "c:" + day + ":" + customerKey;
    }

    /**
     * Claim the session/customer, reserve from the pool and write a PENDING ledger
     * row, all or nothing. A lost uniqueness race surfaces as a
     * DataIntegrityViolationException / PersistenceException (the transaction rolls back).
     */
    @Transactional
    public Reservation reserve(ReserveRequest r) {
        if (claimRepository.existsById(sessionClaim(r.sessionId()))) {
            return new Reservation(ReserveStatus.SESSION_USED, null);
        }
        for (String key : r.customerKeys()) {
            if (claimRepository.existsById(customerClaim(r.day(), key))) {
                return new Reservation(ReserveStatus.CUSTOMER_USED, null);
            }
        }
        if (r.ipHash() != null && ledgerRepository.countLiveForIp(r.ipHash(), r.day()) >= r.maxPerIp()) {
            return new Reservation(ReserveStatus.IP_LIMIT, null);
        }

        poolRepository.ensureDay(r.day());
        BigDecimal reserved = currentReserved(r.day());
        BigDecimal remaining = r.poolLimit().subtract(reserved);
        BigDecimal amount = VoucherRules.min(r.desiredAmount(), VoucherRules.floorToStep(remaining));
        if (amount.compareTo(VoucherRules.STEP) < 0) {
            return new Reservation(ReserveStatus.POOL_EMPTY, null);
        }
        if (poolRepository.reserve(r.day(), amount, r.poolLimit()) != 1) {
            return new Reservation(ReserveStatus.POOL_EMPTY, null); // lost a race for the last dollars
        }

        VoucherLedgerEntry entry = VoucherLedgerEntry.builder()
            .code(r.code())
            .amount(amount.setScale(2))
            .poolDate(r.day())
            .sessionId(r.sessionId())
            .customerKey(r.customerKeys().isEmpty() ? null : r.customerKeys().get(0))
            .ipHash(r.ipHash())
            .cartSubtotal(r.eligibleSubtotal())
            .minSubtotal(VoucherRules.codeMinSubtotal(amount, r.pctForMinimum(), r.minSubtotal()))
            .reason(truncate(r.reason(), 500))
            .status(VoucherStatus.PENDING)
            .issuedAt(r.issuedAt())
            .expiresAt(r.expiresAt())
            .build();
        entityManager.persist(entry);
        entityManager.flush();

        // persist (not merge): an existing key must fail, never be overwritten.
        entityManager.persist(new VoucherClaim(sessionClaim(r.sessionId()), entry.getId(), r.issuedAt()));
        for (String key : r.customerKeys()) {
            entityManager.persist(new VoucherClaim(customerClaim(r.day(), key), entry.getId(), r.issuedAt()));
        }
        entityManager.flush();
        return new Reservation(ReserveStatus.OK, entry);
    }

    private BigDecimal currentReserved(LocalDate day) {
        VoucherPoolDay row = entityManager.find(VoucherPoolDay.class, day);
        if (row != null) entityManager.refresh(row);
        return row == null ? BigDecimal.ZERO : row.getReserved();
    }

    @Transactional
    public boolean markIssued(Long id, String discountId) {
        return ledgerRepository.markIssued(id, discountId) == 1;
    }

    @Transactional
    public boolean markRedeemed(Long id, String orderId, BigDecimal orderTotal, Instant now) {
        return ledgerRepository.markRedeemed(id, orderId, orderTotal, now) == 1;
    }

    /**
     * Move the entry from {@code from} to a value-returning status (EXPIRED, REVOKED,
     * FAILED) and give its amount back to its own day's pool. FAILED also drops the
     * claims so the customer may try again. Returns false if someone else moved it first.
     */
    @Transactional
    public boolean releaseTo(VoucherLedgerEntry entry, VoucherStatus from, VoucherStatus to, Instant now) {
        if (to != VoucherStatus.EXPIRED && to != VoucherStatus.REVOKED && to != VoucherStatus.FAILED) {
            throw new IllegalArgumentException("not a releasing status: " + to);
        }
        if (ledgerRepository.transition(entry.getId(), from, to, now) != 1) return false;
        if (poolRepository.release(entry.getPoolDate(), entry.getAmount()) != 1) {
            log.error("voucher: pool for {} had less than {} reserved while releasing ledger {}",
                entry.getPoolDate(), entry.getAmount(), entry.getId());
        }
        if (to == VoucherStatus.FAILED) claimRepository.deleteByLedgerId(entry.getId());
        return true;
    }

    @Transactional
    public void recordAttempt(Long id, String error) {
        ledgerRepository.recordAttempt(id, truncate(error, 500));
    }

    @Transactional
    public void recordOffer(VoucherOffer offer) {
        offerRepository.save(offer);
    }

    @Transactional(readOnly = true)
    public BigDecimal reserved(LocalDate day) {
        return poolRepository.findById(day).map(VoucherPoolDay::getReserved).orElse(BigDecimal.ZERO);
    }

    static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
