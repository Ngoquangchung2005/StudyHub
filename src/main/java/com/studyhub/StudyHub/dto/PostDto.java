package com.studyhub.StudyHub.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class PostDto {
    private String content;
    private MultipartFile[] files;

    // === THÊM CÁC TRƯỜNG MỚI ===
    private String title; // Tiêu đề tài liệu
    private String description; // Mô tả
    private Long categoryId; // ID của category
    private String tags; // Tags: "java,spring,backend"
    private Boolean isPublic = true; // Công khai hay riêng tư
}