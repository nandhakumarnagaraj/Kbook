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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillServiceImplTest {

    @Mock private BillRepository billRepository;
    @Mock private RestaurantProfileRepository profileRepository;

    private GenericSyncService genericSyncService;
    private BillServiceImpl billService;

    @Captor private ArgumentCaptor<Iterable<Bill>> billSaveCaptor;

    private static final Long TENANT_ID = 55L;
    private static final String DEVICE = "PHONE_1";

    @BeforeEach
    void setUp() {
        genericSyncService = new GenericSyncService();
        billService = new BillServiceImpl(billRepository, genericSyncService, profileRepository);
    }

    @Test
    void pushData_derivesLastResetDateFromCreatedAt() {
        Bill bill = new Bill();
        bill.setLocalId(1L);
        bill.setDeviceId(DEVICE);
        bill.setRestaurantId(TENANT_ID);
        bill.setCreatedAt(1704106800000L); 
        bill.setUpdatedAt(1704106800000L);

        RestaurantProfile profile = new RestaurantProfile();
        profile.setTimezone("Asia/Kolkata");
        when(profileRepository.findByRestaurantId(TENANT_ID)).thenReturn(Optional.of(profile));

        when(billRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(billRepository).saveAll(any());

        PushSyncResponse resp = billService.pushData(TENANT_ID, List.of(bill));

        assertThat(resp.getSuccessfulLocalIds()).contains(1L);
        verify(billRepository).saveAll(billSaveCaptor.capture());
        Bill saved = billSaveCaptor.getValue().iterator().next();
        
        assertThat(saved.getLastResetDate()).startsWith("2024-01-01");
    }

    @Test
    void pushData_missingCreatedAt_fallsBackToUpdatedAt() {
        Bill bill = new Bill();
        bill.setLocalId(1L);
        bill.setDeviceId(DEVICE);
        bill.setRestaurantId(TENANT_ID);
        long now = System.currentTimeMillis();
        bill.setUpdatedAt(now);
        

        RestaurantProfile profile = new RestaurantProfile();
        profile.setTimezone("Asia/Kolkata");
        when(profileRepository.findByRestaurantId(TENANT_ID)).thenReturn(Optional.of(profile));

        when(billRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(billRepository).saveAll(any());

        PushSyncResponse resp = billService.pushData(TENANT_ID, List.of(bill));

        verify(billRepository).saveAll(billSaveCaptor.capture());
        Bill saved = billSaveCaptor.getValue().iterator().next();
        
        String expectedDate = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(java.time.ZoneId.of("Asia/Kolkata"))
            .format(java.time.Instant.ofEpochMilli(now));
            
        assertThat(saved.getLastResetDate()).isEqualTo(expectedDate);
    }

    @Test
    void pushData_withNewYorkTimezone_derivesCorrectDate() {
        Bill bill = new Bill();
        bill.setLocalId(1L);
        bill.setDeviceId(DEVICE);
        bill.setRestaurantId(TENANT_ID);
        
        
        bill.setCreatedAt(1704074400000L); 

        RestaurantProfile profile = new RestaurantProfile();
        profile.setTimezone("America/New_York");
        when(profileRepository.findByRestaurantId(TENANT_ID)).thenReturn(Optional.of(profile));

        when(billRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(billRepository).saveAll(any());

        billService.pushData(TENANT_ID, List.of(bill));

        verify(billRepository).saveAll(billSaveCaptor.capture());
        Bill saved = billSaveCaptor.getValue().iterator().next();
        
        assertThat(saved.getLastResetDate()).startsWith("2023-12-31");
    }
}
