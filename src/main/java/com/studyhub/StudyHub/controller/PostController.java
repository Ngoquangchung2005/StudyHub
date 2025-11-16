package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.dto.CommentDto;
import com.studyhub.StudyHub.dto.PostDto;
import com.studyhub.StudyHub.repository.CategoryRepository; // THÊM DÒNG NÀY
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
}