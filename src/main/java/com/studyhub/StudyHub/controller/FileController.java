package com.studyhub.StudyHub.controller;


import com.studyhub.StudyHub.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class FileController {

    @Autowired
    private StorageService storageService;

    // Endpoint này sẽ "bắt" các yêu cầu có dạng /download/ten-file-uuid.pdf
    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {

        // 1. Tải file từ service
        Resource resource = storageService.loadFileAsResource(fileName);

        // 2. Tạo tiêu đề (Header) "Content-Disposition"
        // Tiêu đề này "bảo" trình duyệt MỞ HỘP THOẠI DOWNLOAD
        // thay vì cố gắng hiển thị file (ví dụ: trình duyệt tự mở PDF)
        String headerValue = "attachment; filename=\"" + resource.getFilename() + "\"";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                .body(resource);
    }
}