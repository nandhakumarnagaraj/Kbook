package com.khanabook.saas.feature.reports.service;

import com.khanabook.saas.core.utility.BusinessDay;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.notifications.data.EmailOutbox;
import com.khanabook.saas.feature.notifications.data.EmailOutboxRepository;
import com.khanabook.saas.feature.notifications.service.EmailNotificationService;
import com.khanabook.saas.feature.reports.dto.DailyClosingReportData;
import com.khanabook.saas.feature.reports.dto.DailyClosingReportData.ShiftSummaryItem;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.shifts.data.CashierShift;
import com.khanabook.saas.feature.shifts.data.CashierShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyClosingReportService {

    private final BillRepository billRepository;
    private final CashierShiftRepository cashierShiftRepository;
    private final RestaurantProfileRepository restaurantProfileRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailNotificationService emailNotificationService;
    private final TemplateEngine templateEngine;

    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    /**
     * Aggregates financial and shift data for a given restaurant and business date.
     */
    @Transactional(readOnly = true)
    public DailyClosingReportData generateReportData(Long restaurantId, String businessDate) {
        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found: " + restaurantId));

        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(businessDate);
        } catch (Exception e) {
            parsedDate = LocalDate.now();
            businessDate = parsedDate.toString();
        }

        List<Bill> bills = billRepository.findByRestaurantIdAndLastResetDateAndIsDeletedFalse(restaurantId, businessDate);
        List<CashierShift> shifts = cashierShiftRepository
                .findByRestaurantIdAndBusinessDateAndIsDeletedFalseOrderByOpenedAtAsc(restaurantId, parsedDate);

        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        BigDecimal customTaxTotal = BigDecimal.ZERO;

        int completedOrders = 0;
        int cancelledOrders = 0;

        BigDecimal cashTotal = BigDecimal.ZERO;
        int cashCount = 0;

        BigDecimal upiTotal = BigDecimal.ZERO;
        int upiCount = 0;

        BigDecimal cardTotal = BigDecimal.ZERO;
        int cardCount = 0;

        BigDecimal splitTotal = BigDecimal.ZERO;
        int splitCount = 0;

        BigDecimal otherTotal = BigDecimal.ZERO;
        int otherCount = 0;

        for (Bill bill : bills) {
            String orderStatus = bill.getOrderStatus() != null ? bill.getOrderStatus().toLowerCase() : "";
            if ("cancelled".equals(orderStatus)) {
                cancelledOrders++;
                continue;
            }

            // Completed or paid
            completedOrders++;
            BigDecimal billAmount = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;
            grossSales = grossSales.add(billAmount);

            BigDecimal cgst = bill.getCgstAmount() != null ? bill.getCgstAmount() : BigDecimal.ZERO;
            BigDecimal sgst = bill.getSgstAmount() != null ? bill.getSgstAmount() : BigDecimal.ZERO;
            BigDecimal customTax = bill.getCustomTaxAmount() != null ? bill.getCustomTaxAmount() : BigDecimal.ZERO;

            cgstTotal = cgstTotal.add(cgst);
            sgstTotal = sgstTotal.add(sgst);
            customTaxTotal = customTaxTotal.add(customTax);
            totalTax = totalTax.add(cgst).add(sgst).add(customTax);

            String mode = bill.getPaymentMode() != null ? bill.getPaymentMode().toUpperCase() : "CASH";
            if (mode.contains("CASH")) {
                cashTotal = cashTotal.add(billAmount);
                cashCount++;
            } else if (mode.contains("UPI") || mode.contains("QR") || mode.contains("ONLINE")) {
                upiTotal = upiTotal.add(billAmount);
                upiCount++;
            } else if (mode.contains("CARD") || mode.contains("POS")) {
                cardTotal = cardTotal.add(billAmount);
                cardCount++;
            } else if (mode.contains("SPLIT")) {
                splitTotal = splitTotal.add(billAmount);
                splitCount++;
            } else {
                otherTotal = otherTotal.add(billAmount);
                otherCount++;
            }
        }

        BigDecimal netSales = grossSales.subtract(totalTax).max(BigDecimal.ZERO);

        List<ShiftSummaryItem> shiftItems = new ArrayList<>();
        for (CashierShift shift : shifts) {
            shiftItems.add(ShiftSummaryItem.builder()
                    .cashierName(shift.getCashierName() != null ? shift.getCashierName() : "Cashier")
                    .terminal(shift.getTerminalSeries() != null ? shift.getTerminalSeries() : shift.getTerminalId())
                    .openingFloat(shift.getOpeningFloat() != null ? shift.getOpeningFloat() : BigDecimal.ZERO)
                    .declaredCash(shift.getDeclaredCash() != null ? shift.getDeclaredCash() : BigDecimal.ZERO)
                    .expectedCash(shift.getExpectedCash() != null ? shift.getExpectedCash() : BigDecimal.ZERO)
                    .cashVariance(shift.getCashVariance() != null ? shift.getCashVariance() : BigDecimal.ZERO)
                    .totalRevenue(shift.getTotalRevenue() != null ? shift.getTotalRevenue() : BigDecimal.ZERO)
                    .totalOrders(shift.getTotalOrders() != null ? shift.getTotalOrders() : 0)
                    .status(shift.getStatus())
                    .build());
        }

        String opTime = profile.getOpeningTime() != null ? profile.getOpeningTime().toString().substring(0, 5) : "08:00";
        String clTime = profile.getClosingTime() != null ? profile.getClosingTime().toString().substring(0, 5) : "23:00";
        String cutoff = profile.getBusinessDayCutoffTime() != null ? profile.getBusinessDayCutoffTime().toString().substring(0, 5) : "00:00";

        return DailyClosingReportData.builder()
                .restaurantId(restaurantId)
                .restaurantName(profile.getShopName() != null ? profile.getShopName() : "Restaurant")
                .restaurantAddress(profile.getShopAddress())
                .restaurantPhone(profile.getWhatsappNumber())
                .businessDate(parsedDate.format(DISPLAY_DATE_FMT))
                .rawBusinessDate(businessDate)
                .openingTime(opTime)
                .closingTime(clTime)
                .cutoffTime(cutoff)
                .currency(profile.getCurrency() != null && !profile.getCurrency().isBlank() ? profile.getCurrency() : "₹")
                .generatedAt(LocalDateTime.now(BusinessDay.zoneOf(profile.getTimezone())).format(TIMESTAMP_FMT))
                .grossSales(grossSales)
                .netSales(netSales)
                .totalOrders(bills.size())
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .totalTax(totalTax)
                .cgst(cgstTotal)
                .sgst(sgstTotal)
                .customTax(customTaxTotal)
                .cashTotal(cashTotal)
                .cashCount(cashCount)
                .upiTotal(upiTotal)
                .upiCount(upiCount)
                .cardTotal(cardTotal)
                .cardCount(cardCount)
                .splitTotal(splitTotal)
                .splitCount(splitCount)
                .otherTotal(otherTotal)
                .otherCount(otherCount)
                .shifts(shiftItems)
                .build();
    }

    /**
     * Renders the HTML email body from the Thymeleaf template.
     */
    public String renderEmailHtml(DailyClosingReportData data) {
        Context ctx = new Context();
        ctx.setVariable("data", data);
        return templateEngine.process("email/daily-closing-report", ctx);
    }

    /**
     * Compiles the report, records it in the email_outbox, and dispatches the email via Gmail SMTP.
     */
    @Transactional
    public EmailOutbox generateAndSendReport(Long restaurantId, String businessDate, boolean force) {
        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found: " + restaurantId));

        String recipientsRaw = profile.getDailyClosingEmailRecipients();
        if (recipientsRaw == null || recipientsRaw.isBlank()) {
            recipientsRaw = profile.getEmail();
        }

        if (recipientsRaw == null || recipientsRaw.isBlank()) {
            log.info("No email configured for restaurant {}. Skipping daily closing email.", restaurantId);
            return null;
        }

        String primaryRecipient = recipientsRaw.split("[,;]")[0].trim();
        if (primaryRecipient.isBlank()) {
            return null;
        }

        // The outbox is unique on (report_type, reference_key, revision, recipient)
        // [uq_email_outbox_revision]. A row therefore exists for at most one attempt per day and
        // recipient, so a blind second INSERT throws a constraint violation. That is what happened
        // on every sweep for any date that was not SENT: the old check only skipped STATUS_SENT,
        // and every FAILED/DEAD_LETTER date was re-inserted each 15 minutes and rejected.
        // Look the row up first and reuse it; never insert a duplicate.
        final int revision = 1;
        Optional<EmailOutbox> existingRow = emailOutboxRepository
                .findByReportTypeAndReferenceKeyAndRevisionAndRecipient(
                        EmailOutbox.REPORT_TYPE_DAILY_EOD, businessDate, revision, primaryRecipient);

        if (existingRow.isPresent() && !force) {
            EmailOutbox row = existingRow.get();
            // PENDING/FAILED rows are owned by processOutboxRetries() (attempt cap + backoff), and
            // DEAD_LETTER rows have already exhausted it. Regenerating here would reset attemptCount
            // and resurrect an exhausted row every sweep, so leave the retry policy to the outbox.
            log.info("Daily closing report for restaurant {} on {} already queued for {} (status={}). Skipping.",
                    restaurantId, businessDate, primaryRecipient, row.getStatus());
            return row;
        }

        DailyClosingReportData reportData = generateReportData(restaurantId, businessDate);
        String htmlBody = renderEmailHtml(reportData);
        String subject = "Daily Closing Report - " + reportData.getRestaurantName() + " (" + reportData.getBusinessDate() + ")";

        long now = System.currentTimeMillis();
        // force=true (manual resend) reuses the existing row rather than inserting a revision 2,
        // because revision is fixed at 1 and an insert would violate the same constraint.
        EmailOutbox outbox = existingRow.orElseGet(() -> EmailOutbox.builder()
                .restaurantId(restaurantId)
                .reportType(EmailOutbox.REPORT_TYPE_DAILY_EOD)
                .referenceKey(businessDate)
                .revision(revision)
                .recipient(primaryRecipient)
                .maxAttempts(5)
                .createdAt(now)
                .build());
        outbox.setSubject(subject);
        outbox.setHtmlBody(htmlBody);
        outbox.setStatus(EmailOutbox.STATUS_PENDING);
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(null);
        outbox.setLastError(null);
        outbox.setUpdatedAt(now);

        outbox = emailOutboxRepository.save(outbox);

        try {
            emailNotificationService.sendDailyClosingReport(primaryRecipient, subject, htmlBody);
            outbox.setStatus(EmailOutbox.STATUS_SENT);
            outbox.setSentAt(System.currentTimeMillis());
            outbox.setAttemptCount(1);
            log.info("Daily closing report successfully sent to {} for restaurant {} (date: {})",
                    primaryRecipient, restaurantId, businessDate);
        } catch (Exception e) {
            log.error("Failed to send daily closing report to {}: {}", primaryRecipient, e.getMessage(), e);
            outbox.setStatus(EmailOutbox.STATUS_FAILED);
            outbox.setAttemptCount(1);
            outbox.setLastError(e.getMessage());
            outbox.setNextAttemptAt(System.currentTimeMillis() + 5 * 60 * 1000L); // retry in 5m
        }

        return emailOutboxRepository.save(outbox);
    }
}
