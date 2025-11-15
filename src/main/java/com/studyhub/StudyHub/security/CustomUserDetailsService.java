package com.studyhub.StudyHub.security;


import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service // Báo cho Spring biết đây là một Dịch vụ
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired // Tự động tiêm UserRepository vào đây
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        // Tìm user trong CSDL bằng username hoặc email
        User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Không tìm thấy người dùng với: " + usernameOrEmail));

        // Chuyển đổi Set<Role> của User thành Set<GrantedAuthority> mà Spring Security yêu cầu
        Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toSet());

        // Trả về một đối tượng UserDetails (đối tượng mà Spring Security sử dụng)
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), // Chúng ta sẽ dùng email để đăng nhập
                user.getPassword(), // Mật khẩu đã được mã hóa
                authorities // Quyền (roles) của người dùng
        );
    }
}