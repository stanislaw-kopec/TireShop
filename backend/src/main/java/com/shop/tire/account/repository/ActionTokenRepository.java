package com.shop.tire.account.repository;

import com.shop.tire.account.entity.AccountActionToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface ActionTokenRepository extends JpaRepository<AccountActionToken, UUID> {
    @Query("select t.accountId from AccountActionToken t where t.tokenHash = :hash")
    Optional<UUID> findAccountIdByHash(String hash);
    Optional<AccountActionToken> findByTokenHash(String hash);

    @Modifying
    @Query("update AccountActionToken t set t.revokedAt = :now where t.accountId = :accountId and t.usedAt is null and t.revokedAt is null")
    void revokeUnused(UUID accountId, Instant now);
}
