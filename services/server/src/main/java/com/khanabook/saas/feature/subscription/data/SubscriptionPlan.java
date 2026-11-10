package com.khanabook.saas.feature.subscription.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
public class SubscriptionPlan {

    @Id
    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_monthly", precision = 10, scale = 2, nullable = false)
    private BigDecimal priceMonthly = BigDecimal.ZERO;

    @Column(name = "price_yearly", precision = 10, scale = 2, nullable = false)
    private BigDecimal priceYearly = BigDecimal.ZERO;

    @Column(name = "max_terminals", nullable = false)
    private Integer maxTerminals = 1;

    @Column(name = "max_staff_members", nullable = false)
    private Integer maxStaffMembers = 5;

    @Column(name = "inventory_enabled", nullable = false)
    private Boolean inventoryEnabled = true;

    @Column(name = "ai_menu_enabled", nullable = false)
    private Boolean aiMenuEnabled = false;

    @Column(name = "online_payments_enabled", nullable = false)
    private Boolean onlinePaymentsEnabled = true;

    @Column(name = "advanced_reports_enabled", nullable = false)
    private Boolean advancedReportsEnabled = true;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
