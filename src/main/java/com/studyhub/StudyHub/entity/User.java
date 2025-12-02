package com.studyhub.StudyHub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@NoArgsConstructor
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "email")
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String username;
    private String email;
    private String password;

    // === TRƯỜNG CŨ (PROFILE) ===
    @Column(length = 255)
    private String avatarUrl; // Sẽ lưu tên file duy nhất (VD: abc-123.jpg)

    @Column(columnDefinition = "TEXT")
    private String bio; // Mô tả tiểu sử


    @Column(length = 255)
    private String coverPhotoUrl; // Ảnh bìa

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private UserType userType; // Sinh viên, Giảng viên

    @Column(length = 255)
    private String school; // Trường học

    @Column(length = 255)
    private String major; // Chuyên ngành

    @Column(length = 255)
    private String location; // Sống ở

    @Column(length = 255)
    private String hometown; // Đến từ

    @Column
    private LocalDate birthday; // Ngày sinh

    @Column(length = 20)
    private String contactPhone; // Số điện thoại
    // === KẾT THÚC TRƯỜNG MỚI ===
    @Column(nullable = false)
    private boolean enabled = true; // Mặc định là true (hoạt động)


    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
}