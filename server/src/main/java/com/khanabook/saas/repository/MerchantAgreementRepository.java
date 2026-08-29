package com.khanabook.saas.repository;

import com.khanabook.saas.entity.MerchantAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantAgreementRepository extends JpaRepository<MerchantAgreement, Long> {

    Optional<MerchantAgreement> findByRestaurantId(Long restaurantId);

    boolean existsByRestaurantId(Long restaurantId);
}
