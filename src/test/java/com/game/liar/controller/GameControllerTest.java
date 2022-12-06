package com.game.liar.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.domain.GameState;
import com.game.liar.domain.Global;
import com.game.liar.domain.request.MessageContainer;
import com.game.liar.domain.response.MessageResponse;
import com.game.liar.service.GameInfo;
import com.game.liar.service.GameService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameControllerTest {
    @LocalServerPort
    private Integer port;
    WebSocketStompClient stompClient;
    StompSession stompSession;
    PrivateStompHandler<MessageContainer> handler;

    @Autowired
    GameController gameController;

    @Autowired
    GameService gameService;

    Map<String, GameInfo> gameManagerMap;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void init() throws ExecutionException, InterruptedException, TimeoutException {
        stompClient = new WebSocketStompClient(new SockJsClient(createTransportClient()));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        stompSession = stompClient.connect("ws://localhost:" + port + "/ws-connection", new StompSessionHandlerAdapter() {
        }).get(3, SECONDS);

        assertThat(stompSession).isNotNull();

//        Field field = gameController.getClass().getDeclaredField("gameManagerMap");
//        field.setAccessible(true);
//        gameManagerMap = (Map<String, GameInfo>) field.get(gameController);
    }

//    @Test
//    public void 방장게임시작() throws Exception {
//        게임시작();
//    }

    private void 게임시작(String roomId) throws JsonProcessingException, InterruptedException, ExecutionException, TimeoutException {
        gameService.addGame(roomId,"roomOwner");
        //given
        handler = new PrivateStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/private/%s",roomId), handler);

        //when
        String uuid = UUID.randomUUID().toString();

        GameInfo.GameSettings settings = GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(1)
                .category(Arrays.asList("food"))
                .build();
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("roomOwner")
                .message(new MessageContainer.Message("startGame", String.format("%s",objectMapper.writeValueAsString(settings))))
                .build();
        System.out.println("sendMessage : "+ sendMessage);
        stompSession.send(String.format("/publish/system/private/%s",roomId), sendMessage);

        //then
        MessageContainer message = handler.getCompletableFuture().get(3, SECONDS);

        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_GAME_STARTED, "{\"state\":\"BEFORE_ROUND\"}"))
                .uuid(uuid)
                .build();

        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectMessage);
    }

    @Test
    public void 방장라운드시작() throws Exception {
        String roomId = "12345";
        게임시작(roomId);
        System.out.println("=================================================================================================");

        String uuid = UUID.randomUUID().toString();

        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("roomOwner")
                .message(new MessageContainer.Message(Global.START_ROUND, "{}"))
                .build();
        System.out.println("sendMessage : "+ sendMessage);
        stompSession.send(String.format("/publish/system/private/%s",roomId), sendMessage);

        //then
        MessageContainer message = handler.getCompletableFuture().get(3, SECONDS);

        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_ROUND_STARTED, "{\"state\":\"SELECT_LIAR\",\"round\":1}"))
                .uuid(uuid)
                .build();

        assertThat(message).isNotNull();
        //assertThat(message).isEqualTo(expectMessage);
    }

    private List<Transport> createTransportClient() {
        List<Transport> transports = new ArrayList<>(1);
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        return transports;
    }

    private static class PrivateStompHandler<T> implements StompFrameHandler {
        private final CompletableFuture<T> completableFuture = new CompletableFuture<>();

        public CompletableFuture<T> getCompletableFuture() {
            return completableFuture;
        }

        private final Class<T> tClass;

        public PrivateStompHandler(Class<T> tClass) {
            this.tClass = tClass;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {

            System.out.println("payload : "+headers);
            return this.tClass;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {

            System.out.println("handleFrame headers: "+headers+", payload: "+payload);
            completableFuture.complete((T) payload);
        }
    }
}