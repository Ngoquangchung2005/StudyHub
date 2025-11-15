package com.studyhub.StudyHub.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data // Lombok: Tự tạo Getter, Setter...
public class PostDto {
    private String content;
    // Chúng ta sẽ thêm phần upload file ở đây sau
    private MultipartFile[] files;
}