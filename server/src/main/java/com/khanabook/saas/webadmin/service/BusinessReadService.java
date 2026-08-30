package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.utility.AppConstants;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.webadmin.dto.BusinessDashboardResponse;
import com.khanabook.saas.webadmin.dto.BusinessCategoryResponse;
import com.khanabook.saas.webadmin.dto.BusinessMenuListItemResponse;
import com.khanabook.saas.webadmin.dto.BusinessOrderListItemResponse;
import com.khanabook.saas.webadmin.dto.BusinessStaffListItemResponse;
import com.khanabook.saas.webadmin.dto.DashboardTrendsResponse;
import com.khanabook.saas.webadmin.dto.OrderDetailResponse;
import com.khanabook.saas.webadmin.dto.OrderLineItemResponse;
import com.khanabook.saas.webadmin.dto.PaginatedOrdersResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusinessReadService {

    private final RestaurantProfileRepository restaurantProfileRepository;
    private final UserRepository userRepository;
    private final MenuItemRepository menuItemRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final CategoryRepository categoryRepository;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final com.khanabook.saas.service.SecurityAuditService securityAuditService;
    private final EasebuzzSubMerchantRepository subMerchantRepository;

    @Transactional(readOnly = true)
    public BusinessDashboardResponse getDashboard(Long restaurantId) {
        return getDashboard(restaurantId, null, null);
    }

    @Transactional(readOnly = true)
    public BusinessDashboardResponse getDashboard(Long restaurantId, LocalDate from, LocalDate to) {
        var profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        List<User> staff = getBusinessUsers(restaurantId);
        List<MenuItem> menuItems = getBusinessMenuItems(restaurantId);
        List<Bill> allBills = getBusinessBills(restaurantId);

        ZoneId zoneId = ZoneId.of(AppConstants.DEFAULT_TIMEZONE);

        // Filter bills by date range if from/to are provided
        List<Bill> bills;
        if (from != null || to != null) {
            long rangeStart = from != null
                    ? from.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    : 0L;
            long rangeEnd = to != null
                    ? to.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                    : Long.MAX_VALUE;
            bills = allBills.stream()
                    .filter(bill -> bill.getCreatedAt() != null
                            && bill.getCreatedAt() >= rangeStart
                            && bill.getCreatedAt() < rangeEnd)
                    .toList();
        } else {
            bills = allBills;
        }

        LocalDate today = LocalDate.now(zoneId);
        long startOfToday = today.atStartOfDay(zoneId).toInstant().toEpochMilli();

        BigDecimal billRevenue = bills.stream()
                .filter(bill -> isRevenueBillStatus(bill.getOrderStatus(), bill.getPaymentStatus()))
                .map(bill -> safeAmount(bill.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal todayBillRevenue = bills.stream()
                .filter(bill -> bill.getCreatedAt() != null && bill.getCreatedAt() >= startOfToday)
                .filter(bill -> isRevenueBillStatus(bill.getOrderStatus(), bill.getPaymentStatus()))
                .map(bill -> safeAmount(bill.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long refundedOrders = bills.stream()
                .filter(bill -> isRefundedBill(bill))
                .count();
        BigDecimal refundedAmount = bills.stream()
                .filter(bill -> isRefundedBill(bill))
                .map(bill -> safeAmount(bill.getRefundAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BusinessOrderListItemResponse> recentOrders = buildOrders(bills).stream()
                .sorted(Comparator.comparing(BusinessOrderListItemResponse::createdAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(8)
                .toList();

        return BusinessDashboardResponse.builder()
                .restaurantId(restaurantId)
                .shopName(profile.getShopName())
                .websiteEnabled(Boolean.TRUE.equals(profile.getOwnWebsiteEnabled()))
                .printerEnabled(Boolean.TRUE.equals(profile.getPrinterEnabled()))
                .kitchenPrinterEnabled(Boolean.TRUE.equals(profile.getKitchenPrinterEnabled()))
                .totalStaff(staff.size())
                .totalMenuItems(menuItems.size())
                .posOrderCount(bills.size())
                .pendingPosPayments(bills.stream()
                        .filter(this::isPendingPosPayment)
                        .count())
                .totalRevenue(billRevenue)
                .todayRevenue(todayBillRevenue)
                .refundedOrders(refundedOrders)
                .refundedAmount(refundedAmount)
                .recentOrders(recentOrders)
                .build();
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 8) {
            return "••••••";
        }
        return value.substring(0, 4) + "••••••" + value.substring(value.length() - 4);
    }

    @Transactional(readOnly = true)
    public List<BusinessOrderListItemResponse> getOrders(Long restaurantId) {
        List<Bill> bills = getBusinessBills(restaurantId);
        return buildOrders(bills).stream()
                .sorted(Comparator.comparing(BusinessOrderListItemResponse::createdAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public PaginatedOrdersResponse getOrdersPaginated(Long restaurantId, int page, int size, String status, LocalDate from, LocalDate to) {
        ZoneId zoneId = ZoneId.of(AppConstants.DEFAULT_TIMEZONE);
        Long fromEpoch = from != null ? from.atStartOfDay(zoneId).toInstant().toEpochMilli() : null;
        Long toEpoch = to != null ? to.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() : null;

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<Bill> billPage = billRepository.findOrdersPageable(restaurantId, status, fromEpoch, toEpoch, pageable);

        List<BusinessOrderListItemResponse> content = billPage.getContent().stream()
                .map(this::toBillOrderResponse)
                .toList();

        return new PaginatedOrdersResponse(content, billPage.getTotalElements(), billPage.getTotalPages(), page, size);
    }

    @Transactional(readOnly = true)
    public List<BusinessMenuListItemResponse> getMenu(Long restaurantId) {
        List<MenuItem> menuItems = getBusinessMenuItems(restaurantId);
        Map<Long, String> categoryNames = categoryRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(restaurantId, 0L).stream()
                .filter(category -> !Boolean.TRUE.equals(category.getIsDeleted()))
                .collect(Collectors.toMap(Category::getId, Category::getName, (left, right) -> left));

        Map<Long, Long> variantCountByMenuId = itemVariantRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(restaurantId, 0L).stream()
                .filter(variant -> !Boolean.TRUE.equals(variant.getIsDeleted()))
                .collect(Collectors.groupingBy(variant -> variant.getMenuItemId(), Collectors.counting()));

        return menuItems.stream()
                .map(item -> BusinessMenuListItemResponse.builder()
                        .menuItemId(item.getId())
                        .categoryId(item.getCategoryId())
                        .categoryName(categoryNames.get(item.getCategoryId()))
                        .name(item.getName())
                        .description(item.getDescription())
                        .foodType(item.getFoodType())
                        .basePrice(item.getBasePrice())
                        .available(Boolean.TRUE.equals(item.getIsAvailable()))
                        .stockStatus(item.getStockStatus().name())
                        .variantCount(variantCountByMenuId.getOrDefault(item.getId(), 0L))
                        .updatedAt(item.getUpdatedAt())
                        .build())
                .sorted(Comparator.comparing(BusinessMenuListItemResponse::updatedAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BusinessCategoryResponse> getCategories(Long restaurantId) {
        return categoryRepository.findByRestaurantIdAndIsDeletedFalseAndIsActiveTrueOrderByNameAsc(restaurantId)
                .stream()
                .map(category -> new BusinessCategoryResponse(category.getId(), category.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BusinessStaffListItemResponse> getStaff(Long restaurantId) {
        return getBusinessUsers(restaurantId).stream()
                .map(user -> BusinessStaffListItemResponse.builder()
                        .userId(user.getId())
                        .name(user.getName())
                        .loginId(user.getLoginId())
                        .email(user.getEmail())
                        .whatsappNumber(user.getWhatsappNumber())
                        .role(user.getRole().name())
                        .active(Boolean.TRUE.equals(user.getIsActive()))
                        .updatedAt(user.getUpdatedAt())
                        .build())
                .sorted(Comparator.comparing(BusinessStaffListItemResponse::updatedAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessOrderListItemResponse getPosOrder(Long restaurantId, Long billId) {
        Bill bill = billRepository.findById(billId)
                .filter(existing -> existing.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        return toBillOrderResponse(bill);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long restaurantId, Long billId) {
        BusinessOrderListItemResponse order = getPosOrder(restaurantId, billId);
        List<BillItem> billItems = billItemRepository.findByServerBillIdAndIsDeletedFalseOrderById(billId);
        List<OrderLineItemResponse> lineItems = billItems.stream()
                .map(item -> new OrderLineItemResponse(
                        item.getId(),
                        item.getItemName(),
                        item.getVariantName(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getItemTotal()
                ))
                .toList();
        return new OrderDetailResponse(order, lineItems);
    }

    @Transactional
    public void markManualRefund(Long restaurantId, Long billId, BigDecimal refundAmount, String reason) {
        Bill bill = billRepository.findById(billId)
                .filter(existing -> existing.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        long now = System.currentTimeMillis();
        bill.setRefundAmount(refundAmount);
        bill.setCancelReason(reason);
        bill.setOrderStatus("cancelled");
        bill.setPaymentStatus("refunded");
        bill.setUpdatedAt(now);
        bill.setServerUpdatedAt(now);
        billRepository.save(bill);

        // KB-006: Audit trail for all financial write-backs (refunds).
        securityAuditService.record(
                "MANUAL_REFUND",
                "SUCCESS",
                bill.getPublicToken() != null ? bill.getPublicToken().toString() : String.valueOf(billId),
                refundAmount != null ? refundAmount.toPlainString() : "0"
        );
    }

    @Transactional
    public void voidBill(Long restaurantId, Long billId, String reason) {
        Bill bill = billRepository.findById(billId)
                .filter(existing -> existing.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        String status = bill.getOrderStatus();
        if ("completed".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Cannot void a finalized bill. Use refund instead.");
        }
        if ("cancelled".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Bill is already cancelled.");
        }

        long now = System.currentTimeMillis();
        bill.setOrderStatus("cancelled");
        bill.setPaymentStatus("cancelled");
        Long userId = null;
        try { userId = TenantContext.getCurrentUserId(); } catch (Exception ignored) {}
        bill.setCancelReason(reason != null ? reason : "Voided by user " + userId);
        bill.setUpdatedAt(now);
        bill.setServerUpdatedAt(now);
        billRepository.save(bill);

        securityAuditService.record(
                "BILL_VOID",
                "SUCCESS",
                bill.getPublicToken() != null ? bill.getPublicToken().toString() : String.valueOf(billId),
                reason
        );
    }

    private List<BusinessOrderListItemResponse> buildOrders(List<Bill> bills) {
        return bills.stream()
                .map(bill -> toBillOrderResponse(bill))
                .toList();
    }

    private List<User> getBusinessUsers(Long restaurantId) {
        return userRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);
    }

    private List<MenuItem> getBusinessMenuItems(Long restaurantId) {
        return menuItemRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(restaurantId, 0L).stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .toList();
    }

    private List<Bill> getBusinessBills(Long restaurantId) {
        return billRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(restaurantId, 0L).stream()
                .filter(bill -> !Boolean.TRUE.equals(bill.getIsDeleted()))
                .toList();
    }

    private boolean isRevenueBillStatus(String orderStatus, String paymentStatus) {
        return ("completed".equalsIgnoreCase(orderStatus) || "paid".equalsIgnoreCase(orderStatus))
                && ("success".equalsIgnoreCase(paymentStatus) || "paid".equalsIgnoreCase(paymentStatus));
    }

    private boolean isPendingPosPayment(Bill bill) {
        return "draft".equalsIgnoreCase(bill.getOrderStatus())
                && "pending".equalsIgnoreCase(bill.getPaymentStatus());
    }

    private boolean isRefundedBill(Bill bill) {
        return bill.getRefundAmount() != null
                && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0
                && "refunded".equalsIgnoreCase(bill.getPaymentStatus());
    }

    private BusinessOrderListItemResponse toBillOrderResponse(Bill bill) {
        return BusinessOrderListItemResponse.builder()
                .sourceType(bill.getSourceChannel() != null && !bill.getSourceChannel().isBlank()
                        ? bill.getSourceChannel() : "POS")
                .orderId(bill.getId())
                .orderCode(bill.getDailyOrderDisplay() != null && !bill.getDailyOrderDisplay().isBlank()
                        ? bill.getDailyOrderDisplay()
                        : bill.getLifetimeOrderId() != null && bill.getLifetimeOrderId() > 0
                                ? "INV" + bill.getLifetimeOrderId()
                                : bill.getInvoiceNumber() != null && !bill.getInvoiceNumber().isBlank()
                                        ? bill.getInvoiceNumber()
                                        : "ORD-" + bill.getId())
                .customerName(bill.getCustomerName())
                .customerContact(bill.getCustomerWhatsapp())
                .orderStatus(normalizeLabel(bill.getOrderStatus()))
                .paymentStatus(normalizeLabel(bill.getPaymentStatus()))
                .paymentMethod(normalizeLabel(bill.getPaymentMode()))
                .totalAmount(safeAmount(bill.getTotalAmount()))
                .gatewayPaidAmount(null)
                .refundAmount(bill.getRefundAmount())
                .refundStatus(bill.getRefundAmount() != null && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0
                        ? "Refunded" : "Not refunded")
                .refundMode(null)
                .cancelReason(bill.getCancelReason())
                .manualRefundAllowed(canManualRefund(bill))
                .gatewayRefundAllowed(false)
                .createdAt(bill.getCreatedAt())
                .build();
    }

    private boolean canManualRefund(Bill bill) {
        if (!isRefundableOrderStatus(bill.getOrderStatus()) || !"success".equalsIgnoreCase(bill.getPaymentStatus())) {
            return false;
        }
        if (bill.getRefundAmount() != null && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            return false;
        }
        return true;
    }

    private boolean isRefundableOrderStatus(String orderStatus) {
        return "completed".equalsIgnoreCase(orderStatus) || "cancelled".equalsIgnoreCase(orderStatus);
    }

    @Transactional(readOnly = true)
    public DashboardTrendsResponse getDashboardTrends(Long restaurantId) {
        List<Bill> allBills = getBusinessBills(restaurantId);
        ZoneId zoneId = ZoneId.of(AppConstants.DEFAULT_TIMEZONE);
        LocalDate today = LocalDate.now(zoneId);

        long startOfToday = today.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long startOfYesterday = today.minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        long startOfThisWeek = today.minusDays(today.getDayOfWeek().getValue() - 1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        long startOfLastWeek = today.minusDays(today.getDayOfWeek().getValue() + 6).atStartOfDay(zoneId).toInstant().toEpochMilli();

        long todayRevenue = 0;
        long yesterdayRevenue = 0;
        long thisWeekRevenue = 0;
        long lastWeekRevenue = 0;

        java.util.Map<LocalDate, long[]> dailyData = new java.util.LinkedHashMap<>();

        for (Bill bill : allBills) {
            if (bill.getCreatedAt() == null) continue;
            if (!isRevenueBillStatus(bill.getOrderStatus(), bill.getPaymentStatus())) continue;

            long amount = safeAmount(bill.getTotalAmount()).longValue();
            LocalDate billDate = java.time.Instant.ofEpochMilli(bill.getCreatedAt()).atZone(zoneId).toLocalDate();

            if (bill.getCreatedAt() >= startOfToday) {
                todayRevenue += amount;
            } else if (bill.getCreatedAt() >= startOfYesterday && bill.getCreatedAt() < startOfToday) {
                yesterdayRevenue += amount;
            }

            if (bill.getCreatedAt() >= startOfThisWeek) {
                thisWeekRevenue += amount;
            } else if (bill.getCreatedAt() >= startOfLastWeek && bill.getCreatedAt() < startOfThisWeek) {
                lastWeekRevenue += amount;
            }

            dailyData.computeIfAbsent(billDate, k -> new long[]{0, 0});
            long[] data = dailyData.get(billDate);
            data[0] += amount;
            data[1]++;
        }

        List<DashboardTrendsResponse.DayTrend> last7Days = new java.util.ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long[] data = dailyData.getOrDefault(date, new long[]{0, 0});
            long revenue = data[0];
            long orderCount = data[1];
            long avgOrderValue = orderCount > 0 ? revenue / orderCount : 0;
            last7Days.add(DashboardTrendsResponse.DayTrend.builder()
                    .day(date.getDayOfWeek().name().substring(0, 3))
                    .revenue(revenue)
                    .orderCount(orderCount)
                    .avgOrderValue(avgOrderValue)
                    .build());
        }

        return DashboardTrendsResponse.builder()
                .last7Days(last7Days)
                .todayRevenue(todayRevenue)
                .yesterdayRevenue(yesterdayRevenue)
                .thisWeekRevenue(thisWeekRevenue)
                .lastWeekRevenue(lastWeekRevenue)
                .build();
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String normalizeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim()
                .replace('_', ' ')
                .toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    /**
     * Maps a single MenuItem entity to a BusinessMenuListItemResponse.
     * Used by write endpoints after creating/updating an item.
     */
    public BusinessMenuListItemResponse mapMenuItemToResponse(MenuItem item) {
        String categoryName = null;
        if (item.getCategoryId() != null) {
            categoryName = categoryRepository
                    .findByIdAndRestaurantIdAndIsDeletedFalse(item.getCategoryId(), item.getRestaurantId())
                    .map(Category::getName)
                    .orElse(null);
        }
        long variantCount = itemVariantRepository
                .countByMenuItemIdAndIsDeletedFalse(item.getId());
        return BusinessMenuListItemResponse.builder()
                .menuItemId(item.getId())
                .categoryId(item.getCategoryId())
                .categoryName(categoryName)
                .name(item.getName())
                .description(item.getDescription())
                .foodType(item.getFoodType())
                .basePrice(item.getBasePrice())
                .available(Boolean.TRUE.equals(item.getIsAvailable()))
                .stockStatus(item.getStockStatus() != null ? item.getStockStatus().name() : "IN_STOCK")
                .variantCount(variantCount)
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
