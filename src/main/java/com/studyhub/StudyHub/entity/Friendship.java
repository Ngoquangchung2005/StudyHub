package com.studyhub.StudyHub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "friendships", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"requester_id", "addressee_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester; // Người gửi lời mời

    @ManyToOne
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee; // Người nhận lời mời

    @Enumerated(EnumType.STRING)
    private FriendshipStatus status; // PENDING (Chờ), ACCEPTED (Đã kết bạn)

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum FriendshipStatus {
        PENDING, ACCEPTED, BLOCKED
    }
}