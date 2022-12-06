package com.game.liar.service;

import com.game.liar.domain.request.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ChattingService {
    private List<ChatMessage> roomList = new ArrayList<>();

    public void save(ChatMessage request) {
        log.info("request : {}", request);
    }
}
