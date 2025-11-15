package com.studyhub.StudyHub.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName; // Tên file gốc (vi.pdf)

    @Column(nullable = false)
    private String fileType; // Loại file (application/pdf)

    @Column(nullable = false, unique = true)
    private String storagePath; // Đường dẫn lưu file trên server (ví dụ: uploads/abc-123.pdf)

    // --- Quan hệ Nhiều-Một (Nhiều Document thuộc về Một Post) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false) // Khóa ngoại trỏ đến bảng 'posts'
    private Post post;
}