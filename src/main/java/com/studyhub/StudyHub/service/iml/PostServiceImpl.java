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

    // Helper: Lấy user hiện tại
    private User getCurrentUser(Principal principal) {
        String username = principal.getName();
        return userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));
    }

    @Override
    @Transactional(readOnly = true) // Chỉ đọc
    public List<Post> getAllPostsSortedByDate() {
        // Dùng query tối ưu (N+1)
        return postRepository.findAllWithDetails(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Override
    @Transactional // Có ghi CSDL
    public void createPost(PostDto postDto, Principal principal) {
        User user = getCurrentUser(principal);

        Post post = new Post();
        post.setContent(postDto.getContent());
        post.setUser(user);

        // Xử lý Upload File
        Set<Document> documents = new HashSet<>();
        if (postDto.getFiles() != null && postDto.getFiles().length > 0) {
            for (MultipartFile file : postDto.getFiles()) {
                if (!file.isEmpty()) {
                    String storagePath = storageService.saveFile(file); // Lưu file

                    Document doc = new Document();
                    doc.setFileName(file.getOriginalFilename());
                    doc.setFileType(file.getContentType());
                    doc.setStoragePath(storagePath);
                    doc.setPost(post);
                    documents.add(doc);
                }
            }
        }
        post.setDocuments(documents);

        // Lưu Post (và Document sẽ tự động lưu theo nhờ CascadeType.ALL)
        postRepository.save(post);
    }

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
        // (Sẽ nâng cấp gửi thông báo ở Phần 4)
    }

    @Override
    @Transactional
    public void toggleLike(Long postId, Principal principal) {
        User user = getCurrentUser(principal);

        // Kiểm tra xem đã like chưa
        Optional<Reaction> existingLike = reactionRepository.findByPostIdAndUserId(postId, user.getId());

        if (existingLike.isPresent()) {
            // Đã like -> Xóa like (Unlike)
            reactionRepository.delete(existingLike.get());
        } else {
            // Chưa like -> Thêm like
            Post post = postRepository.findById(postId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy Post"));

            Reaction reaction = new Reaction();
            reaction.setType("LIKE");
            reaction.setUser(user);
            reaction.setPost(post);
            reactionRepository.save(reaction);
            // (Sẽ nâng cấp gửi thông báo ở Phần 4)
        }
    }
}