package com.studyhub.StudyHub.service;


import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Service // Đánh dấu đây là một Singleton Bean
public class PresenceService {

    // Dùng Set an toàn cho đa luồng (thread-safe)
    private final Set<String> onlineUsers = new CopyOnWriteArraySet<>();

    public void userConnected(String username) {
        onlineUsers.add(username);
    }

    public void userDisconnected(String username) {
        onlineUsers.remove(username);
    }

    public Set<String> getOnlineUsers() {
        return onlineUsers;
    }
}