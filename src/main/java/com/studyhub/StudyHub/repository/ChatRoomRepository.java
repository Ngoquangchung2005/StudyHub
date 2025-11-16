package com.studyhub.StudyHub.repository;

import com.studyhub.StudyHub.entity.ChatRoom;
import com.studyhub.StudyHub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // Tìm tất cả các phòng mà 1 user tham gia
    List<ChatRoom> findByMembersContains(User user);

    // Tìm phòng 1-1 giữa 2 user
    @Query("SELECT cr FROM ChatRoom cr WHERE cr.type = 'ONE_TO_ONE' AND " +
            "SIZE(cr.members) = 2 AND " +
            ":user1 MEMBER OF cr.members AND " +
            ":user2 MEMBER OF cr.members")
    Optional<ChatRoom> findOneToOneRoom(User user1, User user2);
}