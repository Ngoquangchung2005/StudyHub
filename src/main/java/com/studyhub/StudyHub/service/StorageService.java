package com.studyhub.StudyHub.service;




import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    // Lưu file, trả về tên file duy nhất đã lưu
    String saveFile(MultipartFile file);

    // Tải file
    Resource loadFileAsResource(String fileName);
}