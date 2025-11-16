package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.dto.CommentDto; // <-- THÊM
import com.studyhub.StudyHub.dto.ProfileUpdateDto;
import com.studyhub.StudyHub.entity.Post; // <-- THÊM
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.PostService; // <-- THÊM
import com.studyhub.StudyHub.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable; // <-- THÊM
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;
import java.util.List; // <-- THÊM

@Controller
public class ProfileController {

    @Autowired private UserRepository userRepository;
    @Autowired private StorageService storageService;
    @Autowired private PostService postService;

    // Helper (lấy từ GlobalControllerAdvice cho chắc)
    private User getCurrentUser(Principal principal) {
        String usernameOrEmail = principal.getName();
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));
    }
    // === THÊM METHOD MỚI: TRANG PROFILE CÔNG KHAI ===
    @GetMapping("/profile/{username}")
    public String showProfilePage(@PathVariable("username") String username, Model model, Principal principal) {
        // 1. Tìm user đang xem
        User profileUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + username));

        // 2. Lấy danh sách bài đăng của user đó
        List<Post> posts = postService.getPostsByUser(profileUser);

        // 3. Gửi thông tin sang view
        model.addAttribute("profileUser", profileUser); // Thông tin user (avatar, bio...)
        model.addAttribute("posts", posts); // Danh sách bài đăng
        model.addAttribute("pageTitle", profileUser.getName());

        // 4. Gửi DTO rỗng cho form bình luận (giống hệt index.html)
        model.addAttribute("commentDto", new CommentDto());

        // 5. Gửi ID user đang đăng nhập (để check Like)
        if (principal != null) {
            User currentUser = getCurrentUser(principal);
            model.addAttribute("currentUserId", currentUser.getId());
        } else {
            model.addAttribute("currentUserId", 0L);
        }

        return "profile-view"; // Trả về file profile-view.html
    }
    /**
     * Hiển thị form "Cài đặt" (để edit profile và upload avatar)
     */
    @GetMapping("/profile/edit")
    public String showEditProfileForm(Model model, Principal principal) {
        User user = getCurrentUser(principal);

        // Tạo DTO từ User hiện tại
        ProfileUpdateDto dto = new ProfileUpdateDto();
        dto.setName(user.getName());
        dto.setBio(user.getBio());

        model.addAttribute("profileDto", dto);
        model.addAttribute("pageTitle", "Cài đặt tài khoản");

        // Gửi avatarUrl hiện tại để hiển thị
        model.addAttribute("currentAvatarUrl", user.getAvatarUrl());

        return "profile-edit"; // Trả về file profile-edit.html
    }

    /**
     * Xử lý việc cập nhật profile và upload avatar
     */
    @PostMapping("/profile/edit")
    public String handleEditProfile(
            @ModelAttribute("profileDto") ProfileUpdateDto profileDto,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        User user = getCurrentUser(principal);

        // 1. Cập nhật thông tin (Tên, Bio)
        user.setName(profileDto.getName());
        user.setBio(profileDto.getBio());

        // 2. Xử lý Upload Avatar
        MultipartFile avatarFile = profileDto.getAvatarFile();
        if (avatarFile != null && !avatarFile.isEmpty()) {
            try {
                // (Có thể thêm logic xóa file avatar cũ ở đây)

                String uniqueFileName = storageService.saveFile(avatarFile);
                user.setAvatarUrl(uniqueFileName);
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("errorMessage", "Lỗi upload avatar: " + e.getMessage());
                return "redirect:/profile/edit";
            }
        }

        // 3. Lưu user
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật profile thành công!");
        return "redirect:/profile/edit";
    }
}