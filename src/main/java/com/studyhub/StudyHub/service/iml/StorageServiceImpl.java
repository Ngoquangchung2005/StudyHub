package com.studyhub.StudyHub.service.iml;



import com.studyhub.StudyHub.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID; // Dùng để tạo tên file duy nhất
import org.springframework.core.io.Resource; // THÊM DÒNG NÀY
import org.springframework.core.io.UrlResource; // THÊM DÒNG NÀY
import java.net.MalformedURLException; // THÊM DÒNG NÀY

@Service
public class StorageServiceImpl implements StorageService {

    // Đọc đường dẫn thư mục upload từ file application.properties
    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public String saveFile(MultipartFile file) {
        // 1. Kiểm tra file rỗng
        if (file.isEmpty()) {
            throw new RuntimeException("Không thể lưu file rỗng.");
        }

        try {
            // 2. Tạo đường dẫn (Path) đến thư mục upload
            Path uploadPath = Paths.get(uploadDir);
            // Nếu thư mục chưa tồn tại, tạo nó
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 3. Tạo tên file duy nhất (để tránh trùng lặp)
            // Ví dụ: "chung.pdf" -> "random-uuid-chung.pdf"
            String originalFileName = file.getOriginalFilename();
            String uniqueFileName = UUID.randomUUID().toString() + "-" + originalFileName;

            // 4. Tạo đường dẫn (Path) đầy đủ đến file mới
            Path filePath = uploadPath.resolve(uniqueFileName);

            // 5. Sao chép file từ request vào đường dẫn mới
            Files.copy(file.getInputStream(), filePath);

            // 6. Trả về tên file duy nhất (để lưu vào CSDL)
            return uniqueFileName;

        } catch (IOException e) {
            throw new RuntimeException("Không thể lưu file: " + e.getMessage());
        }
    }
    @Override
    public Resource loadFileAsResource(String fileName) {
        try {
            // 1. Lấy đường dẫn (Path) đến thư mục upload
            Path uploadPath = Paths.get(uploadDir);
            // 2. Lấy đường dẫn đầy đủ đến file
            Path filePath = uploadPath.resolve(fileName).normalize();

            // 3. Tải file dưới dạng Resource
            Resource resource = new UrlResource(filePath.toUri());

            // 4. Kiểm tra xem file có tồn tại không
            if (resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("Không tìm thấy file: " + fileName);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Lỗi đường dẫn file: " + fileName, e);
        }
    }
}