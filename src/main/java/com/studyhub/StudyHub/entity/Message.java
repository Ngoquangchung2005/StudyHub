package com.studyhub.StudyHub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;

    // Người gửi
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // Phòng chat
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    // Thu hồi tin nhắn
    @Column(name = "is_recalled", nullable = false)
    private boolean isRecalled = false;

    // === THÊM CÁC TRƯỜNG MỚI ===

    // Loại tin nhắn: TEXT, IMAGE, FILE
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private MessageType type = MessageType.TEXT;

    // Đường dẫn file (nếu là IMAGE hoặc FILE)
    @Column(length = 500)
    private String filePath;

    // Tên file gốc
    @Column(length = 255)
    private String fileName;

    // Kích thước file (bytes)
    @Column
    private Long fileSize;

    // Loại MIME
    @Column(length = 100)
    private String mimeType;

    public enum MessageType {
        TEXT,
        IMAGE,
        FILE
    }
}