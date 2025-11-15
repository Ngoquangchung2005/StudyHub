package com.studyhub.StudyHub.service;



import com.studyhub.StudyHub.dto.RegisterDto;

public interface AuthService {
    // Định nghĩa một hàm để đăng ký người dùng
    String registerUser(RegisterDto registerDto);
}