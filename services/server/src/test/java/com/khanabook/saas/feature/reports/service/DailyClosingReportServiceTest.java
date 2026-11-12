package com.khanabook.saas.feature.reports.service;

import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.notifications.data.EmailOutbox;
import com.khanabook.saas.feature.notifications.data.EmailOutboxRepository;
import com.khanabook.saas.feature.notifications.service.EmailNotificationService;
import com.khanabook.saas.feature.reports.dto.DailyClosingReportData;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.shifts.data.CashierShift;
import com.khanabook.saas.feature.shifts.data.CashierShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyClosingReportServiceTest {

    @Mock private BillRepository billRepository;
    @Mock private CashierShiftRepository cashierShiftRepository;
    @Mock private RestaurantProfileRepository restaurantProfileRepository;
    @Mock private EmailOutboxRepository emailOutboxRepository;
    @Mock private EmailNotificationService emailNotificationService;
    @Mock private TemplateEngine templateEngine;

    private DailyClosingReportService service;

    private final Long tenantId = 100L;
    private final String businessDate = "2026-11-06";

    @BeforeEach
    void setUp() {
        service = new DailyClosingReportService(
                billRepository,
                cashierShiftRepository,
                restaurantProfileRepository,
                emailOutboxRepository,
                emailNotificationService,
                templateEngine
        );
    }

    private RestaurantProfile createMockProfile() {
        RestaurantProfile profile = new RestaurantProfile();
        profile.setId(1L);
        profile.setRestaurantId(tenantId);
        profile.setShopName("Khana Paradise");
        profile.setShopAddress("123 Food Street, Bangalore");
        profile.setWhatsappNumber("9876543210");
        profile.setEmail("owner@khanaparadise.com");
        profile.setOpeningTime(LocalTime.of(9, 0));
        profile.setClosingTime(LocalTime.of(22, 30));
        profile.setBusinessDayCutoffTime(LocalTime.of(4, 0));
        profile.setCurrency("₹");
        profile.setTimezone("Asia/Kolkata");
        return profile;
    }

    private Bill createBill(Long id, BigDecimal total, String paymentMode, String status,
                           BigDecimal cgst, BigDecimal sgst, BigDecimal customTax) {
        Bill bill = new Bill();
        bill.setId(id);
        bill.setRestaurantId(tenantId);
        bill.setTotalAmount(total);
        bill.setPaymentMode(paymentMode);
        bill.setOrderStatus(status);
        bill.setCgstAmount(cgst);
        bill.setSgstAmount(sgst);
        bill.setCustomTaxAmount(customTax);
        bill.setLastResetDate(businessDate);
        bill.setIsDeleted(false);
        return bill;
    }

    @Test
    void aggregatesFinancialMetricsCorrectly() {
        RestaurantProfile profile = createMockProfile();
        when(restaurantProfileRepository.findByRestaurantId(tenantId)).thenReturn(Optional.of(profile));

        Bill bill1 = createBill(1L, new BigDecimal("500.00"), "CASH", "COMPLETED", new BigDecimal("12.50"), new BigDecimal("12.50"), BigDecimal.ZERO);
        Bill bill2 = createBill(2L, new BigDecimal("1000.00"), "UPI", "PAID", new BigDecimal("25.00"), new BigDecimal("25.00"), new BigDecimal("10.00"));
        Bill bill3 = createBill(3L, new BigDecimal("200.00"), "CASH", "CANCELLED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(billRepository.findByRestaurantIdAndLastResetDateAndIsDeletedFalse(tenantId, businessDate))
                .thenReturn(List.of(bill1, bill2, bill3));

        CashierShift shift = new CashierShift();
        shift.setCashierName("John Doe");
        shift.setTerminalSeries("A");
        shift.setOpeningFloat(new BigDecimal("1000.00"));
        shift.setDeclaredCash(new BigDecimal("1500.00"));
        shift.setExpectedCash(new BigDecimal("1500.00"));
        shift.setCashVariance(BigDecimal.ZERO);
        shift.setTotalRevenue(new BigDecimal("1500.00"));
        shift.setTotalOrders(2);
        shift.setStatus("CLOSED");

        when(cashierShiftRepository.findByRestaurantIdAndBusinessDateAndIsDeletedFalseOrderByOpenedAtAsc(eq(tenantId), any(LocalDate.class)))
                .thenReturn(List.of(shift));

        DailyClosingReportData data = service.generateReportData(tenantId, businessDate);

        assertThat(data.getRestaurantName()).isEqualTo("Khana Paradise");
        assertThat(data.getTotalOrders()).isEqualTo(3);
        assertThat(data.getCompletedOrders()).isEqualTo(2);
        assertThat(data.getCancelledOrders()).isEqualTo(1);
        assertThat(data.getGrossSales()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(data.getCgst()).isEqualByComparingTo(new BigDecimal("37.50"));
        assertThat(data.getSgst()).isEqualByComparingTo(new BigDecimal("37.50"));
        assertThat(data.getCustomTax()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(data.getTotalTax()).isEqualByComparingTo(new BigDecimal("85.00"));
        assertThat(data.getNetSales()).isEqualByComparingTo(new BigDecimal("1415.00"));

        assertThat(data.getCashTotal()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(data.getCashCount()).isEqualTo(1);
        assertThat(data.getUpiTotal()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(data.getUpiCount()).isEqualTo(1);

        assertThat(data.getShifts()).hasSize(1);
        assertThat(data.getShifts().get(0).getCashierName()).isEqualTo("John Doe");
        assertThat(data.getShifts().get(0).getCashVariance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void generatesAndQueuesReportInOutbox() throws Exception {
        RestaurantProfile profile = createMockProfile();
        when(restaurantProfileRepository.findByRestaurantId(tenantId)).thenReturn(Optional.of(profile));
        when(emailOutboxRepository.findByReportTypeAndReferenceKeyAndRevisionAndRecipient(
                EmailOutbox.REPORT_TYPE_DAILY_EOD, businessDate, 1, "owner@khanaparadise.com"))
                .thenReturn(Optional.empty());

        when(billRepository.findByRestaurantIdAndLastResetDateAndIsDeletedFalse(tenantId, businessDate))
                .thenReturn(List.of());
        when(cashierShiftRepository.findByRestaurantIdAndBusinessDateAndIsDeletedFalseOrderByOpenedAtAsc(eq(tenantId), any(LocalDate.class)))
                .thenReturn(List.of());

        when(templateEngine.process(eq("email/daily-closing-report"), any(Context.class)))
                .thenReturn("<html>Daily Report</html>");

        when(emailOutboxRepository.save(any(EmailOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmailOutbox result = service.generateAndSendReport(tenantId, businessDate, false);

        assertThat(result).isNotNull();
        assertThat(result.getRecipient()).isEqualTo("owner@khanaparadise.com");
        assertThat(result.getReportType()).isEqualTo(EmailOutbox.REPORT_TYPE_DAILY_EOD);
        assertThat(result.getReferenceKey()).isEqualTo(businessDate);
        assertThat(result.getStatus()).isEqualTo(EmailOutbox.STATUS_SENT);

        verify(emailNotificationService).sendDailyClosingReport(
                eq("owner@khanaparadise.com"),
                contains("Daily Closing Report"),
                contains("<html>Daily Report</html>")
        );
    }

    /**
     * Regression: the outbox is unique on (report_type, reference_key, revision, recipient), so a
     * second INSERT for an already-recorded day throws on "uq_email_outbox_revision". The sweep used
     * to re-insert every FAILED/DEAD_LETTER day every 15 minutes and produce that error endlessly.
     * An existing row must be returned as-is, with no further insert and no second send.
     */
    @Test
    void returnsExistingOutboxRowInsteadOfInsertingDuplicateForNonSentStatus() throws Exception {
        RestaurantProfile profile = createMockProfile();
        when(restaurantProfileRepository.findByRestaurantId(tenantId)).thenReturn(Optional.of(profile));

        EmailOutbox existing = EmailOutbox.builder()
                .id(42L)
                .restaurantId(tenantId)
                .reportType(EmailOutbox.REPORT_TYPE_DAILY_EOD)
                .referenceKey(businessDate)
                .revision(1)
                .recipient("owner@khanaparadise.com")
                .subject("existing")
                .htmlBody("<html>existing</html>")
                .status(EmailOutbox.STATUS_DEAD_LETTER)
                .attemptCount(5)
                .maxAttempts(5)
                .createdAt(0L)
                .updatedAt(0L)
                .build();
        when(emailOutboxRepository.findByReportTypeAndReferenceKeyAndRevisionAndRecipient(
                EmailOutbox.REPORT_TYPE_DAILY_EOD, businessDate, 1, "owner@khanaparadise.com"))
                .thenReturn(Optional.of(existing));

        EmailOutbox result = service.generateAndSendReport(tenantId, businessDate, false);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getStatus()).isEqualTo(EmailOutbox.STATUS_DEAD_LETTER);
        assertThat(result.getAttemptCount()).isEqualTo(5);
        verify(emailOutboxRepository, never()).save(any(EmailOutbox.class));
        verifyNoInteractions(emailNotificationService);
    }

    /**
     * A forced resend can reuse a row that was already delivered. If that resend fails, the row must
     * not keep the old sentAt, otherwise a FAILED attempt reads as delivered in the outbox.
     */
    @Test
    void failedForcedResendClearsStaleSentAt() throws Exception {
        RestaurantProfile profile = createMockProfile();
        when(restaurantProfileRepository.findByRestaurantId(tenantId)).thenReturn(Optional.of(profile));

        EmailOutbox existing = EmailOutbox.builder()
                .id(42L)
                .restaurantId(tenantId)
                .reportType(EmailOutbox.REPORT_TYPE_DAILY_EOD)
                .referenceKey(businessDate)
                .revision(1)
                .recipient("owner@khanaparadise.com")
                .subject("existing")
                .htmlBody("<html>existing</html>")
                .status(EmailOutbox.STATUS_SENT)
                .attemptCount(1)
                .maxAttempts(5)
                .sentAt(1_000L)
                .createdAt(0L)
                .updatedAt(0L)
                .build();
        when(emailOutboxRepository.findByReportTypeAndReferenceKeyAndRevisionAndRecipient(
                EmailOutbox.REPORT_TYPE_DAILY_EOD, businessDate, 1, "owner@khanaparadise.com"))
                .thenReturn(Optional.of(existing));

        when(billRepository.findByRestaurantIdAndLastResetDateAndIsDeletedFalse(tenantId, businessDate))
                .thenReturn(List.of());
        when(cashierShiftRepository.findByRestaurantIdAndBusinessDateAndIsDeletedFalseOrderByOpenedAtAsc(eq(tenantId), any(LocalDate.class)))
                .thenReturn(List.of());
        when(templateEngine.process(eq("email/daily-closing-report"), any(Context.class)))
                .thenReturn("<html>Daily Report</html>");

        when(emailOutboxRepository.save(any(EmailOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("SMTP down"))
                .when(emailNotificationService).sendDailyClosingReport(anyString(), anyString(), anyString());

        EmailOutbox result = service.generateAndSendReport(tenantId, businessDate, true);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getStatus()).isEqualTo(EmailOutbox.STATUS_FAILED);
        assertThat(result.getSentAt()).isNull();
        assertThat(result.getAttemptCount()).isEqualTo(1);
    }
}
