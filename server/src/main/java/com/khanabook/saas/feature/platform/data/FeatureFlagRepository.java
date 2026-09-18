package com.khanabook.saas.feature.platform.data;

import com.khanabook.saas.feature.platform.data.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, String> {
}