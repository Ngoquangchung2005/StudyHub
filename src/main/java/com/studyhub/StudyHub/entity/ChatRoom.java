package com.studyhub.StudyHub.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "chat_rooms")
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tên phòng (cho chat 1-1 có thể là null, cho chat nhóm là tên nhóm)
    @Column(length = 100)
    private String name;

    // Phân biệt chat 1-1 và chat nhóm
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomType type;


    // Chủ nhóm (chỉ áp dụng cho GROUP)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private User owner;

    // Danh sách admin của nhóm (chỉ áp dụng cho GROUP)
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "chat_room_admins",
            joinColumns = @JoinColumn(name = "room_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> admins = new HashSet<>();

    // Thành viên trong phòng
    @ManyToMany(fetch = FetchType.EAGER) // EAGER để lấy thành viên
    @JoinTable(
            name = "chat_room_members",
            joinColumns = @JoinColumn(name = "room_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> members = new HashSet<>();
    // CascadeType.ALL: Khi xóa Room, xóa luôn Messages
    // orphanRemoval = true: Xóa các message mồ côi
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Message> messages = new HashSet<>();
    // ==========================

    public enum RoomType {
        ONE_TO_ONE,
        GROUP
    }
}