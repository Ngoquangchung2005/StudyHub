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
        // Dùng cho Server gửi tin nhắn ĐẾN Client
        // /topic: Dùng cho kênh công cộng (presence, chat nhóm)
        // /queue: Dùng cho kênh cá nhân (thông báo)
        registry.enableSimpleBroker("/topic", "/queue");

        // Dùng cho Client gửi tin nhắn ĐẾN Server

        registry.setApplicationDestinationPrefixes("/app");

        // Dùng để gửi tin nhắn 1-1

        registry.setUserDestinationPrefix("/user");
    }
}