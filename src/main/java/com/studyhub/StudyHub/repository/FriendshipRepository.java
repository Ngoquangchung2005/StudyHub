package com.studyhub.StudyHub.repository;

import com.studyhub.StudyHub.entity.Friendship;
import com.studyhub.StudyHub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    // Kiểm tra xem đã có quan hệ chưa (chiều xuôi hoặc ngược)
    @Query("SELECT f FROM Friendship f WHERE (f.requester = :u1 AND f.addressee = :u2) OR (f.requester = :u2 AND f.addressee = :u1)")
    Optional<Friendship> findRelationship(@Param("u1") User u1, @Param("u2") User u2);

    // Lấy danh sách bạn bè (status = ACCEPTED)
    @Query("SELECT f FROM Friendship f WHERE (f.requester.id = :userId OR f.addressee.id = :userId) AND f.status = 'ACCEPTED'")
    List<Friendship> findAllFriends(@Param("userId") Long userId);

    // Lấy danh sách lời mời đã nhận (status = PENDING)
    List<Friendship> findByAddresseeAndStatus(User addressee, Friendship.FriendshipStatus status);
}