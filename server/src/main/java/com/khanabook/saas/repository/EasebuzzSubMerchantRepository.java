package com.khanabook.saas.repository;

import com.khanabook.saas.entity.EasebuzzSubMerchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EasebuzzSubMerchantRepository extends JpaRepository<EasebuzzSubMerchant, Long> {

    Optional<EasebuzzSubMerchant> findByRestaurantId(Long restaurantId);

    Optional<EasebuzzSubMerchant> findBySubMerchantId(String subMerchantId);

    List<EasebuzzSubMerchant> findByStatus(String status);

    boolean existsByRestaurantId(Long restaurantId);
}
