package com.khanabook.saas.feature.sync.service;

import java.util.List;

import com.khanabook.saas.feature.sync.data.BaseSyncEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves individual sync records in their OWN transaction.
 *
 * Used as the per-record fallback after a batch saveAll() hits a unique-constraint
 * violation (e.g. ux_bills_restaurant_invoice_series_active). PostgreSQL aborts the
 * surrounding transaction on that failure — any further save executed inside the
 * original @Transactional push fails with "current transaction is aborted", which
 * previously meant every fallback save silently failed while successfulLocalIds
 * still reported success. Running each fallback save with REQUIRES_NEW commits it
 * independently of the aborted batch transaction.
 */
@Component
public class SyncFallbackSaver {

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public <T extends BaseSyncEntity, ID> T saveRecord(JpaRepository<T, ID> repository, T record) {
		return repository.save(record);
	}

	/**
	 * Runs the batch saveAll() in its OWN transaction so a batch-aborting failure
	 * (unique-constraint or optimistic-lock) rolls back only this inner transaction
	 * and never marks the outer handlePushSync transaction rollback-only. Before this
	 * existed, a caught batch failure still poisoned the outer commit — the caller
	 * threw UnexpectedRollbackException at the proxy boundary and the client received
	 * HTTP 500 instead of the per-record failedLocalIds response, so devices retried
	 * forever. With this, the response always reaches the client.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public <T extends BaseSyncEntity, ID> List<T> saveBatchInNewTx(JpaRepository<T, ID> repository, List<T> records) {
		return repository.saveAll(records);
	}
}
