package com.studyhub.StudyHub.service;


import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    // Định nghĩa hàm để lưu file
    // Nó sẽ trả về đường dẫn (path) duy nhất của file đã lưu
    String saveFile(MultipartFile file);
    // Dùng để tải file từ "nhà kho" (D:/studyhub-uploads)
    Resource loadFileAsResource(String fileName);
}