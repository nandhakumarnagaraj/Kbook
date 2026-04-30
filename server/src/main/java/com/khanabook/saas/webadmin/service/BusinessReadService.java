package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.payment.entity.Payment;
import com.khanabook.saas.payment.entity.PaymentGateway;
import com.khanabook.saas.payment.entity.PaymentStatus;
import com.khanabook.saas.payment.entity.RefundMode;
import com.khanabook.saas.payment.entity.RefundStatus;
import com.khanabook.saas.payment.repository.PaymentRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.storefront.entity.CustomerOrder;
import com.khanabook.saas.storefront.repository.CustomerOrderRepository;
import com.khanabook.saas.webadmin.dto.BusinessDashboardResponse;
import com.khanabook.saas.webadmin.dto.BusinessMenuListItemResponse;
import com.khanabook.saas.webadmin.dto.BusinessOrderListItemResponse;
import com.khanabook.saas.webadmin.dto.BusinessStaffListItemResponse;
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
    private final PaymentRepository paymentRepository;
    private final CustomerOrderRepository customerOrderRepository;

    @Transactional(readOnly = true)
    public BusinessDashboardResponse getDashboard(Long restaurantId) {
        var profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        List<User> staff = getBusinessUsers(restaurantId);
        List<MenuItem> menuItems = getBusinessMenuItems(restaurantId);
        List<Bill> bills = getBusinessBills(restaurantId);
        Map<Long, Payment> latestPaymentByBillId = getLatestPaymentsByBillId(restaurantId, bills);
        List<CustomerOrder> storefrontOrders = customerOrderRepository.findByRestaurantIdOrderByCreatedAtDescIdDesc(restaurantId);

        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(zoneId);
        long startOfToday = today.atStartOfDay(zoneId).toInstant().toEpochMilli();

        BigDecimal billRevenue = bills.stream()
                .filter(bill -> isRevenueBillStatus(bill.getOrderStatus(), bill.getPaymentStatus()))
                .map(bill -> safeAmount(bill.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal storefrontRevenue = storefrontOrders.stream()
                .filter(order -> isCompletedStorefrontOrder(order.getOrderStatus(), order.getPaymentStatus()))
                .map(order -> safeAmount(order.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal todayBillRevenue = bills.stream()
                .filter(bill -> bill.getCreatedAt() != null && bill.getCreatedAt() >= startOfToday)
                .filter(bill -> isRevenueBillStatus(bill.getOrderStatus(), bill.getPaymentStatus()))
                .map(bill -> safeAmount(bill.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal todayStorefrontRevenue = storefrontOrders.stream()
                .filter(order -> order.getCreatedAt() != null && order.getCreatedAt() >= startOfToday)
                .filter(order -> isCompletedStorefrontOrder(order.getOrderStatus(), order.getPaymentStatus()))
                .map(order -> safeAmount(order.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long refundedOrders = bills.stream()
                .filter(bill -> isRefundedBill(bill, latestPaymentByBillId.get(bill.getId())))
                .count();
        BigDecimal refundedAmount = bills.stream()
                .filter(bill -> isRefundedBill(bill, latestPaymentByBillId.get(bill.getId())))
                .map(bill -> safeAmount(latestPaymentByBillId.get(bill.getId()) != null
                        ? latestPaymentByBillId.get(bill.getId()).getRefundAmount()
                        : bill.getRefundAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BusinessOrderListItemResponse> recentOrders = buildOrders(restaurantId, bills, latestPaymentByBillId).stream()
                .sorted(Comparator.comparing(BusinessOrderListItemResponse::createdAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(8)
                .toList();

        return BusinessDashboardResponse.builder()
                .restaurantId(restaurantId)
                .shopName(profile.getShopName())
                .totalStaff(staff.size())
                .totalMenuItems(menuItems.size())
                .posOrderCount(bills.size())
                .onlineOrderCount(storefrontOrders.size())
                .pendingOnlineOrders(storefrontOrders.stream()
                        .filter(order -> "PENDING_CONFIRMATION".equalsIgnoreCase(order.getOrderStatus()))
                        .count())
                .pendingPosPayments(bills.stream()
                        .filter(this::isPendingPosPayment)
                        .count())
                .totalRevenue(billRevenue.add(storefrontRevenue))
                .todayRevenue(todayBillRevenue.add(todayStorefrontRevenue))
                .refundedOrders(refundedOrders)
                .refundedAmount(refundedAmount)
                .recentOrders(recentOrders)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BusinessOrderListItemResponse> getOrders(Long restaurantId) {
        List<Bill> bills = getBusinessBills(restaurantId);
        return buildOrders(restaurantId, bills, getLatestPaymentsByBillId(restaurantId, bills)).stream()
                .sorted(Comparator.comparing(BusinessOrderListItemResponse::createdAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
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
        Payment latestPayment = getLatestPaymentsByBillId(restaurantId, List.of(bill)).get(bill.getId());
        return toBillOrderResponse(bill, latestPayment);
    }

    private List<BusinessOrderListItemResponse> buildOrders(Long restaurantId, List<Bill> bills, Map<Long, Payment> latestPaymentByBillId) {
        List<BusinessOrderListItemResponse> posOrders = bills.stream()
                .map(bill -> toBillOrderResponse(bill, latestPaymentByBillId.get(bill.getId())))
                .toList();

        List<BusinessOrderListItemResponse> onlineOrders = customerOrderRepository.findByRestaurantIdOrderByCreatedAtDescIdDesc(restaurantId).stream()
                .map(order -> BusinessOrderListItemResponse.builder()
                        .sourceType("ONLINE")
                        .orderId(order.getId())
                        .orderCode(order.getPublicOrderCode())
                        .customerName(order.getCustomerName())
                        .customerContact(order.getCustomerPhone())
                        .orderStatus(normalizeLabel(order.getOrderStatus()))
                        .paymentStatus(normalizeLabel(order.getPaymentStatus()))
                        .paymentMethod(normalizeLabel(order.getPaymentMethod()))
                        .totalAmount(safeAmount(order.getTotalAmount()))
                        .gatewayPaidAmount(null)
                        .refundAmount(null)
                        .refundStatus("Not refunded")
                        .refundMode(null)
                        .cancelReason(null)
                        .manualRefundAllowed(false)
                        .gatewayRefundAllowed(false)
                        .createdAt(order.getCreatedAt())
                        .build())
                .toList();

        return java.util.stream.Stream.concat(posOrders.stream(), onlineOrders.stream()).toList();
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

    private boolean isRefundedBill(Bill bill, Payment payment) {
        if (payment != null && payment.getRefundStatus() == RefundStatus.SUCCESS) {
            return payment.getRefundAmount() != null && payment.getRefundAmount().compareTo(BigDecimal.ZERO) > 0;
        }
        return bill.getRefundAmount() != null
                && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0
                && "refunded".equalsIgnoreCase(bill.getPaymentStatus());
    }

    private boolean isCompletedStorefrontOrder(String orderStatus, String paymentStatus) {
        return "COMPLETED".equalsIgnoreCase(orderStatus)
                || "SUCCESS".equalsIgnoreCase(paymentStatus)
                || "PAID".equalsIgnoreCase(paymentStatus);
    }

    private BusinessOrderListItemResponse toBillOrderResponse(Bill bill, Payment payment) {
        RefundStatus refundStatus = effectiveRefundStatus(bill, payment);
        RefundMode refundMode = payment != null ? payment.getRefundMode() : null;
        return BusinessOrderListItemResponse.builder()
                .sourceType("POS")
                .orderId(bill.getId())
                .orderCode(bill.getDailyOrderDisplay() != null && !bill.getDailyOrderDisplay().isBlank()
                        ? bill.getDailyOrderDisplay()
                        : "INV" + bill.getLifetimeOrderId())
                .customerName(bill.getCustomerName())
                .customerContact(bill.getCustomerWhatsapp())
                .orderStatus(normalizeLabel(bill.getOrderStatus()))
                .paymentStatus(normalizeLabel(bill.getPaymentStatus()))
                .paymentMethod(normalizeLabel(bill.getPaymentMode()))
                .totalAmount(safeAmount(bill.getTotalAmount()))
                .gatewayPaidAmount(payment != null ? safeAmount(payment.getAmount()) : null)
                .refundAmount(payment != null && payment.getRefundAmount() != null ? payment.getRefundAmount() : bill.getRefundAmount())
                .refundStatus(normalizeLabel(refundStatus.name()))
                .refundMode(refundMode == null ? null : normalizeLabel(refundMode.name()))
                .cancelReason(bill.getCancelReason())
                .manualRefundAllowed(canManualRefund(bill, payment))
                .gatewayRefundAllowed(canGatewayRefund(bill, payment))
                .createdAt(bill.getCreatedAt())
                .build();
    }

    private Map<Long, Payment> getLatestPaymentsByBillId(Long restaurantId, List<Bill> bills) {
        if (bills.isEmpty()) {
            return Map.of();
        }
        List<Long> billIds = bills.stream().map(Bill::getId).toList();
        return paymentRepository.findByRestaurantIdAndBillIdIn(restaurantId, billIds).stream()
                .collect(Collectors.toMap(
                        Payment::getBillId,
                        payment -> payment,
                        (left, right) -> left.getCreatedAt() >= right.getCreatedAt() ? left : right
                ));
    }

    private RefundStatus effectiveRefundStatus(Bill bill, Payment payment) {
        if (payment != null && payment.getRefundStatus() != null) {
            return payment.getRefundStatus();
        }
        if (bill.getRefundAmount() != null && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            return "refunded".equalsIgnoreCase(bill.getPaymentStatus()) ? RefundStatus.SUCCESS : RefundStatus.PENDING;
        }
        return RefundStatus.NOT_REFUNDED;
    }

    private boolean canManualRefund(Bill bill, Payment payment) {
        if (!isRefundableOrderStatus(bill.getOrderStatus()) || !"success".equalsIgnoreCase(bill.getPaymentStatus())) {
            return false;
        }
        if (bill.getRefundAmount() != null && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            return false;
        }
        if (isEasebuzzBill(bill) || isEasebuzzPayment(payment)) {
            return false;
        }
        return payment == null || payment.getRefundStatus() == null
                || payment.getRefundStatus() == RefundStatus.NOT_REFUNDED
                || payment.getRefundStatus() == RefundStatus.FAILED;
    }

    private boolean canGatewayRefund(Bill bill, Payment payment) {
        if (!isRefundableOrderStatus(bill.getOrderStatus()) || !"success".equalsIgnoreCase(bill.getPaymentStatus())) {
            return false;
        }
        if (bill.getRefundAmount() != null && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            return false;
        }
        if (payment == null || payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            return false;
        }
        if (payment.getRefundStatus() == RefundStatus.PENDING || payment.getRefundStatus() == RefundStatus.SUCCESS) {
            return false;
        }
        return isEasebuzzPayment(payment)
                && !payment.getGatewayPaymentId().isBlank();
    }

    private boolean isRefundableOrderStatus(String orderStatus) {
        return "completed".equalsIgnoreCase(orderStatus) || "cancelled".equalsIgnoreCase(orderStatus);
    }

    private boolean isEasebuzzPayment(Payment payment) {
        return payment != null
                && payment.getGateway() == PaymentGateway.EASEBUZZ
                && payment.getGatewayPaymentId() != null
                && !payment.getGatewayPaymentId().isBlank();
    }

    private boolean isEasebuzzBill(Bill bill) {
        return bill.getPaymentMode() != null
                && bill.getPaymentMode().toLowerCase(Locale.ROOT).contains("easebuzz");
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
}
