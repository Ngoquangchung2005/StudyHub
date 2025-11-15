package com.studyhub.StudyHub.service.iml;



import com.studyhub.StudyHub.dto.RegisterDto;
import com.studyhub.StudyHub.entity.Role;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.RoleRepository;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // Bean ta đã tạo ở SecurityConfig

    @Override
    public String registerUser(RegisterDto registerDto) {

        // --- KIỂM TRA "XỊN" ---
        // 1. Kiểm tra xem username đã tồn tại trong CSDL chưa
        if(userRepository.findByUsername(registerDto.getUsername()).isPresent()) {
            // isPresent() trả về true nếu tìm thấy (Optional không rỗng)
            return "Username đã tồn tại!";
        }

        // 2. Kiểm tra xem email đã tồn tại trong CSDL chưa
        if(userRepository.findByEmail(registerDto.getEmail()).isPresent()) {
            return "Email đã tồn tại!";
        }

        // --- Nếu không trùng, tạo User mới ---
        User user = new User();
        user.setName(registerDto.getName());
        user.setUsername(registerDto.getUsername());
        user.setEmail(registerDto.getEmail());

        // 3. Mã hóa mật khẩu trước khi lưu
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));

        // 4. Gán vai trò (Role) cho user
        // Tìm Role "ROLE_USER" trong CSDL
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> {
                    // Nếu không có, tạo mới và lưu vào CSDL
                    Role newRole = new Role();
                    newRole.setName("ROLE_USER");
                    return roleRepository.save(newRole);
                });

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        user.setRoles(roles);

        // 5. Lưu User mới vào CSDL
        userRepository.save(user);

        return "Đăng ký thành công!";
    }
}
