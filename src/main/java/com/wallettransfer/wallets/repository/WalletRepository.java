package com.wallettransfer.wallets.repository;

import com.wallettransfer.shared.money.Currency;
import com.wallettransfer.wallets.model.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByOwnerIdAndCurrency(UUID ownerId, Currency currency);

    @Query("select w.id from Wallet w where w.ownerId=:ownerId and w.currency=:currency")
    Optional<UUID> findIdByOwnerIdAndCurrency(@Param("ownerId") UUID ownerId, @Param("currency") Currency currency);

    boolean existsByOwnerIdAndCurrency(UUID ownerId, Currency currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);
}
