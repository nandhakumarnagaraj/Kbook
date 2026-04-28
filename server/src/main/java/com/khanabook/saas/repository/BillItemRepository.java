package com.khanabook.saas.repository;

import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillItemRepository extends SyncRepository<BillItem, Long> {

	List<BillItem> findByServerBillIdAndIsDeletedFalseOrderById(Long serverBillId);
}
