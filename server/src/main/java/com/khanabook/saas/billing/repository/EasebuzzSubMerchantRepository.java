package com.khanabook.saas.billing.repository;

import com.khanabook.saas.billing.entity.EasebuzzSubMerchant;
import com.khanabook.saas.billing.entity.SubMerchantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EasebuzzSubMerchantRepository extends JpaRepository<EasebuzzSubMerchant, Long> {
    Optional<EasebuzzSubMerchant> findByRestaurantId(Long restaurantId);
    Optional<EasebuzzSubMerchant> findBySubMerchantId(String subMerchantId);
    List<EasebuzzSubMerchant> findAllByOrderByUpdatedAtDesc();
    List<EasebuzzSubMerchant> findAllByStatusOrderByUpdatedAtDesc(SubMerchantStatus status);
}
