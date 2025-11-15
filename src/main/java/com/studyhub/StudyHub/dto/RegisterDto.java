package com.studyhub.StudyHub.dto;


import lombok.Data;

@Data // Lombok: Tự tạo Getter, Setter, toString...
public class RegisterDto {
    private String name;
    private String username;
    private String email;
    private String password;
}