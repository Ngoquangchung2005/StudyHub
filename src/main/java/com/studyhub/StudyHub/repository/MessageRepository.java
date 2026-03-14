package com.studyhub.StudyHub.repository;


import com.studyhub.StudyHub.entity.Message;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    //Tai 50 đến 100 tin nhắn gần nhất
    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.room.id = :roomId")
    List<Message> findByRoomIdWithSender(Long roomId, Sort sort);
}