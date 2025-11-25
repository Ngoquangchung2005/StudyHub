package com.studyhub.StudyHub.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        // Kiểm tra xem lỗi có phải do tài khoản bị vô hiệu hóa (Disabled) không
        if (exception instanceof DisabledException) {
            // Chuyển hướng kèm param error=banned
            setDefaultFailureUrl("/login?error=banned");
        } else {
            // Các lỗi khác (sai pass, không tìm thấy user...) -> error=true
            setDefaultFailureUrl("/login?error=true");
        }

        super.onAuthenticationFailure(request, response, exception);
    }
}