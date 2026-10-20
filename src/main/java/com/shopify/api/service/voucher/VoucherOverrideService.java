package com.shopify.api.service.voucher;

import com.shopify.api.model.voucher.VoucherOverride;
import com.shopify.api.repository.voucher.VoucherOverrideRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Owner overrides. Only {@link com.shopify.api.controller.VoucherAdminController}
 * creates them, behind the owner key; the chat path only reads them.
 */
@Service
@Slf4j
public class VoucherOverrideService {

    static final Duration MAX_SESSION_OVERRIDE = Duration.ofHours(24);
    static final Duration DEFAULT_SESSION_OVERRIDE = Duration.ofHours(2);
    static final Duration MAX_PAUSE = Duration.ofDays(7);
    static final Duration DEFAULT_PAUSE = Duration.ofHours(24);

    private final VoucherOverrideRepository repository;
    private final VoucherClock clock;

    public VoucherOverrideService(VoucherOverrideRepository repository, VoucherClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public record Request(VoucherOverride.Type type, String target, BigDecimal value,
                          Integer durationMinutes, String reason) {}

    @Transactional
    public VoucherOverride create(Request req, String createdBy, String sourceIp) {
        if (req.type() == null) throw new IllegalArgumentException("type is required (POOL_RAISE, SESSION_AMOUNT, PAUSE)");
        if (req.reason() == null || req.reason().isBlank()) throw new IllegalArgumentException("reason is required");
        if (req.reason().length() > 500) throw new IllegalArgumentException("reason too long");
        Instant now = clock.now();
        LocalDate today = clock.today();
        VoucherOverride.VoucherOverrideBuilder b = VoucherOverride.builder()
            .type(req.type()).reason(req.reason().trim()).createdBy(createdBy)
            .sourceIp(sourceIp).createdAt(now);

        switch (req.type()) {
            case POOL_RAISE -> {
                BigDecimal v = requireValue(req.value(), VoucherRules.HARD_MAX_POOL_RAISE);
                // Raises today's pool only; it ends at Melbourne midnight like the pool itself.
                Instant end = clock.endOfDay(today);
                if (req.durationMinutes() != null) {
                    end = min(end, now.plus(duration(req.durationMinutes(), Duration.ofDays(1))));
                }
                b.value(v).poolDate(today).expiresAt(end);
            }
            case SESSION_AMOUNT -> {
                if (req.target() == null || req.target().isBlank() || req.target().length() > 255) {
                    throw new IllegalArgumentException("target (the chat session id) is required");
                }
                BigDecimal v = requireValue(req.value(), VoucherRules.HARD_MAX_AMOUNT);
                if (VoucherRules.floorToStep(v).compareTo(v) != 0) {
                    throw new IllegalArgumentException("value must be a multiple of 5");
                }
                Duration d = req.durationMinutes() == null ? DEFAULT_SESSION_OVERRIDE
                    : duration(req.durationMinutes(), MAX_SESSION_OVERRIDE);
                b.target(req.target().trim()).value(v).expiresAt(now.plus(d));
            }
            case PAUSE -> {
                Duration d = req.durationMinutes() == null ? DEFAULT_PAUSE : duration(req.durationMinutes(), MAX_PAUSE);
                b.expiresAt(now.plus(d));
            }
        }
        VoucherOverride saved = repository.save(b.build());
        log.warn("VOUCHER_OVERRIDE created id={} type={} target={} value={} expires={} by='{}' ip={} reason='{}'",
            saved.getId(), saved.getType(), saved.getTarget(), saved.getValue(), saved.getExpiresAt(),
            createdBy, sourceIp, saved.getReason());
        return saved;
    }

    @Transactional
    public Optional<VoucherOverride> revoke(Long id, String by) {
        return repository.findById(id).map(o -> {
            if (o.getRevokedAt() == null) {
                o.setRevokedAt(clock.now());
                o.setRevokedBy(by);
                repository.save(o);
                log.warn("VOUCHER_OVERRIDE revoked id={} type={} by='{}'", id, o.getType(), by);
            }
            return o;
        });
    }

    public boolean paused() {
        return !repository.findActive(VoucherOverride.Type.PAUSE, clock.now()).isEmpty();
    }

    /** Sum of active raises for that pool day. */
    public BigDecimal poolRaise(LocalDate day) {
        BigDecimal total = BigDecimal.ZERO;
        for (VoucherOverride o : repository.findActive(VoucherOverride.Type.POOL_RAISE, clock.now())) {
            if (day.equals(o.getPoolDate()) && o.getValue() != null) total = total.add(o.getValue());
        }
        return total;
    }

    /** The highest active per-session amount override, if any. */
    public Optional<BigDecimal> sessionAmount(String sessionId) {
        if (sessionId == null) return Optional.empty();
        return repository.findActive(VoucherOverride.Type.SESSION_AMOUNT, clock.now()).stream()
            .filter(o -> sessionId.equals(o.getTarget()) && o.getValue() != null)
            .map(VoucherOverride::getValue)
            .max(BigDecimal::compareTo);
    }

    public List<VoucherOverride> recent() {
        return repository.findTop50ByOrderByCreatedAtDesc();
    }

    private static BigDecimal requireValue(BigDecimal v, BigDecimal max) {
        if (v == null || v.signum() <= 0 || v.compareTo(max) > 0) {
            throw new IllegalArgumentException("value must be > 0 and <= " + max);
        }
        return v;
    }

    private static Duration duration(int minutes, Duration max) {
        if (minutes <= 0) throw new IllegalArgumentException("durationMinutes must be positive");
        Duration d = Duration.ofMinutes(minutes);
        if (d.compareTo(max) > 0) throw new IllegalArgumentException("durationMinutes must be <= " + max.toMinutes());
        return d;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
