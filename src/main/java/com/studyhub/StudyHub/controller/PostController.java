package com.studyhub.StudyHub.controller;


import com.studyhub.StudyHub.dto.CommentDto;
import com.studyhub.StudyHub.dto.PostDto;
import com.studyhub.StudyHub.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class PostController {

    @Autowired
    private PostService postService;

    // Xử lý tạo bài đăng
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

    // Xử lý Like/Unlike
    @PostMapping("/posts/{postId}/like")
    public String handleLike(@PathVariable("postId") Long postId, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        postService.toggleLike(postId, principal);
        return "redirect:/"; // Tải lại trang (sẽ nâng cấp bằng AJAX)
    }

    // Xử lý Thêm bình luận
    @PostMapping("/posts/{postId}/comment")
    public String handleComment(@PathVariable("postId") Long postId,
                                @ModelAttribute("commentDto") CommentDto commentDto,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        postService.addComment(postId, commentDto, principal);
        return "redirect:/"; // Tải lại trang (sẽ nâng cấp bằng AJAX)
    }
}