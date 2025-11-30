package com.studyhub.StudyHub.repository;

import com.studyhub.StudyHub.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying; // <-- Thêm
import org.springframework.data.jpa.repository.Query;     // <-- Thêm
import org.springframework.data.repository.query.Param;   // <-- Thêm
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
    long countByRecipientIdAndIsReadFalse(Long recipientId);

    // === THÊM HÀM NÀY ===
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :userId")
    void markAllAsRead(@Param("userId") Long userId);
    // === THÊM HÀM NÀY: Xóa tất cả thông báo của user ===
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipient.id = :userId")
    void deleteAllByRecipientId(@Param("userId") Long userId);
}