package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminWriteService {

    private static final Logger log = LoggerFactory.getLogger(AdminWriteService.class);

    private final RestaurantProfileRepository profileRepository;

    public AdminWriteService(RestaurantProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional
    public void suspendBusiness(Long restaurantId) {
        RestaurantProfile profile = profileRepository
                .findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        profile.setIsSuspended(true);
        profile.setUpdatedAt(System.currentTimeMillis());
        profileRepository.save(profile);
        log.info("Business suspended: restaurantId={}", restaurantId);
    }

    @Transactional
    public void activateBusiness(Long restaurantId) {
        RestaurantProfile profile = profileRepository
                .findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        profile.setIsSuspended(false);
        profile.setUpdatedAt(System.currentTimeMillis());
        profileRepository.save(profile);
        log.info("Business activated: restaurantId={}", restaurantId);
    }
}
