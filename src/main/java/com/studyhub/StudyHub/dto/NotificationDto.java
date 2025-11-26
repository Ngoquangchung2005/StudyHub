package com.studyhub.StudyHub.dto;

import com.studyhub.StudyHub.entity.Notification;
import lombok.Data;

@Data
public class NotificationDto {
    private Long id;
    private String content;
    private String link;
    private boolean isRead;
    private String createdAt;
    private String senderName;
    private String senderAvatar;

    // Constructor chuyển đổi từ Entity sang DTO
    public NotificationDto(Notification notification) {
        this.id = notification.getId();
        this.content = notification.getContent();
        this.link = notification.getLink();
        this.isRead = notification.isRead();
        this.createdAt = notification.getCreatedAt().toString();

        // Lấy thông tin người gửi an toàn, tránh lỗi Proxy
        if (notification.getSender() != null) {
            this.senderName = notification.getSender().getName();
            this.senderAvatar = notification.getSender().getAvatarUrl();
        }
    }
}