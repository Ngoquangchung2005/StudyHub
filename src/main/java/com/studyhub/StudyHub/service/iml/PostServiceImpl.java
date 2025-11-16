package com.studyhub.StudyHub.service.iml;

import com.studyhub.StudyHub.dto.CommentDto;
import com.studyhub.StudyHub.dto.PostDto;
import com.studyhub.StudyHub.entity.*;
import com.studyhub.StudyHub.repository.*;
import com.studyhub.StudyHub.service.PostService;
import com.studyhub.StudyHub.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class PostServiceImpl implements PostService {

    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StorageService storageService;
    @Autowired private CommentRepository commentRepository;
    @Autowired private ReactionRepository reactionRepository;

    // === THÊM DÒNG NÀY ===
    @Autowired private CategoryRepository categoryRepository;

    private User getCurrentUser(Principal principal) {
        String username = principal.getName();
        return userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Post> getAllPostsSortedByDate() {
        return postRepository.findAllWithDetails(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Override
    @Transactional
    public void createPost(PostDto postDto, Principal principal) {
        User user = getCurrentUser(principal);

        Post post = new Post();
        post.setContent(postDto.getContent());
        post.setUser(user);
        // --- BỔ SUNG LOGIC ---
        // Nếu content trống (đăng từ trang /upload),
        // thì tự động dùng Description hoặc Title của tài liệu làm content chính
        if (post.getContent() == null || post.getContent().trim().isEmpty()) {
            if (postDto.getDescription() != null && !postDto.getDescription().trim().isEmpty()) {
                // Ưu tiên 1: Dùng Description
                post.setContent(postDto.getDescription());
            } else if (postDto.getTitle() != null && !postDto.getTitle().trim().isEmpty()) {
                // Ưu tiên 2: Dùng Title (nếu Description cũng trống)
                post.setContent("Đã đăng tải tài liệu: " + postDto.getTitle());
            } else if (postDto.getFiles() != null && postDto.getFiles().length > 0 && !postDto.getFiles()[0].isEmpty()) {
                // Ưu tiên 3: Dùng tên file (nếu cả Title và Description đều trống)
                post.setContent("Đã đăng tải: " + postDto.getFiles()[0].getOriginalFilename());
            }
        }
        // --- KẾT THÚC BỔ SUNG ---

        // === THÊM ĐOẠN NÀY: Xử lý upload file ===
        Set<Document> documents = new HashSet<>();
        if (postDto.getFiles() != null && postDto.getFiles().length > 0) {
            for (MultipartFile file : postDto.getFiles()) {
                if (!file.isEmpty()) {
                    String storagePath = storageService.saveFile(file);

                    Document doc = new Document();
                    doc.setFileName(file.getOriginalFilename());
                    doc.setFileType(file.getContentType());
                    doc.setStoragePath(storagePath);
                    doc.setPost(post);
                    doc.setUser(user); // THÊM: Lưu user upload

                    // === THÊM: Lưu thông tin mới ===
                    doc.setTitle(postDto.getTitle() != null ? postDto.getTitle() : file.getOriginalFilename());
                    doc.setDescription(postDto.getDescription());
                    doc.setTags(postDto.getTags());
                    doc.setFileSize(file.getSize());
                    doc.setIsPublic(postDto.getIsPublic() != null ? postDto.getIsPublic() : true);

                    // === THÊM: Gán category nếu có ===
                    if (postDto.getCategoryId() != null) {
                        Category category = categoryRepository.findById(postDto.getCategoryId())
                                .orElse(null);
                        doc.setCategory(category);
                    }

                    documents.add(doc);
                }
            }
        }
        post.setDocuments(documents);

        postRepository.save(post);
    }

    // === CÁC METHOD CŨ (GIỮ NGUYÊN) ===
    @Override
    @Transactional
    public void addComment(Long postId, CommentDto commentDto, Principal principal) {
        User user = getCurrentUser(principal);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Post"));

        Comment comment = new Comment();
        comment.setContent(commentDto.getContent());
        comment.setUser(user);
        comment.setPost(post);

        commentRepository.save(comment);
    }

    @Override
    @Transactional
    public void toggleLike(Long postId, Principal principal) {
        User user = getCurrentUser(principal);

        Optional<Reaction> existingLike = reactionRepository.findByPostIdAndUserId(postId, user.getId());

        if (existingLike.isPresent()) {
            reactionRepository.delete(existingLike.get());
        } else {
            Post post = postRepository.findById(postId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy Post"));

            Reaction reaction = new Reaction();
            reaction.setType("LIKE");
            reaction.setUser(user);
            reaction.setPost(post);
            reactionRepository.save(reaction);
        }
    }
    @Override
    @Transactional(readOnly = true)
    public List<Post> getPostsByUser(User user) {
        // Gọi method mới từ repository
        return postRepository.findAllByUserWithDetails(user, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}