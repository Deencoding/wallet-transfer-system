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
        List<?> result = redis.execute(
                SCRIPT,
                List.of("wallet:rate:" + key),
                Long.toString(policy.window().toMillis()));
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Redis returned an invalid rate-limit result");
        }
        long count = ((Number) result.get(0)).longValue();
        long ttlMillis = Math.max(0, ((Number) result.get(1)).longValue());
        return new RateLimitDecision(count <= policy.requests(), Math.max(1, (ttlMillis + 999) / 1000));
    }
}
