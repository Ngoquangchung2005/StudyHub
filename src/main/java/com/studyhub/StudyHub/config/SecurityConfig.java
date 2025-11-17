package com.studyhub.StudyHub.config;


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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

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
                        .requestMatchers("/", "/login", "/register", "/js/**", "/css/**", "/ws/**").permitAll()
                        // Cho phép API tải lịch sử chat (sẽ dùng ở Phần 3)
                        .requestMatchers("/messages/**").authenticated()
                        .requestMatchers("/api/chat/**").authenticated() // THÊM DÒNG NÀY
                        // Tất cả các yêu cầu khác đều cần đăng nhập
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true) // Về trang chủ sau khi login
                        .failureUrl("/login?error=true")
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
                                                "style-src 'self' https://cdn.jsdelivr.net; " +
                                                "script-src 'self' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                                "img-src 'self' data:; " + // Cho phép ảnh
                                                "connect-src 'self' ws: wss: http: https:;" // <-- DÒNG QUAN TRỌNG ĐỂ CHAT LAN
                                )
                        )
                );

        return http.build();
    }
}