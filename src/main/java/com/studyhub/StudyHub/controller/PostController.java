package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.dto.CommentDto;
import com.studyhub.StudyHub.dto.PostDto;
import com.studyhub.StudyHub.entity.Document;
import com.studyhub.StudyHub.entity.Post;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.CategoryRepository; // THÊM DÒNG NÀY
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model; // THÊM DÒNG NÀY
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class PostController {

    @Autowired
    private PostService postService;

    // === THÊM DÒNG NÀY ===
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;

    // === THÊM METHOD MỚI: HIỂN thị TRANG UPLOAD TÀI LIỆU ===
    @GetMapping("/upload")
    public String showUploadPage(Model model) {
        model.addAttribute("pageTitle", "Đăng tải tài liệu");
        model.addAttribute("postDto", new PostDto());
        // Gửi danh sách categories sang view
        model.addAttribute("categories", categoryRepository.findAll());
        return "upload"; // Trả về file upload.html
    }

    // === CÁC METHOD CŨ (GIỮ NGUYÊN) ===
    @PostMapping("/posts/create")
    public String handleCreatePost(@ModelAttribute("postDto") PostDto postDto,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        postService.createPost(postDto, principal);
        redirectAttributes.addFlashAttribute("successMessage", "Đăng bài thành công!");
        return "redirect:/";
    }

    @PostMapping("/posts/{postId}/like")
    public String handleLike(@PathVariable("postId") Long postId, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        postService.toggleLike(postId, principal);
        return "redirect:/";
    }

    @PostMapping("/posts/{postId}/comment")
    public String handleComment(@PathVariable("postId") Long postId,
                                @ModelAttribute("commentDto") CommentDto commentDto,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        postService.addComment(postId, commentDto, principal);
        return "redirect:/";
    }

    // === SỬA HÀM showEditPage ===
    @GetMapping("/posts/edit/{id}")
    public String showEditPage(@PathVariable Long id, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            Post post = postService.getPostById(id);

            // === SỬA LOGIC KIỂM TRA QUYỀN ===
            // Lấy thông tin người dùng hiện tại từ database dựa trên principal (Email hoặc Username)
            String usernameOrEmail = principal.getName();
            User currentUser = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy user đang đăng nhập"));

            // So sánh ID của tác giả bài viết với ID người đang login
            if (!post.getUser().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền sửa bài này");
                return "redirect:/";
            }
            // ================================

            // Đổ dữ liệu cũ vào DTO
            PostDto postDto = new PostDto();
            postDto.setContent(post.getContent());
            postDto.setIsPublic(post.isPublic());

            // Nếu có tài liệu, lấy thông tin tài liệu đầu tiên để điền vào form
            if (!post.getDocuments().isEmpty()) {
                Document doc = post.getDocuments().iterator().next();
                postDto.setTitle(doc.getTitle());
                postDto.setDescription(doc.getDescription());
                postDto.setTags(doc.getTags());
                if (doc.getCategory() != null) {
                    postDto.setCategoryId(doc.getCategory().getId());
                }
            }

            model.addAttribute("postDto", postDto);
            model.addAttribute("postId", id);
            model.addAttribute("categories", categoryRepository.findAll());
            model.addAttribute("pageTitle", "Chỉnh sửa bài đăng");

            model.addAttribute("existingDocuments", post.getDocuments());

            return "post-edit"; // Bạn cần đảm bảo đã có file post-edit.html (tương tự upload.html)

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/";
        }
    }

    // === 2. XỬ LÝ SUBMIT FORM SỬA ===
    @PostMapping("/posts/edit/{id}")
    public String handleUpdatePost(@PathVariable Long id,
                                   @ModelAttribute("postDto") PostDto postDto,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {
        try {
            postService.updatePost(id, postDto, principal);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật bài viết thành công!");
            return "redirect:/"; // Hoặc redirect về trang profile
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi cập nhật: " + e.getMessage());
            return "redirect:/posts/edit/" + id;
        }
    }

    // === 3. XỬ LÝ XÓA BÀI ===
    @PostMapping("/posts/delete/{id}")
    public String handleDeletePost(@PathVariable Long id,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {
        try {
            postService.deletePost(id, principal);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa bài viết.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi xóa bài: " + e.getMessage());
        }
        // Trả về trang trước đó (Referer) hoặc trang chủ
        return "redirect:/";
    }

}