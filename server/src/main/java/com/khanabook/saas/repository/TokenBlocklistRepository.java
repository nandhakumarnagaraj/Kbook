package com.khanabook.saas.repository;

import com.khanabook.saas.entity.TokenBlocklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TokenBlocklistRepository extends JpaRepository<TokenBlocklist, String> {

    boolean existsByJti(String jti);

    @Modifying
    @Transactional
    @Query("DELETE FROM TokenBlocklist t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(long now);
}
