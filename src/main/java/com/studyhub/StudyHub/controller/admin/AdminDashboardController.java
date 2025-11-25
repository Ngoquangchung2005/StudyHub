package com.studyhub.StudyHub.controller.admin;

import com.studyhub.StudyHub.repository.PostRepository;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private DocumentRepository documentRepository;

    @GetMapping
    public String dashboard(Model model) {
        // Thống kê sơ bộ
        long totalUsers = userRepository.count();
        long totalPosts = postRepository.count();
        long totalDocs = documentRepository.count();

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalPosts", totalPosts);
        model.addAttribute("totalDocs", totalDocs);
        model.addAttribute("pageTitle", "Dashboard");

        return "admin/dashboard";
    }
}