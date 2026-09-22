package com.wallettransfer.shared.security.repository;

import com.wallettransfer.shared.security.model.RateLimitDecision;
import com.wallettransfer.shared.security.model.RateLimitPolicy;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRateLimitRepository {

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]); "
                    + "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
                    + "local ttl = redis.call('PTTL', KEYS[1]); return {current, ttl};",
            List.class);

    private final StringRedisTemplate redis;

    public RedisRateLimitRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public RateLimitDecision consume(String key, RateLimitPolicy policy) {
        List<String> keys = List.of("wallet:rate:" + key);
        String windowMillis = Long.toString(policy.window().toMillis());
        List<?> result = redis.execute(SCRIPT, keys, windowMillis);
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Redis returned an invalid rate-limit result");
        }
        long count = ((Number) result.get(0)).longValue();
        long ttlMillis = Math.max(0, ((Number) result.get(1)).longValue());
        long retryAfterSeconds = Math.max(1, (ttlMillis + 999) / 1000);
        return new RateLimitDecision(count <= policy.requests(), retryAfterSeconds);
    }
}
