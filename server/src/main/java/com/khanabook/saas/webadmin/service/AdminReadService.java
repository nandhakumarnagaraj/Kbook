package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
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

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getDashboardSummary() {
        long totalBusinesses   = restaurantProfileRepository.countByIsDeletedFalse();
        long liveBusinesses    = restaurantProfileRepository.countByIsDeletedFalseAndOwnWebsiteEnabledTrue();
        long totalStaff        = userRepository.countByIsDeletedFalse();
        long totalBillOrders   = billRepository.countByIsDeletedFalse();
        long totalSfOrders     = customerOrderRepository.count();
        BigDecimal billRev     = nullSafe(billRepository.sumCompletedRevenue());
        BigDecimal sfRev       = nullSafe(customerOrderRepository.sumCompletedRevenue());

        return AdminDashboardSummaryResponse.builder()
                .totalBusinesses(totalBusinesses)
                .liveBusinesses(liveBusinesses)
                .totalStaff(totalStaff)
                .totalOrders(totalBillOrders + totalSfOrders)
                .totalRevenue(billRev.add(sfRev))
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminBusinessListItemResponse> getBusinesses() {
        List<RestaurantProfile> profiles = restaurantProfileRepository
                .findAllByIsDeletedFalseOrderByUpdatedAtDesc();

        Map<Long, User> ownerByRestaurant = userRepository.findAllActiveOwners().stream()
                .collect(Collectors.toMap(
                        User::getRestaurantId,
                        Function.identity(),
                        (left, right) -> Comparator.comparingLong((User u) -> u.getUpdatedAt() == null ? 0L : u.getUpdatedAt())
                                .compare(left, right) >= 0 ? left : right));

        Map<Long, Long> staffCount = userRepository.countStaffGroupedByRestaurant().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));

        Map<Long, Long> menuCount = menuItemRepository.countGroupedByRestaurant().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));

        Map<Long, Long> billCount = billRepository.countGroupedByRestaurant().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));

        Map<Long, Long> sfCount = customerOrderRepository.countGroupedByRestaurant().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));

        return profiles.stream()
                .map(profile -> {
                    User owner = ownerByRestaurant.get(profile.getRestaurantId());
                    long orders = billCount.getOrDefault(profile.getRestaurantId(), 0L)
                            + sfCount.getOrDefault(profile.getRestaurantId(), 0L);
                    return AdminBusinessListItemResponse.builder()
                            .restaurantId(profile.getRestaurantId())
                            .shopName(profile.getShopName())
                            .ownerName(owner != null ? owner.getName() : null)
                            .ownerLoginId(owner != null ? owner.getLoginId() : null)
                            .whatsappNumber(profile.getWhatsappNumber())
                            .email(profile.getEmail())
                            .websiteEnabled(Boolean.TRUE.equals(profile.getOwnWebsiteEnabled()))
                            .staffCount(staffCount.getOrDefault(profile.getRestaurantId(), 0L))
                            .menuCount(menuCount.getOrDefault(profile.getRestaurantId(), 0L))
                            .orderCount(orders)
                            .updatedAt(profile.getUpdatedAt())
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminBusinessDetailResponse getBusinessDetail(Long restaurantId) {
        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        List<User> users = userRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);
        User owner = users.stream()
                .filter(u -> u.getRole() == UserRole.OWNER)
                .max(Comparator.comparing(User::getUpdatedAt, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);

        long menuCount     = menuItemRepository.countByRestaurantIdAndIsDeletedFalse(restaurantId);
        long posOrders     = billRepository.countByRestaurantIdAndIsDeletedFalse(restaurantId);
        long onlineOrders  = customerOrderRepository.countByRestaurantId(restaurantId);
        BigDecimal billRev = nullSafe(billRepository.sumCompletedRevenueByRestaurant(restaurantId));
        BigDecimal sfRev   = nullSafe(customerOrderRepository.sumCompletedRevenueByRestaurant(restaurantId));

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
                .posOrderCount(posOrders)
                .onlineOrderCount(onlineOrders)
                .totalRevenue(billRev.add(sfRev))
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
