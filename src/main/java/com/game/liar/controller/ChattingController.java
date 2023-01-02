package com.game.liar.controller;

import com.game.liar.dto.ChatMessageDto;
import com.game.liar.service.ChattingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
public class ChattingController {
    private final ChattingService chattingService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public ChattingController(ChattingService chattingService, SimpMessagingTemplate simpMessagingTemplate) {
        this.chattingService = chattingService;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @MessageMapping("/messages/{roomId}")
    public void chat(@Payload ChatMessageDto message, @DestinationVariable("roomId") String roomId) {
        chattingService.save(message,roomId);
        simpMessagingTemplate.convertAndSend(String.format("/subscribe/room/%s/chat", roomId), message);
    }
}
