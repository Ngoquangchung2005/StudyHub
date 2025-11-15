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

    // --- Hàm 1: Hiển thị form Đăng Ký ---
    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        // Tạo một đối tượng RegisterDto rỗng để Thymeleaf "bind" (kết dính) dữ liệu
        model.addAttribute("userDto", new RegisterDto());
        model.addAttribute("pageTitle", "Đăng Ký Tài Khoản");
        return "register"; // Trả về file 'register.html'
    }

    // --- Hàm 2: Xử lý dữ liệu từ form Đăng Ký ---
    @PostMapping("/register")
    public String handleRegistration(@ModelAttribute("userDto") RegisterDto registerDto,
                                     RedirectAttributes redirectAttributes) {

        // Gọi service để xử lý đăng ký
        String result = authService.registerUser(registerDto);

        if (result.equals("Đăng ký thành công!")) {
            // Gửi một thông báo thành công sang trang login
            redirectAttributes.addFlashAttribute("successMessage", result);
            return "redirect:/login"; // Chuyển hướng về trang login
        } else {
            // Gửi một thông báo lỗi ngược lại trang register
            redirectAttributes.addFlashAttribute("errorMessage", result);
            return "redirect:/register"; // Ở lại trang register
        }
    }
    // --- Hàm 3: Hiển thị form Đăng Nhập ---
    @GetMapping("/login")
    public String showLoginForm(Model model) {
        model.addAttribute("pageTitle", "Đăng Nhập");
        return "login"; // Trả về file 'login.html'
    }
}