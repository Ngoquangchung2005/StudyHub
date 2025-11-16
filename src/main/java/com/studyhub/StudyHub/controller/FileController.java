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

    // Endpoint này dùng để TẢI XUỐNG (giữ nguyên)
    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        Resource resource = storageService.loadFileAsResource(fileName);

        String headerValue = "attachment; filename=\"" + resource.getFilename() + "\"";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                .body(resource);
    }

    // === THÊM ENDPOINT MỚI NÀY ===
    // Endpoint này dùng để XEM FILE (cho avatar, ảnh)
    @GetMapping("/view-file/{fileName:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String fileName) {
        Resource resource = storageService.loadFileAsResource(fileName);

        // Không set header "attachment", trình duyệt sẽ tự hiển thị
        return ResponseEntity.ok()
                .body(resource);
    }
}