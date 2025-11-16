package com.studyhub.StudyHub.dto;

import com.studyhub.StudyHub.entity.UserType; // <-- THÊM
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat; // <-- THÊM
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate; // <-- THÊM

@Data
public class ProfileUpdateDto {
    private String name;
    private String bio;
    // Dùng để nhận file avatar từ form
    private MultipartFile avatarFile;

    // === THÊM CÁC TRƯỜNG MỚI ===
    private MultipartFile coverPhotoFile; // Ảnh bìa
    private UserType userType;
    private String school;
    private String major;
    private String location;
    private String hometown;

    @DateTimeFormat(pattern = "yyyy-MM-dd") // Giúp Spring Boot parse date từ form
    private LocalDate birthday;

    private String contactPhone;
}