package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.dto.CommentDto;
import com.studyhub.StudyHub.dto.ProfileUpdateDto;
import com.studyhub.StudyHub.entity.Post;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.entity.UserType; // <-- THÊM
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.PostService;
import com.studyhub.StudyHub.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.studyhub.StudyHub.dto.ChangePasswordDto; // Import DTO mới
import org.springframework.security.crypto.password.PasswordEncoder; // Import này

import java.io.IOException;
import java.security.Principal;
import java.util.List;

@Controller
public class ProfileController {

    @Autowired private UserRepository userRepository;
    @Autowired private StorageService storageService;
    @Autowired private PostService postService;
    // === THÊM DÒNG NÀY ===
    @Autowired private PasswordEncoder passwordEncoder;

    // Helper (lấy từ GlobalControllerAdvice cho chắc)
    private User getCurrentUser(Principal principal) {
        String usernameOrEmail = principal.getName();
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));
    }
    @GetMapping("/profile/{username}")
    public String showProfilePage(@PathVariable("username") String username, Model model, Principal principal) {
        // 1. Tìm user của trang profile (profileUser)
        User profileUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + username));

        // 2. Xác định người xem có phải là chính chủ không?
        boolean isOwner = false;
        if (principal != null) {
            User currentUser = getCurrentUser(principal);
            model.addAttribute("currentUserId", currentUser.getId());

            // So sánh ID
            if (currentUser.getId().equals(profileUser.getId())) {
                isOwner = true;
            }
        } else {
            model.addAttribute("currentUserId", 0L);
        }

        // 3. Lấy danh sách bài đăng (Truyền thêm biến isOwner)
        // Nếu isOwner = true -> Lấy hết. Nếu false -> Chỉ lấy public.
        List<Post> posts = postService.getPostsByUser(profileUser, isOwner);

        // 4. Gửi thông tin sang view
        model.addAttribute("profileUser", profileUser);
        model.addAttribute("posts", posts);
        model.addAttribute("pageTitle", profileUser.getName());
        model.addAttribute("commentDto", new CommentDto());

        return "profile-view";
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

        // === THÊM: Load dữ liệu cũ ===
        dto.setUserType(user.getUserType());
        dto.setSchool(user.getSchool());
        dto.setMajor(user.getMajor());
        dto.setLocation(user.getLocation());
        dto.setHometown(user.getHometown());
        dto.setBirthday(user.getBirthday());
        dto.setContactPhone(user.getContactPhone());
        // === KẾT THÚC ===

        model.addAttribute("profileDto", dto);
        model.addAttribute("pageTitle", "Cài đặt tài khoản");

        // Gửi avatarUrl hiện tại để hiển thị
        model.addAttribute("currentAvatarUrl", user.getAvatarUrl());
        // === THÊM: Gửi coverPhotoUrl ===
        model.addAttribute("currentCoverPhotoUrl", user.getCoverPhotoUrl());
        // Gửi danh sách UserType (Enum)
        model.addAttribute("userTypes", UserType.values());
        // === KẾT THÚC ===

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

        // 1. Cập nhật thông tin (Tên, Bio, và các trường mới)
        user.setName(profileDto.getName());
        user.setBio(profileDto.getBio());
        user.setUserType(profileDto.getUserType());
        user.setSchool(profileDto.getSchool());
        user.setMajor(profileDto.getMajor());
        user.setLocation(profileDto.getLocation());
        user.setHometown(profileDto.getHometown());
        user.setBirthday(profileDto.getBirthday());
        user.setContactPhone(profileDto.getContactPhone());


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

        // 3. Xử lý Upload Ảnh bìa
        MultipartFile coverFile = profileDto.getCoverPhotoFile();
        if (coverFile != null && !coverFile.isEmpty()) {
            try {
                // (Có thể thêm logic xóa file ảnh bìa cũ ở đây)
                String uniqueFileName = storageService.saveFile(coverFile);
                user.setCoverPhotoUrl(uniqueFileName);
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("errorMessage", "Lỗi upload ảnh bìa: " + e.getMessage());
                return "redirect:/profile/edit";
            }
        }

        // 4. Lưu user
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật profile thành công!");
        return "redirect:/profile/" + user.getUsername();
    }
    /**
     * Hiển thị trang Đổi mật khẩu
     */
    @GetMapping("/profile/change-password")
    public String showChangePasswordForm(Model model) {
        model.addAttribute("passwordDto", new ChangePasswordDto());
        model.addAttribute("pageTitle", "Đổi mật khẩu");
        return "change-password"; // Trả về file template mới
    }

    /**
     * Xử lý logic Đổi mật khẩu
     */
    @PostMapping("/profile/change-password")
    public String handleChangePassword(@ModelAttribute("passwordDto") ChangePasswordDto passwordDto,
                                       Principal principal,
                                       RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(principal);

        // 1. Kiểm tra mật khẩu hiện tại có đúng không
        if (!passwordEncoder.matches(passwordDto.getCurrentPassword(), user.getPassword())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu hiện tại không đúng!");
            return "redirect:/profile/change-password";
        }

        // 2. Kiểm tra mật khẩu mới và xác nhận có khớp không
        if (!passwordDto.getNewPassword().equals(passwordDto.getConfirmPassword())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu xác nhận không khớp!");
            return "redirect:/profile/change-password";
        }

        // 3. (Tùy chọn) Kiểm tra độ dài mật khẩu mới
        if (passwordDto.getNewPassword().length() < 6) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu mới phải có ít nhất 6 ký tự!");
            return "redirect:/profile/change-password";
        }

        // 4. Mã hóa và lưu mật khẩu mới
        user.setPassword(passwordEncoder.encode(passwordDto.getNewPassword()));
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("successMessage", "Đổi mật khẩu thành công!");
        return "redirect:/profile/edit"; // Quay lại trang cài đặt
    }
}