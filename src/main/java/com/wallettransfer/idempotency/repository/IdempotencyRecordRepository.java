package com.wallettransfer.idempotency.repository;

import com.wallettransfer.idempotency.model.IdempotencyRecord;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO idempotency_records (
                        id,
                        client_identity,
                        endpoint,
                        idempotency_key,
                        request_fingerprint,
                        status,
                        created_at,
                        updated_at,
                        expires_at
                    ) VALUES (
                        :id,
                        :userId,
                        :endpoint,
                        :key,
                        :fingerprint,
                        'PROCESSING',
                        :now,
                        :now,
                        :expires
                    )
                    ON CONFLICT (client_identity, endpoint, idempotency_key) DO NOTHING
                    """,
            nativeQuery = true)
    int claim(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("endpoint") String endpoint,
            @Param("key") String key,
            @Param("fingerprint") String fingerprint,
            @Param("now") Instant now,
            @Param("expires") Instant expires);

    Optional<IdempotencyRecord> findByClientIdentityAndEndpointAndIdempotencyKey(
            UUID userId, String endpoint, String key);
}
