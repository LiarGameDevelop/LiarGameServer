package com.game.liar.chat.controller;

import com.game.liar.chat.domain.ChatMessageDto;
import com.game.liar.chat.service.ChattingService;
import com.game.liar.exception.NotExistException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
public class ChattingController {
    private final ChattingService chattingService;
    private final RabbitTemplate messagingTemplate;

    public ChattingController(ChattingService chattingService, RabbitTemplate messagingTemplate) {
        this.chattingService = chattingService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("messages.{roomId}")
    public void chat(@Payload ChatMessageDto message, @DestinationVariable("roomId") String roomId) {
        try {
            chattingService.save(message, roomId);
        } catch (NotExistException e) {
            log.error("No room in the server");
            return;
        }
        messagingTemplate.convertAndSend("amq.topic", String.format("room.%s.chat", roomId), message);
    }
}
