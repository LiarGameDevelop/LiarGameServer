package com.game.liar.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.domain.GameState;
import com.game.liar.domain.request.MessageRequest;
import com.game.liar.domain.response.MessageResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
public class GameService {
    @Getter
    private GameState status = GameState.INITIAL;
    @Getter
    private String roomName;
    @Getter
    private String ownerId;

    public void initialize(String roomName, String ownerId) {
        this.roomName = roomName;
        this.ownerId = ownerId;
        status = GameState.BEFORE_START;
    }

    public GameState changeStatus() {
        return status.next();
    }
}
