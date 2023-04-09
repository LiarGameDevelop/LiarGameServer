package com.game.liar.chat.repository;

import com.game.liar.chat.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRepository extends JpaRepository<ChatMessage,Long> {
}
