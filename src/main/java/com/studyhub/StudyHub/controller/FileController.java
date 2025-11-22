package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.entity.Document;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.DocumentRepository;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;

@Controller
public class FileController {

    @Autowired
    private StorageService storageService;
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private UserRepository userRepository;

    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<?> downloadFile(@PathVariable String fileName, Principal principal) {
        // 1. Tìm thông tin tài liệu trong Database
        Document doc = documentRepository.findByStoragePath(fileName).orElse(null);

        if (doc == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File không tồn tại trong hệ thống");
        }

        // 2. Kiểm tra quyền truy cập
        // Cho phép tải nếu: File công khai HOẶC Người tải là chủ sở hữu
        boolean isOwner = false;
        if (principal != null) {
            String currentUsername = principal.getName();
            // So sánh username của người đang login với người upload
            if (doc.getUser().getUsername().equals(currentUsername) ||
                    doc.getUser().getEmail().equals(currentUsername)) {
                isOwner = true;
            }
        }

        if (!doc.getIsPublic() && !isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Bạn không có quyền tải tài liệu riêng tư này.");
        }

        // 3. Tăng lượt tải (nếu muốn)
        doc.setDownloads(doc.getDownloads() + 1);
        documentRepository.save(doc);

        // 4. Tải file từ ổ cứng
        Resource resource = storageService.loadFileAsResource(fileName);
        String headerValue = "attachment; filename=\"" + doc.getFileName() + "\""; // Dùng tên gốc của file (doc.getFileName)

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                .body(resource);
    }

    @GetMapping("/view-file/{fileName:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String fileName) {
        // Hàm xem ảnh (avatar, cover) thì thường không cần check public/private gắt gao
        // hoặc tùy logic của bạn. Ở đây giữ nguyên để hiển thị ảnh.
        Resource resource = storageService.loadFileAsResource(fileName);
        return ResponseEntity.ok().body(resource);
    }
}