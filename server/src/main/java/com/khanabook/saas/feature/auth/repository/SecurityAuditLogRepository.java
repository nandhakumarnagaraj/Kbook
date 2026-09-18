package com.khanabook.saas.feature.auth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.khanabook.saas.feature.auth.entity.SecurityAuditEvent;

@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditEvent, Long> {

	/**
	 * Audit events of the given actions for one tenant within a time window, oldest first.
	 * Used by the owner's daily bill-edit digest.
	 */
	@Query("""
			SELECT e FROM SecurityAuditEvent e
			WHERE e.restaurantId = :restaurantId
			  AND e.action IN :actions
			  AND e.createdAt >= :fromMillis
			  AND e.createdAt < :toMillis
			ORDER BY e.createdAt ASC
			""")
	List<SecurityAuditEvent> findForDigest(@Param("restaurantId") Long restaurantId,
			@Param("actions") List<String> actions,
			@Param("fromMillis") Long fromMillis,
			@Param("toMillis") Long toMillis);
}
