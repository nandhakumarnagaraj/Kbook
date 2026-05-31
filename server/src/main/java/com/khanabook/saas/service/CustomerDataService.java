package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.CustomerProfile;
import com.khanabook.saas.exception.BusinessRuleException;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CustomerDataService {

    private static final Logger log = LoggerFactory.getLogger(CustomerDataService.class);
    private final CustomerProfileRepository customerProfileRepository;
    private final BillRepository billRepository;

    @Transactional
    public void syncCustomerFromBill(Long restaurantId, Bill bill) {
        if (bill.getCustomerWhatsapp() == null || bill.getCustomerWhatsapp().isBlank()) return;
        String hash = hashPhone(bill.getCustomerWhatsapp());
        CustomerProfile cp = customerProfileRepository.findByRestaurantIdAndPhoneHash(restaurantId, hash)
                .orElseGet(() -> {
                    CustomerProfile c = new CustomerProfile();
                    c.setRestaurantId(restaurantId);
                    c.setPhoneHash(hash);
                    c.setDisplayName(bill.getCustomerName() != null ? bill.getCustomerName() : "Customer");
                    c.setTotalOrders(0);
                    c.setTotalSpend(BigDecimal.ZERO);
                    c.setFirstOrderAt(bill.getCreatedAt());
                    c.setSegment("new");
                    c.setOptedOut(false);
                    long now = System.currentTimeMillis();
                    c.setCreatedAt(now);
                    c.setUpdatedAt(now);
                    return c;
                });
        if (Boolean.TRUE.equals(cp.getOptedOut())) return;
        cp.setTotalOrders(cp.getTotalOrders() + 1);
        cp.setTotalSpend(cp.getTotalSpend().add(bill.getTotalAmount()));
        cp.setAverageOrderValue(cp.getTotalSpend().divide(BigDecimal.valueOf(cp.getTotalOrders()), 2, RoundingMode.HALF_UP));
        cp.setLastOrderAt(bill.getCreatedAt());
        cp.setPreferredPaymentMode(bill.getPaymentMode());
        cp.setSegment(determineSegment(cp));
        cp.setUpdatedAt(System.currentTimeMillis());
        customerProfileRepository.save(cp);
    }

    @Transactional
    public Map<String, Object> optOutCustomer(Long restaurantId, String phone) {
        String hash = hashPhone(phone);
        CustomerProfile cp = customerProfileRepository.findByRestaurantIdAndPhoneHash(restaurantId, hash)
                .orElseThrow(() -> new BusinessRuleException("Customer not found"));
        cp.setOptedOut(true);
        cp.setOptedOutAt(System.currentTimeMillis());
        cp.setUpdatedAt(System.currentTimeMillis());
        customerProfileRepository.save(cp);
        log.info("Customer opted out: restaurantId={} phoneHash={}", restaurantId, hash.substring(0, 8));
        return Map.of("status", "opted_out", "phone", maskPhone(phone));
    }

    @Transactional
    public Map<String, Object> optInCustomer(Long restaurantId, String phone) {
        String hash = hashPhone(phone);
        CustomerProfile cp = customerProfileRepository.findByRestaurantIdAndPhoneHash(restaurantId, hash)
                .orElseThrow(() -> new BusinessRuleException("Customer not found"));
        cp.setOptedOut(false);
        cp.setOptedOutAt(null);
        cp.setUpdatedAt(System.currentTimeMillis());
        customerProfileRepository.save(cp);
        return Map.of("status", "opted_in", "phone", maskPhone(phone));
    }

    @Transactional
    public Map<String, Object> deleteCustomerData(Long restaurantId, String phone) {
        String hash = hashPhone(phone);
        CustomerProfile cp = customerProfileRepository.findByRestaurantIdAndPhoneHash(restaurantId, hash)
                .orElseThrow(() -> new BusinessRuleException("Customer not found"));

        cp.setDisplayName("Deleted User");
        cp.setPhoneHash(hash + "_deleted_" + System.currentTimeMillis());
        cp.setPreferredPaymentMode(null);
        cp.setOptedOut(true);
        cp.setOptedOutAt(System.currentTimeMillis());
        cp.setUpdatedAt(System.currentTimeMillis());
        customerProfileRepository.save(cp);

        log.info("Customer data deleted (anonymized): restaurantId={} phoneHash={}", restaurantId, hash.substring(0, 8));
        return Map.of("status", "deleted", "message", "Customer data has been anonymized per GDPR/CCPA request");
    }

    @Transactional
    public Map<String, Object> exportCustomerData(Long restaurantId, String phone) {
        String hash = hashPhone(phone);
        CustomerProfile cp = customerProfileRepository.findByRestaurantIdAndPhoneHash(restaurantId, hash)
                .orElseThrow(() -> new BusinessRuleException("Customer not found"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("displayName", cp.getDisplayName());
        data.put("totalOrders", cp.getTotalOrders());
        data.put("totalSpend", cp.getTotalSpend());
        data.put("averageOrderValue", cp.getAverageOrderValue());
        data.put("firstOrderAt", cp.getFirstOrderAt());
        data.put("lastOrderAt", cp.getLastOrderAt());
        data.put("preferredPaymentMode", cp.getPreferredPaymentMode());
        data.put("segment", cp.getSegment());
        data.put("optedOut", cp.getOptedOut());
        data.put("exportedAt", System.currentTimeMillis());
        return data;
    }

    private String determineSegment(CustomerProfile cp) {
        if (cp.getTotalOrders() >= 10) return "vip";
        if (cp.getTotalOrders() >= 5) return "loyal";
        if (cp.getTotalOrders() >= 2) return "returning";
        return "new";
    }

    public Map<String, Object> getCustomerInsights(Long restaurantId) {
        List<CustomerProfile> customers = customerProfileRepository.findByRestaurantIdOrderByTotalSpendDesc(restaurantId);
        long totalCustomers = customers.size();
        long activeCustomers = customers.stream().filter(c -> !Boolean.TRUE.equals(c.getOptedOut())).count();
        long repeatCustomers = customerProfileRepository.countRepeatCustomers(restaurantId, 2);
        BigDecimal avgLtv = customers.isEmpty() ? BigDecimal.ZERO :
            customers.stream().map(CustomerProfile::getTotalSpend).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(totalCustomers), 2, RoundingMode.HALF_UP);

        List<Object[]> segmentCounts = customerProfileRepository.countBySegmentForRestaurant(restaurantId);
        Map<String, Long> segments = new LinkedHashMap<>();
        for (Object[] row : segmentCounts) segments.put((String) row[0], ((Number) row[1]).longValue());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restaurantId", restaurantId);
        result.put("totalCustomers", totalCustomers);
        result.put("activeCustomers", activeCustomers);
        result.put("repeatCustomers", repeatCustomers);
        result.put("retentionRate", totalCustomers > 0 ? BigDecimal.valueOf(repeatCustomers * 100.0 / totalCustomers).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        result.put("averageLtv", avgLtv);
        result.put("segments", segments);
        result.put("topCustomers", customers.stream().filter(c -> !Boolean.TRUE.equals(c.getOptedOut())).limit(10).map(c -> Map.of(
            "displayName", c.getDisplayName() != null ? c.getDisplayName() : "Customer",
            "totalOrders", c.getTotalOrders(),
            "totalSpend", c.getTotalSpend(),
            "segment", c.getSegment() != null ? c.getSegment() : "new",
            "lastOrderAt", c.getLastOrderAt() != null ? c.getLastOrderAt() : 0
        )).toList());
        return result;
    }

    public List<Map<String, Object>> getChurnRisk(Long restaurantId) {
        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        return customerProfileRepository.findByRestaurantIdOrderByTotalSpendDesc(restaurantId).stream()
                .filter(c -> !Boolean.TRUE.equals(c.getOptedOut()))
                .filter(c -> c.getTotalOrders() >= 3)
                .filter(c -> c.getLastOrderAt() != null && c.getLastOrderAt() < thirtyDaysAgo)
                .sorted(Comparator.comparing(CustomerProfile::getLastOrderAt, Comparator.nullsFirst(Long::compareTo)))
                .limit(20)
                .map(c -> Map.<String, Object>of(
                    "displayName", c.getDisplayName() != null ? c.getDisplayName() : "Customer",
                    "totalOrders", c.getTotalOrders(),
                    "totalSpend", c.getTotalSpend(),
                    "lastOrderAt", c.getLastOrderAt() != null ? c.getLastOrderAt() : 0,
                    "daysSinceLastOrder", c.getLastOrderAt() != null ? (System.currentTimeMillis() - c.getLastOrderAt()) / (24 * 60 * 60 * 1000) : 0
                ))
                .toList();
    }

    private String hashPhone(String phone) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(phone.trim().getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return phone.trim();
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return phone.substring(0, 2) + "****" + phone.substring(phone.length() - 2);
    }
}