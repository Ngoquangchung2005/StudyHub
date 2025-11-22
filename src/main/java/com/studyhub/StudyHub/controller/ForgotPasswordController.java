package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.entity.PasswordResetToken;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.PasswordResetTokenRepository;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Controller
public class ForgotPasswordController {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private EmailService emailService;
    @Autowired private PasswordEncoder passwordEncoder;

    // 1. Hiển thị form nhập email
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot-password";
    }

    // 2. Xử lý khi người dùng nhập email
    @PostMapping("/forgot-password")
    public String processForgotPassword(HttpServletRequest request,
                                        @RequestParam("email") String email,
                                        RedirectAttributes redirectAttributes) {
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tài khoản với email này.");
            return "redirect:/forgot-password";
        }

        User user = userOptional.get();
        String token = UUID.randomUUID().toString();

        // Lưu token vào DB
        PasswordResetToken myToken = new PasswordResetToken(token, user);
        tokenRepository.save(myToken);

        // Tạo link reset
        String appUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String resetLink = appUrl + "/reset-password?token=" + token;

        // Gửi email
        try {
            emailService.sendSimpleMessage(user.getEmail(),
                    "Yêu cầu đặt lại mật khẩu - StudyHub",
                    "Để đặt lại mật khẩu, vui lòng nhấp vào liên kết sau:\n" + resetLink);
            redirectAttributes.addFlashAttribute("successMessage", "Link đặt lại mật khẩu đã được gửi vào email của bạn.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi gửi email: " + e.getMessage());
        }

        return "redirect:/forgot-password";
    }

    // 3. Hiển thị form đặt lại mật khẩu (khi bấm link từ email)
    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);

        if (tokenOpt.isEmpty() || tokenOpt.get().getExpiryDate().isBefore(LocalDateTime.now())) {
            model.addAttribute("errorMessage", "Liên kết không hợp lệ hoặc đã hết hạn.");
            return "login"; // Hoặc trang thông báo lỗi
        }

        model.addAttribute("token", token);
        return "reset-password";
    }

    // 4. Xử lý đổi mật khẩu mới
    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam("token") String token,
                                       @RequestParam("password") String password,
                                       @RequestParam("confirmPassword") String confirmPassword,
                                       RedirectAttributes redirectAttributes) {

        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);
        if (tokenOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Token không hợp lệ.");
            return "redirect:/login";
        }

        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu xác nhận không khớp.");
            return "redirect:/reset-password?token=" + token;
        }

        // Cập nhật mật khẩu user
        User user = tokenOpt.get().getUser();
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        // Xóa token sau khi dùng xong
        tokenRepository.delete(tokenOpt.get());

        redirectAttributes.addFlashAttribute("successMessage", "Đặt lại mật khẩu thành công! Vui lòng đăng nhập.");
        return "redirect:/login";
    }
}