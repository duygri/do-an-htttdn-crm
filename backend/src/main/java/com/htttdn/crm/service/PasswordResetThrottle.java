package com.htttdn.crm.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounds request tracking and applies the same cooldown to unknown addresses. */
final class PasswordResetThrottle {
    private final Clock clock;
    private final int capacity;
    private final Map<String, Instant> requests = new LinkedHashMap<>();

    PasswordResetThrottle() {
        this(Clock.systemUTC(), 10_000);
    }

    PasswordResetThrottle(Clock clock, int capacity) {
        this.clock = clock;
        this.capacity = capacity;
    }

    synchronized boolean permits(String email) {
        Instant now = clock.instant();
        var entries = requests.values().iterator();
        while (entries.hasNext()) {
            if (entries.next().isAfter(now.minusSeconds(60))) break;
            entries.remove();
        }
        String key = AuthService.hash(email);
        if (requests.containsKey(key) || requests.size() >= capacity) return false;
        requests.put(key, now);
        return true;
    }
}
