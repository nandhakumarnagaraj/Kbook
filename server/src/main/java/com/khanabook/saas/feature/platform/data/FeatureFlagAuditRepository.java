package com.khanabook.saas.feature.platform.data;

import com.khanabook.saas.feature.platform.data.FeatureFlagAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeatureFlagAuditRepository extends JpaRepository<FeatureFlagAudit, Long> {

    List<FeatureFlagAudit> findByFlagKeyOrderByChangedAtDesc(String flagKey);
}