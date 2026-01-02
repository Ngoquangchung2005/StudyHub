package com.studyhub.StudyHub.repository;

import com.studyhub.StudyHub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // === THÊM HÀM TÌM KIẾM NÀY ===
    @Query("SELECT u FROM User u WHERE (LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND u.id != :currentUserId")
    List<User> searchUsers(@Param("keyword") String keyword, @Param("currentUserId") Long currentUserId);
}