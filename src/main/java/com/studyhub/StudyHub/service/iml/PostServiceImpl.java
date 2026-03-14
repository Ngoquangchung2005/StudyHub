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
import org.springframework.security.access.AccessDeniedException;
import com.studyhub.StudyHub.service.NotificationService;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
// ... imports
import com.studyhub.StudyHub.entity.Comment;
@Service
public class PostServiceImpl implements PostService {

    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StorageService storageService;
    @Autowired private CommentRepository commentRepository;
    @Autowired private ReactionRepository reactionRepository;
    @Autowired private NotificationService notificationService;

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;

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

        // Lưu trạng thái công khai/riêng tư cho bài đăng
        post.setPublic(postDto.getIsPublic() != null ? postDto.getIsPublic() : true);

        // dùng Description hoặc Title của tài liệu làm content chính
        if (post.getContent() == null || post.getContent().trim().isEmpty()) {
            if (postDto.getDescription() != null && !postDto.getDescription().trim().isEmpty()) {
                // Dùng Description
                post.setContent(postDto.getDescription());
            } else if (postDto.getTitle() != null && !postDto.getTitle().trim().isEmpty()) {
                // Dùng Title (nếu Description cũng trống)
                post.setContent("Đã đăng tải tài liệu: " + postDto.getTitle());
            } else if (postDto.getFiles() != null && postDto.getFiles().length > 0 && !postDto.getFiles()[0].isEmpty()) {
                //  Dùng tên file (nếu cả Title và Description đều trống)
                post.setContent("Đã đăng tải: " + postDto.getFiles()[0].getOriginalFilename());
            }
        }


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
                    doc.setUser(user);


                    doc.setTitle(postDto.getTitle() != null ? postDto.getTitle() : file.getOriginalFilename());
                    doc.setDescription(postDto.getDescription());
                    doc.setTags(postDto.getTags());
                    doc.setFileSize(file.getSize());
                    doc.setIsPublic(postDto.getIsPublic() != null ? postDto.getIsPublic() : true);


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


        Comment savedComment = commentRepository.save(comment);

        // Gửi cho chủ bài viết
        String notiContent = user.getName() + " đã bình luận về bài viết của bạn.";
        String link = "/?keyword=" + post.getId();
        notificationService.sendNotification(user, post.getUser(), notiContent, link);

        try {
            CommentDto responseDto = new CommentDto(savedComment);
            // Gửi đến topic chung cho các comment: /topic/comments
            // (Client sẽ lọc xem comment này có thuộc bài viết đang hiển thị hay không)
            messagingTemplate.convertAndSend("/topic/comments", responseDto);
        } catch (Exception e) {
            e.printStackTrace(); // Log lỗi nhưng không chặn luồng chính
        }
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

            String link = "/?keyword=" + postId;

            notificationService.sendNotification(user, post.getUser(), notiContent, link);
        }
    }
    @Override
    @Transactional(readOnly = true)
    public List<Post> getPostsByUser(User user, boolean isOwner) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        if (isOwner) {
            // Nếu là chính chủ xem đc
            return postRepository.findAllByUserWithDetails(user, sort);
        } else {
            // Nếu là người khác xem bai cong khai
            return postRepository.findPublicByUserWithDetails(user, sort);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Post> searchPosts(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllPostsSortedByDate();
        }
        return postRepository.searchPosts(keyword.trim(), Sort.by(Sort.Direction.DESC, "createdAt"));
    }


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

        //  Kiểm tra quyền sở hữu
        if (!post.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền sửa bài viết này");
        }

        // Cập nhật thông tin chung

        if (postDto.getContent() != null && !postDto.getContent().trim().isEmpty()) {
            post.setContent(postDto.getContent());
        }

        if (postDto.getIsPublic() != null) {
            post.setPublic(postDto.getIsPublic());
        }


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

        //  Xử lý file mới (nếu người dùng upload thêm/thay thế)

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

        // Kiểm tra quyền sở hữu
        if (!post.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền xóa bài viết này");
        }



        postRepository.delete(post);
    }
    @Override
    @Transactional
    public void deleteComment(Long commentId, Principal principal) {
        User currentUser = getCurrentUser(principal);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bình luận"));

        // Kiểm tra quyền
        boolean isCommentOwner = comment.getUser().getId().equals(currentUser.getId());
        boolean isPostOwner = comment.getPost().getUser().getId().equals(currentUser.getId());

        if (!isCommentOwner && !isPostOwner) {
            throw new RuntimeException("Bạn không có quyền xóa bình luận này");
        }

        commentRepository.delete(comment);
    }


}