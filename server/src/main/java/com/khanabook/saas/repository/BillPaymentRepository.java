package com.khanabook.saas.repository;

import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BillPaymentRepository extends SyncRepository<BillPayment, Long> {
    boolean existsByRestaurantIdAndGatewayTxnId(Long restaurantId, String gatewayTxnId);

    List<BillPayment> findByRestaurantIdAndServerBillIdInAndServerUpdatedAtGreaterThan(Long restaurantId, List<Long> serverBillIds, Long serverUpdatedAt);

    @Query("""
            SELECT p FROM BillPayment p
            JOIN Bill b ON p.serverBillId = b.id
            WHERE p.restaurantId = :restaurantId
              AND p.serverUpdatedAt > :lastSyncTimestamp
              AND (
                    b.createdTerminalId = :terminalId
                    OR b.currentOwnerTerminalId = :terminalId
                  )
            """)
    List<BillPayment> findUpdatedForTerminal(
            @Param("restaurantId") Long restaurantId,
            @Param("lastSyncTimestamp") Long lastSyncTimestamp,
            @Param("terminalId") String terminalId);
}
