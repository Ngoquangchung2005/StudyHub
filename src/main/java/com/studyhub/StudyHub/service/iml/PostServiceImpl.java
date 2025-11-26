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
import org.springframework.security.access.AccessDeniedException; // Thêm import này
import com.studyhub.StudyHub.service.NotificationService; // <-- THÊM

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
    @Autowired private NotificationService notificationService;

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
        // === THÊM DÒNG NÀY ===
        // Lưu trạng thái công khai/riêng tư cho bài đăng
        post.setPublic(postDto.getIsPublic() != null ? postDto.getIsPublic() : true);
        // =====================
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
        // === THÊM ĐOẠN NÀY: GỬI THÔNG BÁO ===
        // Gửi cho chủ bài viết
        String notiContent = user.getName() + " đã bình luận về bài viết của bạn.";
        String link = "/?keyword=" + post.getId(); // Hoặc link chi tiết bài viết nếu có
        notificationService.sendNotification(user, post.getUser(), notiContent, link);
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
            String notiContent = user.getName() + " đã thích bài viết của bạn.";
            // Chú ý: post.getUser() là chủ bài viết
            notificationService.sendNotification(user, post.getUser(), notiContent, "#post-" + postId);
        }
    }
    @Override
    @Transactional(readOnly = true)
    public List<Post> getPostsByUser(User user, boolean isOwner) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        if (isOwner) {
            // Nếu là chính chủ: Xem được HẾT (gọi hàm cũ)
            return postRepository.findAllByUserWithDetails(user, sort);
        } else {
            // Nếu là người khác: Chỉ xem bài CÔNG KHAI (gọi hàm mới vừa thêm ở Bước 1)
            return postRepository.findPublicByUserWithDetails(user, sort);
        }
    }
    // === THÊM MỚI: Triển khai hàm tìm kiếm ===
    @Override
    @Transactional(readOnly = true)
    public List<Post> searchPosts(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllPostsSortedByDate();
        }
        return postRepository.searchPosts(keyword.trim(), Sort.by(Sort.Direction.DESC, "createdAt"));
    }
    // === TRIỂN KHAI CÁC HÀM MỚI ===

    @Override
    @Transactional(readOnly = true)
    public Post getPostById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết"));
    }

    @Override
    @Transactional
    public void updatePost(Long postId, PostDto postDto, Principal principal) {
        User user = getCurrentUser(principal);
        Post post = getPostById(postId);

        // 1. Kiểm tra quyền sở hữu
        if (!post.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền sửa bài viết này");
        }

        // 2. Cập nhật thông tin chung
        // Nếu content rỗng (do form upload chỉ nhập description), ta giữ logic cũ hoặc cập nhật
        if (postDto.getContent() != null && !postDto.getContent().trim().isEmpty()) {
            post.setContent(postDto.getContent());
        }

        if (postDto.getIsPublic() != null) {
            post.setPublic(postDto.getIsPublic());
        }

        // 3. Cập nhật thông tin Tài liệu (nếu có)
        // Ở đây ta giả định sửa bài là sửa thông tin của tài liệu đầu tiên (nếu đăng dạng tài liệu)
        if (!post.getDocuments().isEmpty()) {
            Document doc = post.getDocuments().iterator().next();
            if (postDto.getTitle() != null) doc.setTitle(postDto.getTitle());
            if (postDto.getDescription() != null) {
                doc.setDescription(postDto.getDescription());
                // Đồng bộ lại content bài viết nếu cần
                post.setContent(postDto.getDescription());
            }
            if (postDto.getTags() != null) doc.setTags(postDto.getTags());
            if (postDto.getCategoryId() != null) {
                categoryRepository.findById(postDto.getCategoryId()).ifPresent(doc::setCategory);
            }
            // Đồng bộ quyền riêng tư của tài liệu theo bài viết
            if (postDto.getIsPublic() != null) {
                doc.setIsPublic(postDto.getIsPublic());
            }
        }

        // 4. Xử lý file mới (nếu người dùng upload thêm/thay thế)
        // (Phần này tùy chọn: nếu bạn muốn upload thêm file vào bài cũ)
        if (postDto.getFiles() != null && postDto.getFiles().length > 0) {
            for (MultipartFile file : postDto.getFiles()) {
                if (!file.isEmpty()) {
                    String storagePath = storageService.saveFile(file);
                    Document newDoc = new Document();
                    // Copy các thuộc tính metadata
                    newDoc.setFileName(file.getOriginalFilename());
                    newDoc.setFileType(file.getContentType());
                    newDoc.setStoragePath(storagePath);
                    newDoc.setPost(post);
                    newDoc.setUser(user);
                    newDoc.setTitle(postDto.getTitle() != null ? postDto.getTitle() : file.getOriginalFilename());
                    newDoc.setDescription(postDto.getDescription());
                    newDoc.setTags(postDto.getTags());
                    newDoc.setFileSize(file.getSize());
                    newDoc.setIsPublic(post.isPublic());

                    if (postDto.getCategoryId() != null) {
                        categoryRepository.findById(postDto.getCategoryId()).ifPresent(newDoc::setCategory);
                    }

                    post.getDocuments().add(newDoc);
                }
            }
        }

        postRepository.save(post);
    }

    @Override
    @Transactional
    public void deletePost(Long postId, Principal principal) {
        User user = getCurrentUser(principal);
        Post post = getPostById(postId);

        // 1. Kiểm tra quyền sở hữu
        if (!post.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền xóa bài viết này");
        }

        // 2. Xóa (Cascade ALL trong Entity Post sẽ tự xóa Documents, Comments, Reactions)
        // Lưu ý: File vật lý trên ổ cứng chưa được xóa ở đây (để đơn giản hóa),
        // bạn có thể thêm logic xóa file trong StorageService nếu muốn.
        postRepository.delete(post);
    }

}