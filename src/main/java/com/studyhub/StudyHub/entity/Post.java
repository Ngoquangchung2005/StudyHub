package com.studyhub.StudyHub.entity;



import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@Entity // Báo cho Spring biết đây là một bảng trong CSDL
@Table(name = "posts") // Tên của bảng
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT") // Nội dung có thể rất dài
    private String content;

    @CreationTimestamp // Tự động điền ngày giờ tạo
    @Column(updatable = false) // Không cho phép cập nhật
    private LocalDateTime createdAt;

    // --- Quan hệ Nhiều-Một (Nhiều Post thuộc về Một User) ---
    @ManyToOne(fetch = FetchType.LAZY) // LAZY: Chỉ tải User khi thực sự cần
    @JoinColumn(name = "user_id", nullable = false) // Khóa ngoại trỏ đến bảng 'users'
    private User user;

    // Chúng ta sẽ thêm danh sách Document (tài liệu) ở đây sau
    // ... (code của Post ở trên) ...

    // --- Quan hệ Một-Nhiều (Một Post có Nhiều Document) ---
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    // cascade = ALL: Khi lưu Post, tự động lưu Document
    // orphanRemoval = true: Khi xóa Document khỏi Post, tự động xóa Document khỏi CSDL
    private Set<Document> documents = new HashSet<>();
}