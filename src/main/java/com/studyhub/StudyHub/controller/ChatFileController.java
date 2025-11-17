package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatFileController {

    @Autowired
    private StorageService storageService;

    /**
     * API upload file cho chat (ảnh hoặc file)
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadChatFile(@RequestParam("file") MultipartFile file) {
        try {
            // Kiểm tra file rỗng
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File rỗng"));
            }

            // Kiểm tra kích thước (Max 50MB)
            if (file.getSize() > 50 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("error", "File quá lớn (max 50MB)"));
            }

            // Lưu file
            String uniqueFileName = storageService.saveFile(file);

            // Trả về thông tin file
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("filePath", uniqueFileName);
            response.put("fileName", file.getOriginalFilename());
            response.put("fileSize", file.getSize());
            response.put("mimeType", file.getContentType());

            // Xác định loại tin nhắn (IMAGE hoặc FILE)
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                response.put("messageType", "IMAGE");
            } else {
                response.put("messageType", "FILE");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Lỗi upload: " + e.getMessage()));
        }
    }
}