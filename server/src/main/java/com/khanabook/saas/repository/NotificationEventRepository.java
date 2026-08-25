package com.khanabook.saas.repository;

import com.khanabook.saas.entity.NotificationEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    List<NotificationEvent> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    List<NotificationEvent> findByRestaurantIdAndIsReadFalseOrderByCreatedAtDesc(Long restaurantId);

    long countByRestaurantIdAndIsReadFalse(Long restaurantId);

    @Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM NotificationEvent n WHERE n.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@org.springframework.data.repository.query.Param("cutoff") Long cutoff);

    @Modifying
    @Query("UPDATE NotificationEvent n SET n.isRead = true, n.readAt = :now WHERE n.restaurantId = :restaurantId AND n.isRead = false")
    int markAllAsRead(@Param("restaurantId") Long restaurantId, @Param("now") Long now);

    @Modifying
    @Query("UPDATE NotificationEvent n SET n.isRead = true, n.readAt = :now WHERE n.id = :id")
    int markAsRead(@Param("id") Long id, @Param("now") Long now);

    void deleteByRestaurantIdAndCreatedAtBefore(Long restaurantId, Long beforeTimestamp);

    Optional<NotificationEvent> findTopByRestaurantIdAndNotificationTypeAndReferenceIdOrderByCreatedAtDesc(
        Long restaurantId, String notificationType, String referenceId);

    long countByRestaurantIdAndNotificationTypeAndCreatedAtAfter(
        Long restaurantId, String notificationType, Long after);
}
