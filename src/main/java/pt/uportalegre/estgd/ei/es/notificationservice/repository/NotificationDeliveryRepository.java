package pt.uportalegre.estgd.ei.es.notificationservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationPriority;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    List<NotificationDelivery> findByNotificationId(Long notification);

    @Query("""
            SELECT d
            FROM NotificationDelivery d
            WHERE d.status = :status
              AND d.attemptCount < d.maxAttempts
            ORDER BY d.id ASC
            """)
    List<NotificationDelivery> findEligibleDeliveries(@Param("status") NotificationStatus status);

    @Query("""
            SELECT d.id
            FROM NotificationDelivery d
            JOIN d.notification n
            WHERE d.status = :status
              AND d.attemptCount < d.maxAttempts
              AND n.priority = :priority
            ORDER BY n.createdAt ASC, d.id ASC
            """)
    List<Long> findNextIdsByPriority(@Param("status") NotificationStatus status,
                                     @Param("priority") NotificationPriority priority,
                                     Pageable pageable);

    @Query("""
            SELECT d.id
            FROM NotificationDelivery d
            JOIN d.notification n
            WHERE d.status = :status
              AND d.attemptCount < d.maxAttempts
              AND n.createdAt <= :agingCutoff
            ORDER BY n.createdAt ASC, d.id ASC
            """)
    List<Long> findAgedIds(@Param("status") NotificationStatus status,
                           @Param("agingCutoff") LocalDateTime agingCutoff,
                           Pageable pageable);

    List<NotificationDelivery> findByStatusAndLockedAtBefore(NotificationStatus status, LocalDateTime lockedAt);

    long countByStatus(NotificationStatus status);

    long countByStatusAndNotificationPriority(NotificationStatus status, NotificationPriority priority);

    List<NotificationDelivery> findByStatusOrderByLockedAtAsc(NotificationStatus status);

    List<NotificationDelivery> findTop10ByStatusOrderBySentAtDesc(NotificationStatus status);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE NotificationDelivery d
            SET d.status = :processing,
                d.lockedBy = :lockedBy,
                d.lockedAt = :lockedAt,
                d.attemptCount = d.attemptCount + 1
            WHERE d.id = :id
              AND d.status = :pending
              AND d.attemptCount < d.maxAttempts
            """)
    int claim(@Param("id") Long id,
              @Param("pending") NotificationStatus pending,
              @Param("processing") NotificationStatus processing,
              @Param("lockedBy") String lockedBy,
              @Param("lockedAt") LocalDateTime lockedAt);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE NotificationDelivery d
            SET d.status = :newStatus,
                d.sentAt = :sentAt,
                d.lockedBy = null,
                d.lockedAt = null
            WHERE d.id = :id
              AND d.status = :processing
              AND d.lockedBy = :lockedBy
            """)
    int finish(@Param("id") Long id,
               @Param("lockedBy") String lockedBy,
               @Param("processing") NotificationStatus processing,
               @Param("newStatus") NotificationStatus newStatus,
               @Param("sentAt") LocalDateTime sentAt);
}
