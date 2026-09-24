package com.wallettransfer.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallettransfer.shared.security.model.RateLimitPolicy;
import com.wallettransfer.shared.security.repository.RedisRateLimitRepository;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisRateLimitIntegrationTest {

    private static final DockerImageName IMAGE = DockerImageName.parse("redis:7.4-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(IMAGE).withExposedPorts(6379);

    @Test
    void concurrentConsumersCannotExceedTheAtomicAllowance() {
        var configuration = new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        var connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate stringRedisTemplate = new StringRedisTemplate(connectionFactory);
            var repository = new RedisRateLimitRepository(stringRedisTemplate);
            var policy = new RateLimitPolicy("integration", 10, Duration.ofMinutes(1));
            String key = "concurrent:" + UUID.randomUUID();
            int attempts = 40;
            var start = new CountDownLatch(1);
            var completed = new AtomicInteger();
            var allowed = new AtomicInteger();
            var rejected = new AtomicInteger();
            var failures = new ConcurrentLinkedQueue<Throwable>();
            var executor = Executors.newFixedThreadPool(16);
            try {
                for (int index = 0; index < attempts; index++) {
                    executor.submit(() -> {
                        try {
                            start.await();
                            if (repository.consume(key, policy).allowed()) allowed.incrementAndGet();
                            else rejected.incrementAndGet();
                        } catch (Throwable failure) {
                            failures.add(failure);
                        } finally {
                            completed.incrementAndGet();
                        }
                    });
                }
                start.countDown();
                Awaitility.await().atMost(Duration.ofSeconds(20)).untilAtomic(completed, Matchers.equalTo(attempts));
            } finally {
                executor.shutdownNow();
            }
            assertThat(failures).isEmpty();
            assertThat(allowed).hasValue(10);
            assertThat(rejected).hasValue(30);
            assertThat(repository.consume(key, policy).retryAfterSeconds()).isPositive();
        } finally {
            connectionFactory.destroy();
        }
    }
}
