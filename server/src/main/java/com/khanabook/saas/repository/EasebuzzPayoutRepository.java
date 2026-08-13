package com.khanabook.saas.repository;

import com.khanabook.saas.entity.EasebuzzPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EasebuzzPayoutRepository extends JpaRepository<EasebuzzPayout, Long> {
    Optional<EasebuzzPayout> findByMerchantRequestId(String merchantRequestId);
    Optional<EasebuzzPayout> findByPayoutId(String payoutId);
    List<EasebuzzPayout> findByRestaurantId(Long restaurantId);
}
