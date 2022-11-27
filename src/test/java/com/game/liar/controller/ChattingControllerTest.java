package com.game.liar.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChattingControllerTest {
    private Integer port=8080;
    private WebSocketStompClient webSocketStompClient;

    @BeforeEach
    void setup() {
        this.webSocketStompClient = new WebSocketStompClient(new SockJsClient(
                Arrays.asList(new WebSocketTransport(new StandardWebSocketClient()))));
    }

    @Test
    public void 연결테스트 () throws Exception{
        //Given
        /*TODO: implement unittest for websocket*/
        StompSession session = webSocketStompClient
                .connect(String.format("ws://localhost:%d/ws-connection", port), new StompSessionHandlerAdapter() {
                })
                .get(1, SECONDS);

        assertThat(session).isNotNull();
        BlockingQueue<String> blockingQueue = new ArrayBlockingQueue<>(1);

        session.subscribe("/subscribe/room/1", new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                System.out.println((String)payload);
                blockingQueue.add((String) payload);
            }
        });
        //When

        session.send("/publish/room/1", "Hello");

        //Then
        await().atMost(1, SECONDS).untilAsserted(() -> assertEquals("Hello", blockingQueue.poll()));

    }
}