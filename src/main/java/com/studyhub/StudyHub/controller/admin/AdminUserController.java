package com.studyhub.StudyHub.controller.admin;

import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("pageTitle", "Quản lý Người dùng");
        return "admin/users"; // Template admin/users.html
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

        // Không cho phép khóa chính admin
        if (user.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_ADMIN"))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể khóa tài khoản Admin!");
            return "redirect:/admin/users";
        }

        user.setEnabled(!user.isEnabled()); // Đảo trạng thái
        userRepository.save(user);

        String status = user.isEnabled() ? "Mở khóa" : "Đã khóa";
        redirectAttributes.addFlashAttribute("successMessage", status + " thành công user: " + user.getUsername());
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_ADMIN"))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa tài khoản Admin!");
            return "redirect:/admin/users";
        }
        userRepository.delete(user);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa user thành công.");
        return "redirect:/admin/users";
    }
}