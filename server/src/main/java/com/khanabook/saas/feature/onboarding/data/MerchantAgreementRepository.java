package com.khanabook.saas.feature.onboarding.data;

import com.khanabook.saas.feature.onboarding.entity.MerchantAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantAgreementRepository extends JpaRepository<MerchantAgreement, Long> {

    Optional<MerchantAgreement> findByRestaurantId(Long restaurantId);

    boolean existsByRestaurantId(Long restaurantId);
}
