package com.khanabook.saas.repository;

import com.khanabook.saas.entity.OtpRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpRequestRepository extends JpaRepository<OtpRequest, Long> {
    Optional<OtpRequest> findByChallengeKey(String challengeKey);
    void deleteByChallengeKey(String challengeKey);
    void deleteByExpiresAtBefore(Long timestamp);
}
