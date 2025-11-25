package com.studyhub.StudyHub.config;


import com.studyhub.StudyHub.security.CustomAuthenticationSuccessHandler;
import com.studyhub.StudyHub.security.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
// ... các import
import com.studyhub.StudyHub.security.CustomAuthenticationFailureHandler; // <-- Import class vừa tạo

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Autowired
    private CustomAuthenticationFailureHandler customAuthenticationFailureHandler; // <-- Inject vào đây

    @Autowired
    private CustomUserDetailsService customUserDetailsService;
    @Autowired
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler; // <-- Inject

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // Cho phép tất cả mọi người truy cập trang chủ, login, register, file tĩnh
                        .requestMatchers("/", "/login", "/register", "/forgot-password", "/reset-password", "/js/**", "/css/**", "/ws/**").permitAll()
                        // Cho phép API tải lịch sử chat (sẽ dùng ở Phần 3)
                        .requestMatchers("/admin/**").hasRole("ADMIN") // <-- THÊM DÒNG NÀY
                        .requestMatchers("/messages/**").authenticated()
                        .requestMatchers("/api/chat/**").authenticated() // THÊM DÒNG NÀY
                        // Tất cả các yêu cầu khác đều cần đăng nhập
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(customAuthenticationSuccessHandler) // <-- THÊM DÒNG NÀY
                        .failureHandler(customAuthenticationFailureHandler) // <-- THÊM DÒNG NÀY VÀO
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll()
                )
                .authenticationProvider(authenticationProvider())
                // Cấu hình CSP (Content Security Policy)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                                // Cho phép CSS từ cdnjs (để tải FontAwesome)
                                                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                                // Cho phép Script
                                                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                                // Quan trọng: Cho phép tải Font từ cdnjs
                                                "font-src 'self' https://cdnjs.cloudflare.com; " +
                                                "img-src 'self' data:; " +
                                                "connect-src 'self' ws: wss: http: https:;"
                                )
                        )
                );

        return http.build();
    }
}