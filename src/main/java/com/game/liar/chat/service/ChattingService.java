package com.game.liar.chat.service;

import com.game.liar.chat.repository.ChatRepository;
import com.game.liar.chat.domain.ChatMessage;
import com.game.liar.chat.domain.ChatMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class ChattingService {
    private final ChatRepository chatRepository;

    public void save(ChatMessageDto message, String roomId) {
        chatRepository.save(message.toEntity(roomId));
        log.info("message : {}", message);
    }
}
