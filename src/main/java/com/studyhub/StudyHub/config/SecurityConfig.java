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
                        .requestMatchers("/", "/login", "/register", "/download/**", "/ws/**", "/js/**", "/css/**").permitAll()
                        .anyRequest().authenticated()
                )
                // ... (phần code authorizeHttpRequests của bạn) ...
                .formLogin(form -> form
                        .loginPage("/login") // ĐƯỜNG DẪN TỚI TRANG ĐĂNG NHẬP (do ta tạo)
                        .loginProcessingUrl("/login") // URL MÀ SPRING SẼ "LẮNG NGHE" YÊU CẦU POST
                        .defaultSuccessUrl("/", true) // URL KHI ĐĂNG NHẬP THÀNH CÔNG
                        .failureUrl("/login?error=true") // URL KHI ĐĂNG NHẬP THẤT BẠI
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout") // URL ĐỂ KÍCH HOẠT ĐĂNG XUẤT
                        .logoutSuccessUrl("/login?logout=true") // URL SAU KHI ĐĂNG XUẤT
                        .permitAll()
                )
// ... (phần code còn lại của bạn) ...
                .authenticationProvider(authenticationProvider())

                // --- THÊM PHẦN NÀY ĐỂ CHO PHÉP BOOTSTRAP (CDN) ---
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " + // Mặc định chỉ cho phép từ domain của mình
                                                "style-src 'self' https://cdn.jsdelivr.net; " + // Cho phép CSS từ domain của mình VÀ cdn
                                                "script-src 'self' https://cdn.jsdelivr.net"  // Cho phép JS từ domain của mình VÀ cdn
                                )
                        )
                );
        // --- KẾT THÚC PHẦN THÊM ---

        return http.build();
    }
}