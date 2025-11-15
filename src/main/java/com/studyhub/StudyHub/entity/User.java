package com.studyhub.StudyHub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@NoArgsConstructor // Lombok: Tự tạo constructor không tham số
@Entity // Báo cho Spring biết đây là một bảng trong CSDL
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"), // Tên đăng nhập không được trùng
        @UniqueConstraint(columnNames = "email") // Email không được trùng
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String username;
    private String email;
    private String password;

    // Quan hệ Nhiều-Nhiều (Một User có Nhiều Role, một Role có Nhiều User)
    @ManyToMany(fetch = FetchType.EAGER) // EAGER: Tải Role ngay khi tải User
    @JoinTable(
            name = "user_roles", // Tên bảng trung gian
            joinColumns = @JoinColumn(name = "user_id"), // Khóa ngoại trỏ đến User
            inverseJoinColumns = @JoinColumn(name = "role_id") // Khóa ngoại trỏ đến Role
    )
    private Set<Role> roles = new HashSet<>();
}