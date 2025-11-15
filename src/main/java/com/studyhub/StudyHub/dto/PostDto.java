package com.studyhub.StudyHub.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class PostDto {
    private String content;
    // Nhận mảng file upload
    private MultipartFile[] files;
}