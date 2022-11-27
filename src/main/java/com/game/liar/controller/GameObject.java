package com.game.liar.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.dto.GameState;
import com.game.liar.dto.request.MessageRequest;
import com.game.liar.dto.response.MessageResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class GameObject {
    private static final String SERVER_IP = "13.125.250.0";
    @Getter
    private GameState status;
    private WebSocketStompClient socketClient;
    private StompSession serverSession;
    @Getter
    private String roomName;

    private ObjectMapper objectMapper = new ObjectMapper();

    void initialize(String roomName) throws ExecutionException, InterruptedException, TimeoutException {
        createSession();
        this.roomName = roomName;
        status = GameState.BEFORE_START;
    }

    private void createSession() throws InterruptedException, ExecutionException, TimeoutException {
        socketClient = new WebSocketStompClient(new SockJsClient(
                Collections.singletonList(new WebSocketTransport(new StandardWebSocketClient()))));
        serverSession = socketClient
                .connect(String.format("ws://%s:8080/ws-connection",SERVER_IP), new StompSessionHandlerAdapter() {
                }).get(1, TimeUnit.SECONDS);
        log.debug("game manager created");
    }

    void sendMessage(MessageResponse message){
        serverSession.send(String.format("/publish/system/room/%s",roomName), message.getMessage());
    }

    void sendMessage(MessageResponse.MessageDetail detail){
        MessageResponse request = new MessageResponse();
        request.setSenderId(roomName);
        request.setMessage(detail);
        request.setStatus(status);

        try {
            String response = objectMapper.writeValueAsString(request);
            serverSession.send(String.format("/publish/system/room/%s",roomName), response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    GameState changeStatus(){
        return status.next();
    }
}
