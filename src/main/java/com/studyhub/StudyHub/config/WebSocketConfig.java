package com.studyhub.StudyHub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // "Bật" Message Broker (STOMP) lên
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Đăng ký một "Điểm cuối" (Endpoint) cho STOMP tại '/ws'
        // Đây là URL mà Client (JavaScript) sẽ dùng để kết nối
        // .withSockJS() là để bật phương án dự phòng SockJS
        registry.addEndpoint("/ws").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // --- CẤU HÌNH "BỘ MÔI GIỚI" (MESSAGE BROKER) ---

        // 1. Dùng một Message Broker đơn giản (in-memory)
        // Các "chủ đề" (topics) bắt đầu bằng /topic hoặc /queue sẽ được
        // broker này xử lý và gửi đến các client đã đăng ký.
        // /topic: Dùng cho chat công cộng (public, chat nhóm)
        // /queue: Dùng cho chat 1-1 (private)
        registry.enableSimpleBroker("/topic", "/queue");

        // 2. Định nghĩa "Tiền tố" cho các Endpoint của Ứng dụng
        // Các tin nhắn mà Client gửi LÊN server (ví dụ: gửi 1 tin chat)
        // phải có tiền tố /app.
        // Ví dụ: Client gửi tin nhắn đến /app/chat.sendMessage
        registry.setApplicationDestinationPrefixes("/app");
    }
}