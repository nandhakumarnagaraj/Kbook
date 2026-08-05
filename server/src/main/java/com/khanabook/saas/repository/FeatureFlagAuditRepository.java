package com.khanabook.saas.repository;

import com.khanabook.saas.entity.FeatureFlagAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeatureFlagAuditRepository extends JpaRepository<FeatureFlagAudit, Long> {

    List<FeatureFlagAudit> findByFlagKeyOrderByChangedAtDesc(String flagKey);
}