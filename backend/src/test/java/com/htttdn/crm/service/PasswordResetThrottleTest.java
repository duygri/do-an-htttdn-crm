package com.htttdn.crm.service;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetThrottleTest {
    @Test void permitsAgainOnlyAfterSixtySecondsWithoutExtendingCooldown() {
        var clock = mock(Clock.class);
        var start = Instant.parse("2026-10-26T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        var throttle = new PasswordResetThrottle(clock, 2);
        assertTrue(throttle.permits("owner@example.com"));
        when(clock.instant()).thenReturn(start.plusSeconds(59));
        assertFalse(throttle.permits("owner@example.com"));
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertTrue(throttle.permits("owner@example.com"));
    }

    @Test void boundsUnknownAddressesAndRecoversCapacityAfterExpiry() {
        var clock = mock(Clock.class);
        var start = Instant.parse("2026-10-26T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        var throttle = new PasswordResetThrottle(clock, 2);
        assertTrue(throttle.permits("missing1@example.com"));
        assertTrue(throttle.permits("missing2@example.com"));
        assertFalse(throttle.permits("missing3@example.com"));
        assertFalse(throttle.permits("missing1@example.com"));
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertTrue(throttle.permits("missing3@example.com"));
    }

    @Test void concurrentRequestsForOneAddressAreAdmittedOnce() throws Exception {
        var throttle = new PasswordResetThrottle();
        try (var executor = Executors.newFixedThreadPool(8)) {
            Callable<Boolean> request = () -> throttle.permits("owner@example.com");
            var results = executor.invokeAll(Collections.nCopies(32, request));
            int admitted = 0;
            for (var result : results) if (result.get()) admitted++;
            assertEquals(1, admitted);
        }
    }
}
