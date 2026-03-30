package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.service.impl.BillServiceImpl;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillServiceTest {

    @Mock
    private BillRepository billRepository;
    
    @Mock
    private RestaurantProfileRepository profileRepository;

    private GenericSyncService genericSyncService;
    private BillServiceImpl billService;

    @Captor
    private ArgumentCaptor<Iterable<Bill>> listCaptor;

    private final Long AUTHENTICATED_RESTAURANT_ID = 99L;
    private final String DEVICE_ID = "TABLET_1";

    @BeforeEach
    void setUp() {
        genericSyncService = new GenericSyncService();
        billService = new BillServiceImpl(billRepository, genericSyncService, profileRepository);
    }

    private Bill createMobileBill(Long localId, Long updatedAt) {
        Bill bill = new Bill();
        bill.setLocalId(localId);
        bill.setUpdatedAt(updatedAt);
        bill.setDeviceId(DEVICE_ID);
        return bill;
    }

    @Test
    void givenExistingBill_whenMobileIsNewer_thenUpdateLwwSuccess() {
        Long oldServerTime = 1000L;
        Long newMobileTime = 2000L;

        Bill existingDbBill = new Bill();
        existingDbBill.setId(5L);
        existingDbBill.setUpdatedAt(oldServerTime);
        existingDbBill.setDeviceId(DEVICE_ID);
        existingDbBill.setLocalId(101L);

        Bill mobileBill = createMobileBill(101L, newMobileTime);

        RestaurantProfile profile = new RestaurantProfile();
        profile.setTimezone("Asia/Kolkata");
        when(profileRepository.findByRestaurantId(AUTHENTICATED_RESTAURANT_ID)).thenReturn(Optional.of(profile));

        
        when(billRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(
                eq(AUTHENTICATED_RESTAURANT_ID), eq(DEVICE_ID), anyList()))
                .thenReturn(List.of(existingDbBill));

        
        doAnswer(i -> i.getArgument(0)).when(billRepository).saveAll(anyList());

        PushSyncResponse response = billService.pushData(AUTHENTICATED_RESTAURANT_ID, List.of(mobileBill));

        
        verify(billRepository).saveAll(listCaptor.capture());
        Bill savedBill = listCaptor.getValue().iterator().next();

        assertThat(savedBill.getId()).isEqualTo(5L);
        assertThat(savedBill.getUpdatedAt()).isEqualTo(newMobileTime);
        assertThat(response.getSuccessfulLocalIds()).containsExactly(101L);
    }

    @Test
    void givenHackedPayload_whenInsertNewBill_thenForceTenantIsolation() {
        Long maliciousRestaurantId = 666L;
        Bill hackedMobileBill = createMobileBill(202L, 1000L);
        hackedMobileBill.setRestaurantId(maliciousRestaurantId);

        RestaurantProfile profile = new RestaurantProfile();
        profile.setTimezone("Asia/Kolkata");
        when(profileRepository.findByRestaurantId(AUTHENTICATED_RESTAURANT_ID)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> billService.pushData(AUTHENTICATED_RESTAURANT_ID, List.of(hackedMobileBill)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Permission denied");

        verify(billRepository, never()).saveAll(any());
    }
}
