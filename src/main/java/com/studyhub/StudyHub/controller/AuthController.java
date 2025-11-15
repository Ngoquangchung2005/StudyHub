package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.dto.RegisterDto;
import com.studyhub.StudyHub.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    @Autowired
    private AuthService authService;

    // Hiển thị form Đăng Ký
    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("userDto", new RegisterDto());
        model.addAttribute("pageTitle", "Đăng Ký Tài Khoản");
        return "register";
    }

    // Xử lý form Đăng Ký
    @PostMapping("/register")
    public String handleRegistration(@ModelAttribute("userDto") RegisterDto registerDto,
                                     RedirectAttributes redirectAttributes) {
        String result = authService.registerUser(registerDto);
        if (result.equals("Đăng ký thành công!")) {
            redirectAttributes.addFlashAttribute("successMessage", result);
            return "redirect:/login";
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", result);
            return "redirect:/register";
        }
    }

    // Hiển thị form Đăng Nhập
    @GetMapping("/login")
    public String showLoginForm(Model model) {
        model.addAttribute("pageTitle", "Đăng Nhập");
        return "login";
    }
}