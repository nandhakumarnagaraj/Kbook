package com.khanabook.saas.billing.repository;

import com.khanabook.saas.billing.domain.MarketplaceOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MarketplaceOrderItemRepository extends JpaRepository<MarketplaceOrderItem, Long> {
    List<MarketplaceOrderItem> findByMarketplaceOrderId(Long marketplaceOrderId);
    List<MarketplaceOrderItem> findByBillItemId(Long billItemId);
}
