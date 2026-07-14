package com.khanabook.saas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.khanabook.saas.entity.SecurityAuditEvent;

@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditEvent, Long> {
}
