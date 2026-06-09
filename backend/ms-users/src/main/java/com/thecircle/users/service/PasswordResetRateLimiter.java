package com.thecircle.users.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window per-IP throttle for {@code POST /api/auth/forgot-password}.
 *
 * <p>Stores a deque of request timestamps per source IP and rejects when more
 * than {@code maxAttempts} fall inside the {@code windowSeconds} window. Stale
 * entries are evicted on every check and by a periodic sweep so the map cannot
 * grow unbounded under address rotation.
 *
 * <h3>Production caveats (documented as future work for the TFM)</h3>
 * <ul>
 *   <li><b>Single-instance only.</b> State lives in the JVM heap; horizontally
 *       scaling ms-users multiplies the effective quota by the replica count.</li>
 *   <li><b>Lost on restart.</b> Deploys, OOMs and crashes reset the counters,
 *       so an attacker monitoring uptime could time bursts around restarts.</li>
 *   <li><b>App-layer only.</b> The real first line of defence in production is
 *       the edge (CDN / WAF rate-limiting rules) before traffic reaches the
 *       backend at all.</li>
 * </ul>
 *
 * <p>Migration path: replace the {@link ConcurrentHashMap} with a Redis-backed
 * implementation using atomic {@code INCR} + {@code EXPIRE} (e.g. Bucket4j with
 * the Redisson backend) and the rest of the call sites stay unchanged.
 *
 * <h3>Role in the password-reset brute-force defence</h3>
 * <p>The per-session OTP cap in {@code AuthOtpService} (max 5 attempts on a
 * single sessionId) does NOT bound the total number of guesses an attacker can
 * make against a victim's email — they can call {@code /forgot-password}
 * repeatedly to mint fresh sessionIds, each with its own attempt budget. This
 * IP-level rate limit is what actually bounds the brute-force surface. Loosening
 * it (raising {@code max-attempts} or shortening the window) without adding a
 * per-email throttle re-opens the attack.
 */
@Service
@Slf4j
public class PasswordResetRateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean warnedAboutBlankIp =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Value("${auth.password-reset.rate-limit.max-attempts:3}")
    private int maxAttempts;

    @Value("${auth.password-reset.rate-limit.window-seconds:900}")
    private int windowSeconds;

    /**
     * Records the attempt and returns {@code true} when the caller is within the
     * quota for the current window, {@code false} when it is rate-limited.
     */
    public boolean tryAcquire(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            // Unknown source — fail closed to avoid a free bypass. Warn once so a
            // misconfigured proxy (Servlet container behind something that strips
            // the source IP) is debuggable from the logs instead of looking like
            // "rate limiter always rejects" from the user's perspective.
            if (warnedAboutBlankIp.compareAndSet(false, true)) {
                log.warn("PasswordResetRateLimiter received a blank client IP — the rate limiter "
                        + "is rejecting all requests until the upstream proxy supplies a source IP. "
                        + "Check the reverse proxy / Servlet container configuration.");
            }
            return false;
        }
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofSeconds(windowSeconds));
        Deque<Instant> bucket = hits.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        synchronized (bucket) {
            evictBefore(bucket, cutoff);
            if (bucket.size() >= maxAttempts) {
                return false;
            }
            bucket.addLast(now);
            return true;
        }
    }

    /**
     * Periodic compaction so IPs that stopped calling do not occupy memory
     * forever. Cheap: O(n) on the keyset and the deques are tiny.
     */
    @Scheduled(fixedDelayString = "${auth.password-reset.rate-limit.cleanup-interval-ms:600000}")
    public void evictStale() {
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(windowSeconds));
        Iterator<ConcurrentHashMap.Entry<String, Deque<Instant>>> it = hits.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            ConcurrentHashMap.Entry<String, Deque<Instant>> entry = it.next();
            Deque<Instant> bucket = entry.getValue();
            synchronized (bucket) {
                evictBefore(bucket, cutoff);
                if (bucket.isEmpty()) {
                    it.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.debug("PasswordResetRateLimiter swept {} idle buckets", removed);
        }
    }

    private static void evictBefore(Deque<Instant> bucket, Instant cutoff) {
        while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) {
            bucket.pollFirst();
        }
    }
}
