package com.khanabook.saas.repository;

import com.khanabook.saas.entity.FssaiRenewal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FssaiRenewalRepository extends JpaRepository<FssaiRenewal, Long> {
    Optional<FssaiRenewal> findByEasebuzzTxnId(String easebuzzTxnId);
    List<FssaiRenewal> findByRestaurantId(Long restaurantId);
}
