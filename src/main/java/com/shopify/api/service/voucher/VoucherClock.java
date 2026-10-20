package com.shopify.api.service.voucher;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Time source for vouchers. The pool day is the Australia/Melbourne calendar day. */
@Component
public class VoucherClock {

    public static final ZoneId ZONE = ZoneId.of("Australia/Melbourne");

    private volatile Clock clock = Clock.system(ZONE);

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** The instant the given pool day ends (next Melbourne midnight). */
    public Instant endOfDay(LocalDate day) {
        return day.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    public Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }

    public ZonedDateTime local(Instant instant) {
        return instant.atZone(ZONE);
    }

    /** Tests only. */
    public void setClock(Clock clock) {
        this.clock = clock;
    }
}
