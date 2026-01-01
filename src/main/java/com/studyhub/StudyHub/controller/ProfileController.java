package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.dto.CommentDto;
import com.studyhub.StudyHub.dto.ProfileUpdateDto;
import com.studyhub.StudyHub.entity.Post;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.entity.UserType;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.FriendService;
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
import com.studyhub.StudyHub.dto.ChangePasswordDto;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

@Controller
public class ProfileController {

    @Autowired private UserRepository userRepository;
    @Autowired private StorageService storageService;
    @Autowired private PostService postService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private FriendService friendService;

    // Helper: Lấy user hiện tại từ Principal
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

        User currentUser = null;
        boolean isOwner = false;
        String friendshipStatus = "NONE";
        Long friendshipId = null;

        // 2. Xác định người xem và trạng thái bạn bè
        if (principal != null) {
            currentUser = getCurrentUser(principal);
            model.addAttribute("currentUserId", currentUser.getId());

            // So sánh ID để xem có phải chính chủ không
            if (currentUser.getId().equals(profileUser.getId())) {
                isOwner = true;
                friendshipStatus = "SELF";
            } else {
                // Nếu không phải chính chủ, lấy trạng thái bạn bè từ Service
                friendshipStatus = friendService.getFriendshipStatus(currentUser.getId(), profileUser.getId());
                friendshipId = friendService.getFriendshipId(currentUser.getId(), profileUser.getId());
            }
        } else {
            model.addAttribute("currentUserId", 0L);
        }

        // 3. Lấy danh sách bài đăng (Nếu isOwner = true -> Lấy hết. Nếu false -> Chỉ lấy public)
        List<Post> posts = postService.getPostsByUser(profileUser, isOwner);

        // 4. Gửi thông tin sang view
        model.addAttribute("profileUser", profileUser);
        model.addAttribute("posts", posts);
        model.addAttribute("pageTitle", profileUser.getName());
        model.addAttribute("commentDto", new CommentDto());

        // Gửi các biến phục vụ cho nút kết bạn và quyền chỉnh sửa
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("friendshipStatus", friendshipStatus);
        model.addAttribute("friendshipId", friendshipId);

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

        // Load dữ liệu cũ
        dto.setUserType(user.getUserType());
        dto.setSchool(user.getSchool());
        dto.setMajor(user.getMajor());
        dto.setLocation(user.getLocation());
        dto.setHometown(user.getHometown());
        dto.setBirthday(user.getBirthday());
        dto.setContactPhone(user.getContactPhone());

        model.addAttribute("profileDto", dto);
        model.addAttribute("pageTitle", "Cài đặt tài khoản");

        // Gửi avatarUrl và coverPhotoUrl hiện tại để hiển thị
        model.addAttribute("currentAvatarUrl", user.getAvatarUrl());
        model.addAttribute("currentCoverPhotoUrl", user.getCoverPhotoUrl());

        // Gửi danh sách UserType (Enum)
        model.addAttribute("userTypes", UserType.values());

        return "profile-edit";
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

        // 1. Cập nhật thông tin
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
        return "change-password";
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

        // 3. Kiểm tra độ dài mật khẩu mới
        if (passwordDto.getNewPassword().length() < 6) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu mới phải có ít nhất 6 ký tự!");
            return "redirect:/profile/change-password";
        }

        // 4. Mã hóa và lưu mật khẩu mới
        user.setPassword(passwordEncoder.encode(passwordDto.getNewPassword()));
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("successMessage", "Đổi mật khẩu thành công!");
        return "redirect:/profile/edit";
    }
}