package com.game.liar.service;

import com.game.liar.dto.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ChattingService {
    private List<ChatRequest> roomList = new ArrayList<ChatRequest>();

    public void save(ChatRequest request) {
        log.info("request : {}", request);
    }
}
