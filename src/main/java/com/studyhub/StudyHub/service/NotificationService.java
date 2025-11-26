package com.studyhub.StudyHub.service;

import com.studyhub.StudyHub.dto.NotificationDto; // <-- Import DTO
import com.studyhub.StudyHub.entity.Notification;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void sendNotification(User sender, User recipient, String content, String link) {
        if (sender.getId().equals(recipient.getId())) return;

        // 1. Lưu vào Database (Giữ nguyên)
        Notification notification = new Notification();
        notification.setSender(sender);
        notification.setRecipient(recipient);
        notification.setContent(content);
        notification.setLink(link);
        notification.setRead(false);
        Notification savedNotification = notificationRepository.save(notification);

        // 2. Chuyển sang DTO (Giữ nguyên)
        NotificationDto dto = new NotificationDto(savedNotification);

        // 3. Gửi DTO qua WebSocket - SỬA DÒNG NÀY
        // Đổi recipient.getUsername() thành recipient.getEmail()
        messagingTemplate.convertAndSendToUser(
                recipient.getEmail(), // <--- QUAN TRỌNG: Phải dùng Email để khớp với Security
                "/queue/notifications",
                dto
        );
    }
    // Hàm đánh dấu đã đọc
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    // Các hàm hỗ trợ khác
    @Transactional(readOnly = true)
    public List<NotificationDto> getUserNotifications(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId)
                .stream().map(NotificationDto::new).collect(Collectors.toList());
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }
}