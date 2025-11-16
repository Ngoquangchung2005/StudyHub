package com.studyhub.StudyHub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    // === THÊM 2 TRƯỜNG MỚI ===
    @Column(length = 255)
    private String avatarUrl; // Sẽ lưu tên file duy nhất (VD: abc-123.jpg)

    @Column(columnDefinition = "TEXT")
    private String bio; // Mô tả tiểu sử
    // === KẾT THÚC TRƯỜNG MỚI ===

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
}