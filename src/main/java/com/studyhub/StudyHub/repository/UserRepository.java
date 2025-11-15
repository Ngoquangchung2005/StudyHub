package com.studyhub.StudyHub.repository;


import com.studyhub.StudyHub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Tự động tạo câu lệnh "SELECT * FROM users WHERE email = ?"
    Optional<User> findByEmail(String email);

    // Tự động tạo câu lệnh "SELECT * FROM users WHERE username = ?"
    Optional<User> findByUsername(String username);

    // Tự động tạo câu lệnh "SELECT * FROM users WHERE username = ? OR email = ?"
    Optional<User> findByUsernameOrEmail(String username, String email);
}