package com.game.liar.controller;

import com.game.liar.domain.request.ChatMessage;
import com.game.liar.domain.request.MessageContainer;
import com.game.liar.domain.response.MessageResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChattingControllerTest {
    @LocalServerPort
    private Integer port;
    WebSocketStompClient stompClient;
    StompSession stompSession;

    @BeforeEach
    void init() throws ExecutionException, InterruptedException, TimeoutException {
        stompClient = new WebSocketStompClient(new SockJsClient(createTransportClient()));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        stompSession = stompClient.connect("ws://localhost:" + port + "/ws-connection", new StompSessionHandlerAdapter() {
        }).get(3, SECONDS);

        assertThat(stompSession).isNotNull();
    }

    @Test
    public void 채팅서비스() throws Exception {
        //given
        PrivateStompHandler<ChatMessage> handler = new PrivateStompHandler<>(ChatMessage.class);
        stompSession.subscribe("/subscribe/room/12345/chat", handler);

        //when
        ChatMessage expectedMessage = new ChatMessage("abc", "hello");
        stompSession.send("/publish/messages/12345", expectedMessage);

        //then
        ChatMessage message = handler.getCompletableFuture().get(3, SECONDS);

        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectedMessage);
    }

    @Test
    public void 채팅서비스_여러개_서로_간섭하지않아야한다() throws Exception {
        stompSession =createStompSession();
        StompSession stompSession1=createStompSession();
        //given
        PrivateStompHandler<ChatMessage> handler = new PrivateStompHandler<>(ChatMessage.class);
        stompSession.subscribe("/subscribe/room/12345/chat", handler);
        ChatMessage expectedMessage = new ChatMessage("abc", "hello");
        stompSession.send("/publish/messages/12345", expectedMessage);

        //when
        PrivateStompHandler<ChatMessage> handler2 = new PrivateStompHandler<>(ChatMessage.class);
        stompSession.subscribe("/subscribe/room/12346/chat", handler);

        //then
        ChatMessage message = handler.getCompletableFuture().get(3, SECONDS);
        assertThat(stompSession).isNotNull();
        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectedMessage);

        assertThrows(TimeoutException.class,()->handler2.getCompletableFuture().get(3, SECONDS));
    }

    private StompSession createStompSession() throws ExecutionException, InterruptedException, TimeoutException {
        WebSocketStompClient client = new WebSocketStompClient(new SockJsClient(createTransportClient()));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompSession stompSession = client.connect("ws://localhost:" + port + "/ws-connection", new StompSessionHandlerAdapter() {
        }).get(3, SECONDS);
        return stompSession;
    }

    private List<Transport> createTransportClient() {
        List<Transport> transports = new ArrayList<>(1);
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        return transports;
    }

    private class PrivateStompHandler<T> implements StompFrameHandler {
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
            return this.tClass;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            completableFuture.complete((T) payload);
        }
    }
}