package com.game.liar.chat.repository;

import com.game.liar.chat.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRepository extends JpaRepository<ChatMessage,Long> {
    List<ChatMessage> findAllByRoomId(String roomId);
    ChatMessage save(ChatMessage message);
}
