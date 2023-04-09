package com.game.liar.chat.service;

import com.game.liar.chat.domain.ChatMessageDto;
import com.game.liar.chat.repository.ChatRepository;
import com.game.liar.exception.NotExistException;
import com.game.liar.room.dto.RoomIdRequest;
import com.game.liar.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class ChattingService {
    private final ChatRepository chatRepository;
    private final RoomService roomService;

    public void save(ChatMessageDto message, String roomId) throws NotExistException {
        roomService.getRoom(new RoomIdRequest(roomId));
        chatRepository.save(message.toEntity(roomId));
        log.debug("message : {} from roomId: {}", message, roomId);
    }
}
