package com.khanabook.saas.storefront.repository;

import com.khanabook.saas.storefront.entity.CustomerOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerOrderItemRepository extends JpaRepository<CustomerOrderItem, Long> {
    List<CustomerOrderItem> findByCustomerOrderIdOrderByIdAsc(Long customerOrderId);
}
