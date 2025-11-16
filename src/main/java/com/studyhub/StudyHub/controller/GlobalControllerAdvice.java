package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private UserRepository userRepository;

    /**
     * Thêm đối tượng 'currentUser' vào model cho TẤT CẢ các request.
     * Giúp layout.html luôn truy cập được avatarUrl.
     */
    @ModelAttribute("currentUser")
    public User getCurrentUser(Principal principal) {
        if (principal != null) {
            String usernameOrEmail = principal.getName();
            // Tìm user và trả về đầy đủ (gồm cả avatarUrl)
            return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                    .orElse(null); // Trả về null nếu không tìm thấy
        }
        return null; // Không ai đăng nhập
    }
}