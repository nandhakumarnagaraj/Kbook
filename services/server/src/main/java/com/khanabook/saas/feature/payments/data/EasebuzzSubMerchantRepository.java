package com.khanabook.saas.feature.payments.data;

import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EasebuzzSubMerchantRepository extends JpaRepository<EasebuzzSubMerchant, Long> {

    Optional<EasebuzzSubMerchant> findByRestaurantId(Long restaurantId);

    Optional<EasebuzzSubMerchant> findBySubMerchantId(String subMerchantId);

    // contact_email is not unique: Easebuzz allows several sub-merchants (e.g. one owner's
    // outlets) to share a contact email, so this must not be a single-result finder.
    List<EasebuzzSubMerchant> findAllByContactEmail(String contactEmail);

    List<EasebuzzSubMerchant> findByStatus(String status);

    boolean existsByRestaurantId(Long restaurantId);
}
