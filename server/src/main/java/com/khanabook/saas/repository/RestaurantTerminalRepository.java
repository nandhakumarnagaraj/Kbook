package com.khanabook.saas.repository;

import com.khanabook.saas.entity.RestaurantTerminal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface RestaurantTerminalRepository extends JpaRepository<RestaurantTerminal, Long> {

	/**
	 * Look up a terminal registration and take a pessimistic write lock so that
	 * concurrent invoice-sequence allocation for the same (restaurant, series)
	 * serializes on this row (PLAN §5, §4.2).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT t FROM RestaurantTerminal t
			WHERE t.restaurantId = :restaurantId
			  AND t.terminalSeries = :terminalSeries
			""")
	Optional<RestaurantTerminal> findAndLockByRestaurantIdAndTerminalSeries(
			@Param("restaurantId") Long restaurantId,
			@Param("terminalSeries") String terminalSeries);

	Optional<RestaurantTerminal> findByRestaurantIdAndTerminalSeries(Long restaurantId, String terminalSeries);

	Optional<RestaurantTerminal> findByRestaurantIdAndDeviceId(Long restaurantId, String deviceId);

	List<RestaurantTerminal> findByRestaurantIdOrderByIdAsc(Long restaurantId);

	long countByRestaurantIdAndStatus(Long restaurantId, String status);

	List<RestaurantTerminal> findByRestaurantIdAndStatus(Long restaurantId, String status);
}
