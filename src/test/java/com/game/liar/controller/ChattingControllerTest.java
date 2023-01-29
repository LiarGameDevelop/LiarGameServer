package com.game.liar.controller;

import com.game.liar.game.domain.Global;
import com.game.liar.chat.domain.ChatMessageDto;
import com.game.liar.chat.repository.ChatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
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

    @Autowired
    ChatRepository chatRepository;

    @BeforeEach
    void init() throws ExecutionException, InterruptedException, TimeoutException {
        stompClient = new WebSocketStompClient(new SockJsClient(createTransportClient()));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompSession = stompClient.connect("ws://localhost:" + port + "/ws-connection", new StompSessionHandlerAdapter() {
        }).get(3, SECONDS);

        assertThat(stompSession).isNotNull();
    }

    @AfterEach
    void clear(){
        //chatRepository.deleteAll();
        //chatRepository.flush();
    }

    //@Test
    public void 채팅채널에_메세지를보내면_메세지를받고_db에저장된다() throws Exception {
        //given
        PrivateStompHandler<ChatMessageDto> handler = new PrivateStompHandler<>(ChatMessageDto.class);
        stompSession.subscribe("/subscribe/room/12345/chat", handler);

        //when
        ChatMessageDto expectedMessage = new ChatMessageDto("abc", "hello", Global.MessageType.MESSAGE);
        stompSession.send("/publish/messages/12345", expectedMessage);

        //then
        ChatMessageDto message = handler.getCompletableFuture().get(3, SECONDS);
        //chatRepository.flush();

        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectedMessage);
        //System.out.println(chatRepository.findAll());
        //assertThat(chatRepository.findAll().size()).isEqualTo(1);
    }

    //@Test
    public void 채팅서비스_여러개_서로_간섭하지않아야한다() throws Exception {
        stompSession = createStompSession();
        StompSession stompSession1 = createStompSession();
        //given
        PrivateStompHandler<ChatMessageDto> handler = new PrivateStompHandler<>(ChatMessageDto.class);
        stompSession.subscribe("/subscribe/room/12345/chat", handler);
        ChatMessageDto expectedMessage = new ChatMessageDto("abc", "hello", Global.MessageType.MESSAGE);
        stompSession.send("/publish/messages/12345", expectedMessage);

        //when
        PrivateStompHandler<ChatMessageDto> handler2 = new PrivateStompHandler<>(ChatMessageDto.class);
        stompSession1.subscribe("/subscribe/room/12346/chat", handler2);

        //then
        ChatMessageDto message = handler.getCompletableFuture().get(3, SECONDS);
        //chatRepository.flush();
        assertThat(stompSession).isNotNull();
        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectedMessage);

        assertThrows(TimeoutException.class, () -> handler2.getCompletableFuture().get(3, SECONDS));
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