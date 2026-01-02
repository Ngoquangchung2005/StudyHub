package com.studyhub.StudyHub.config;

import com.studyhub.StudyHub.entity.Role;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.RoleRepository;
import com.studyhub.StudyHub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class DataSeeder implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {

        // ===== 1. Tạo ROLE_ADMIN nếu chưa tồn tại =====
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName("ROLE_ADMIN");
                    return roleRepository.save(role);
                });

        // ===== 2. Tạo ROLE_USER nếu chưa tồn tại =====
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName("ROLE_USER");
                    return roleRepository.save(role);
                });

        // ===== 3. Tạo tài khoản ADMIN nếu chưa tồn tại =====
        if (userRepository.findByUsername("admin").isEmpty()) {

            User admin = new User();
            admin.setName("Administrator");
            admin.setUsername("admin");
            admin.setEmail("admin@studyhub.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEnabled(true);

            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            roles.add(userRole);

            admin.setRoles(roles);

            userRepository.save(admin);
        }
    }
}
