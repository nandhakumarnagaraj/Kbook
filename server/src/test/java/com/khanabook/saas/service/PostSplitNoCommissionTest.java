package com.khanabook.saas.service;

import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.billing.service.PostSplitService;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.payments.service.EasebuzzApiClient;
import com.khanabook.saas.feature.payments.service.SubMerchantService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostSplitNoCommissionTest {

    @Test
    void sendsFullBillToRestaurantEvenIfLegacyRateIsPositive() {
        EasebuzzApiClient api = mock(EasebuzzApiClient.class);
        BillRepository bills = mock(BillRepository.class);
        SubMerchantService subMerchants = mock(SubMerchantService.class);
        PostSplitService service = new PostSplitService(api, bills, subMerchants);

        Bill bill = new Bill();
        bill.setId(10L);
        bill.setRestaurantId(20L);
        bill.setTotalAmount(new BigDecimal("100.00"));
        bill.setDailyOrderDisplay("001");
        EasebuzzSubMerchant subMerchant = new EasebuzzSubMerchant();
        subMerchant.setStatus("ACTIVE");
        subMerchant.setSplitLabel("restaurant_20");
        subMerchant.setCommissionRate(new BigDecimal("3.00"));

        when(bills.findById(10L)).thenReturn(Optional.of(bill));
        when(subMerchants.getByRestaurantId(20L)).thenReturn(subMerchant);
        when(api.updateTransactionSplit(any(), any(), any(), any(), any()))
                .thenReturn(Map.of("status", "success"));

        service.createPostSplitAsync(10L, "EASEBUZZ123", "KB123");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> splits = ArgumentCaptor.forClass(List.class);
        verify(api).updateTransactionSplit(any(), any(), any(), any(), splits.capture());
        assertEquals("100.00", splits.getValue().get(0).get("amount"));
        assertEquals(0, bill.getCommissionAmount().compareTo(BigDecimal.ZERO));
    }
}
