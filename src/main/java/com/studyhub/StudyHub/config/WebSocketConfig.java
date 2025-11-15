package com.studyhub.StudyHub.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint để client kết nối (với SockJS)
        registry.addEndpoint("/ws").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 1. Dùng cho Server gửi tin nhắn ĐẾN Client
        // /topic: Dùng cho kênh công cộng (presence, chat nhóm)
        // /queue: Dùng cho kênh cá nhân (thông báo)
        registry.enableSimpleBroker("/topic", "/queue");

        // 2. Dùng cho Client gửi tin nhắn ĐẾN Server
        // (VD: Client gửi đến /app/chat.sendMessage)
        registry.setApplicationDestinationPrefixes("/app");

        // 3. (Quan trọng) Dùng để gửi tin nhắn 1-1
        // (VD: /user/chung/queue/notifications)
        registry.setUserDestinationPrefix("/user");
    }
}