package com.studyhub.StudyHub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String content; // Nội dung: "A đã bình luận...", "B đã thích..."

    private String link; // Đường dẫn khi click vào (VD: /posts/123)

    private boolean isRead = false; // Đã xem chưa

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "recipient_id")
    private User recipient; // Người nhận thông báo

    @ManyToOne
    @JoinColumn(name = "sender_id")
    private User sender; // Người tạo ra thông báo
}