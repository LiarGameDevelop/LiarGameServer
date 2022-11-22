package com.game.liar.controller;

import com.game.liar.dto.request.ChatRequest;
import com.game.liar.service.ChattingService;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import javax.validation.Valid;

@Controller
public class ChattingController {
    private final ChattingService chattingService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public ChattingController(ChattingService chattingService, SimpMessagingTemplate simpMessagingTemplate) {
        this.chattingService = chattingService;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @MessageMapping("/messages")
    public void chat(@Valid ChatRequest request) {
        chattingService.save(request);
        simpMessagingTemplate.convertAndSend("/subscribe/room/" + request.getRoomId(), request.getMessage());
    }
}
