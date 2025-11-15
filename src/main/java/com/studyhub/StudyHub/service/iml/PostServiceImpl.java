package com.studyhub.StudyHub.service.iml;



import com.studyhub.StudyHub.dto.PostDto;
import com.studyhub.StudyHub.entity.Document; // THÊM DÒNG NÀY
import com.studyhub.StudyHub.entity.Post;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.DocumentRepository; // THÊM DÒNG NÀY
import com.studyhub.StudyHub.repository.PostRepository;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.PostService;
import com.studyhub.StudyHub.service.StorageService; // THÊM DÒNG NÀY
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile; // THÊM DÒNG NÀY
import java.util.HashSet; // THÊM DÒNG NÀY
import java.util.Set; // THÊM DÒNG NÀY

import java.util.List;

@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StorageService storageService;
    @Autowired
    private DocumentRepository documentRepository;

    @Override
    public void createPost(PostDto postDto, String username) {
        // 1. Tìm người dùng (tác giả) đang đăng nhập
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

        // 2. Tạo đối tượng Post mới
        Post post = new Post();
        post.setContent(postDto.getContent());
        post.setUser(user); // Gán tác giả cho bài đăng

        // 3. Xử lý File Upload (Phần "xịn")
        Set<Document> documents = new HashSet<>();
        if (postDto.getFiles() != null && postDto.getFiles().length > 0) {

            for (MultipartFile file : postDto.getFiles()) {
                if (!file.isEmpty()) {
                    // 3a. Lưu file vào "nhà kho" (thư mục D:/studyhub-uploads)
                    // và nhận lại tên file duy nhất đã lưu.
                    String storagePath = storageService.saveFile(file);

                    // 3b. Tạo đối tượng Document mới
                    Document doc = new Document();
                    doc.setFileName(file.getOriginalFilename());
                    doc.setFileType(file.getContentType());
                    doc.setStoragePath(storagePath); // Tên file duy nhất
                    doc.setPost(post); // Liên kết file này với bài đăng

                    // 3c. Thêm vào danh sách (chưa lưu CSDL)
                    documents.add(doc);
                }
            }
        }

        // 4. Gán danh sách file vào bài đăng
        post.setDocuments(documents);

        // 5. Lưu bài đăng (Post)
        // Nhờ 'cascade = CascadeType.ALL' (trong Post.java),
        // khi ta lưu Post, tất cả Document trong danh sách cũng TỰ ĐỘNG được lưu.
        postRepository.save(post);
    }
    @Override
    public List<Post> getAllPostsSortedByDate() {
        // Gọi hàm repository và yêu cầu sắp xếp (Sort) theo cột 'createdAt'
        // theo thứ tự Giảm dần (Descending)
        return postRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

}