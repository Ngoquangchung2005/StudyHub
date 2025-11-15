package com.studyhub.StudyHub.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity // Báo cho Spring biết đây là một bảng trong CSDL
@Table(name = "messages") // Tên của bảng
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT") // Nội dung tin nhắn
    private String content;

    @CreationTimestamp // Tự động điền ngày giờ tạo
    @Column(updatable = false)
    private LocalDateTime timestamp;

    // --- Quan hệ Nhiều-Một (Nhiều Message gửi từ Một User - NGƯỜI GỬI) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false) // Khóa ngoại trỏ đến bảng 'users'
    private User sender;

    // --- Quan hệ Nhiều-Một (Nhiều Message gửi đến Một User - NGƯỜI NHẬN) ---
    // (Chúng ta sẽ làm chat 1-1 trước, chat nhóm sau)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false) // Khóa ngoại trỏ đến bảng 'users'
    private User recipient;
}