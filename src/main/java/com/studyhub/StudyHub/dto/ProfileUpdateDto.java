package com.studyhub.StudyHub.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ProfileUpdateDto {
    private String name;
    private String bio;
    // Dùng để nhận file avatar từ form
    private MultipartFile avatarFile;
}