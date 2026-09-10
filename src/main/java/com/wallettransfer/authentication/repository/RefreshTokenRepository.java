package com.wallettransfer.authentication.repository;

import com.wallettransfer.authentication.model.RefreshToken;
import com.wallettransfer.authentication.model.RefreshTokenRevocationReason;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.jwtId = :jwtId")
    Optional<RefreshToken> findByJwtIdForUpdate(@Param("jwtId") UUID jwtId);

    @Modifying
    @Query("update RefreshToken t set t.status = 'REVOKED', t.revocationReason = :reason, "
            + "t.revokedAt = :now, t.lastUsedAt = :now where t.familyId = :familyId and t.status = 'ACTIVE'")
    int revokeActiveFamily(
            @Param("familyId") UUID familyId,
            @Param("reason") RefreshTokenRevocationReason reason,
            @Param("now") Instant now);
}
