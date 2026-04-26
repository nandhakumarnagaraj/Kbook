package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.payment.entity.Payment;
import com.khanabook.saas.payment.repository.PaymentRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.storefront.entity.CustomerOrder;
import com.khanabook.saas.storefront.repository.CustomerOrderRepository;
import com.khanabook.saas.webadmin.dto.AdminBusinessDetailResponse;
import com.khanabook.saas.webadmin.dto.AdminBusinessListItemResponse;
import com.khanabook.saas.webadmin.dto.AdminDashboardSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminReadService {

    private final RestaurantProfileRepository restaurantProfileRepository;
    private final UserRepository userRepository;
    private final MenuItemRepository menuItemRepository;
    private final BillRepository billRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getDashboardSummary() {
        List<RestaurantProfile> profiles = restaurantProfileRepository.findAll().stream()
                .filter(profile -> !Boolean.TRUE.equals(profile.getIsDeleted()))
                .toList();

        List<User> users = userRepository.findAll().stream()
                .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                .toList();

        long totalOrders = billRepository.findAll().stream()
                .filter(bill -> !Boolean.TRUE.equals(bill.getIsDeleted()))
                .count()
                + customerOrderRepository.count();

        BigDecimal billRevenue = billRepository.findAll().stream()
                .filter(bill -> !Boolean.TRUE.equals(bill.getIsDeleted()))
                .filter(bill -> isRevenueBillStatus(bill.getOrderStatus(), bill.getPaymentStatus()))
                .map(bill -> defaultAmount(bill.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal storefrontRevenue = customerOrderRepository.findAll().stream()
                .filter(order -> isCompletedStorefrontOrder(order.getOrderStatus(), order.getPaymentStatus()))
                .map(order -> defaultAmount(order.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AdminDashboardSummaryResponse.builder()
                .totalBusinesses(profiles.size())
                .liveBusinesses(profiles.stream().filter(profile -> Boolean.TRUE.equals(profile.getOwnWebsiteEnabled())).count())
                .totalStaff(users.size())
                .totalOrders(totalOrders)
                .totalRevenue(billRevenue.add(storefrontRevenue))
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminBusinessListItemResponse> getBusinesses() {
        List<RestaurantProfile> profiles = restaurantProfileRepository.findAll().stream()
                .filter(profile -> !Boolean.TRUE.equals(profile.getIsDeleted()))
                .sorted(Comparator.comparing(RestaurantProfile::getUpdatedAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();

        List<User> users = userRepository.findAll().stream()
                .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                .toList();

        Map<Long, User> ownerByRestaurant = users.stream()
                .filter(user -> user.getRole() == UserRole.OWNER)
                .collect(Collectors.toMap(User::getRestaurantId, Function.identity(), (left, right) ->
                        left.getUpdatedAt() >= right.getUpdatedAt() ? left : right));

        Map<Long, Long> staffCountByRestaurant = users.stream()
                .collect(Collectors.groupingBy(User::getRestaurantId, Collectors.counting()));

        Map<Long, Long> menuCountByRestaurant = menuItemRepository.findAll().stream()
                .filter(menuItem -> !Boolean.TRUE.equals(menuItem.getIsDeleted()))
                .collect(Collectors.groupingBy(menuItem -> menuItem.getRestaurantId(), Collectors.counting()));

        Map<Long, Long> billCountByRestaurant = billRepository.findAll().stream()
                .filter(bill -> !Boolean.TRUE.equals(bill.getIsDeleted()))
                .collect(Collectors.groupingBy(bill -> bill.getRestaurantId(), Collectors.counting()));

        Map<Long, Long> storefrontCountByRestaurant = customerOrderRepository.findAll().stream()
                .collect(Collectors.groupingBy(CustomerOrder::getRestaurantId, Collectors.counting()));

        return profiles.stream()
                .map(profile -> {
                    User owner = ownerByRestaurant.get(profile.getRestaurantId());
                    long orderCount = billCountByRestaurant.getOrDefault(profile.getRestaurantId(), 0L)
                            + storefrontCountByRestaurant.getOrDefault(profile.getRestaurantId(), 0L);
                    return AdminBusinessListItemResponse.builder()
                            .restaurantId(profile.getRestaurantId())
                            .shopName(profile.getShopName())
                            .ownerName(owner != null ? owner.getName() : null)
                            .ownerLoginId(owner != null ? owner.getLoginId() : null)
                            .whatsappNumber(profile.getWhatsappNumber())
                            .email(profile.getEmail())
                            .websiteEnabled(Boolean.TRUE.equals(profile.getOwnWebsiteEnabled()))
                            .staffCount(staffCountByRestaurant.getOrDefault(profile.getRestaurantId(), 0L))
                            .menuCount(menuCountByRestaurant.getOrDefault(profile.getRestaurantId(), 0L))
                            .orderCount(orderCount)
                            .updatedAt(profile.getUpdatedAt())
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminBusinessDetailResponse getBusinessDetail(Long restaurantId) {
        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        List<User> users = userRepository.findAll().stream()
                .filter(user -> restaurantId.equals(user.getRestaurantId()))
                .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                .toList();

        User owner = users.stream()
                .filter(user -> user.getRole() == UserRole.OWNER)
                .max(Comparator.comparing(User::getUpdatedAt, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);

        long menuCount = menuItemRepository.findAll().stream()
                .filter(menuItem -> restaurantId.equals(menuItem.getRestaurantId()))
                .filter(menuItem -> !Boolean.TRUE.equals(menuItem.getIsDeleted()))
                .count();

        List<com.khanabook.saas.entity.Bill> bills = billRepository.findAll().stream()
                .filter(bill -> restaurantId.equals(bill.getRestaurantId()))
                .filter(bill -> !Boolean.TRUE.equals(bill.getIsDeleted()))
                .toList();

        List<CustomerOrder> storefrontOrders = customerOrderRepository.findAll().stream()
                .filter(order -> restaurantId.equals(order.getRestaurantId()))
                .toList();

        BigDecimal billRevenue = bills.stream()
                .filter(bill -> isRevenueBillStatus(bill.getOrderStatus(), bill.getPaymentStatus()))
                .map(bill -> defaultAmount(bill.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal storefrontRevenue = storefrontOrders.stream()
                .filter(order -> isCompletedStorefrontOrder(order.getOrderStatus(), order.getPaymentStatus()))
                .map(order -> defaultAmount(order.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AdminBusinessDetailResponse.builder()
                .restaurantId(profile.getRestaurantId())
                .shopName(profile.getShopName())
                .ownerName(owner != null ? owner.getName() : null)
                .ownerLoginId(owner != null ? owner.getLoginId() : null)
                .ownerWhatsappNumber(owner != null ? owner.getWhatsappNumber() : null)
                .email(profile.getEmail())
                .shopAddress(profile.getShopAddress())
                .currency(profile.getCurrency())
                .timezone(profile.getTimezone())
                .websiteEnabled(Boolean.TRUE.equals(profile.getOwnWebsiteEnabled()))
                .gstEnabled(Boolean.TRUE.equals(profile.getGstEnabled()))
                .printerEnabled(Boolean.TRUE.equals(profile.getPrinterEnabled()))
                .staffCount(users.size())
                .menuCount(menuCount)
                .posOrderCount(bills.size())
                .onlineOrderCount(storefrontOrders.size())
                .totalRevenue(billRevenue.add(storefrontRevenue))
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private boolean isRevenueBillStatus(String orderStatus, String paymentStatus) {
        return "completed".equalsIgnoreCase(orderStatus)
                || "paid".equalsIgnoreCase(orderStatus)
                || "paid".equalsIgnoreCase(paymentStatus);
    }

    private boolean isCompletedStorefrontOrder(String orderStatus, String paymentStatus) {
        return "COMPLETED".equalsIgnoreCase(orderStatus)
                || "SUCCESS".equalsIgnoreCase(paymentStatus)
                || "PAID".equalsIgnoreCase(paymentStatus);
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
