package com.studyhub.StudyHub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // === THÊM CÁC TRƯỜNG MỚI ===
    @Column(length = 255)
    private String title; // Tiêu đề tài liệu

    @Column(columnDefinition = "TEXT")
    private String description; // Mô tả

    @Column(length = 500)
    private String tags; // Tags: "java,spring,backend"

    @Column(nullable = false)
    private Long fileSize = 0L; // Kích thước file (bytes)

    @Column(nullable = false)
    private Integer views = 0; // Lượt xem

    @Column(nullable = false)
    private Integer downloads = 0; // Lượt tải

    @Column(nullable = false)
    private Boolean isPublic = true; // Công khai/Riêng tư

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime uploadedAt; // Thời gian upload
    // === KẾT THÚC TRƯỜNG MỚI ===

    // === CÁC TRƯỜNG CŨ (GIỮ NGUYÊN) ===
    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileType;

    @Column(nullable = false, unique = true)
    private String storagePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;
    // === KẾT THÚC TRƯỜNG CŨ ===

    // === THÊM QUAN HỆ VỚI CATEGORY ===
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // Người upload
    // === KẾT THÚC QUAN HỆ ===
}