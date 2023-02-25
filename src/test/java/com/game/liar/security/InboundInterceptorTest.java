package com.game.liar.security;

import com.game.liar.security.domain.TokenProvider;
import com.game.liar.websocket.InboundInterceptor;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@WithMockUser
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Disabled
class InboundInterceptorTest {

    @LocalServerPort
    private Integer port;
    WebSocketStompClient stompClient;
    StompSession stompSession;

    @Mock
    TokenProvider tokenProvider;
    @InjectMocks
    InboundInterceptor inboundInterceptor;


    @BeforeEach
    void setUp(){

    }

    @AfterEach
    void tearDown() {
    }

    //@Test
    @DisplayName("유저 접속시 인증된 jwt를 확인한다")
    void preSend() throws ExecutionException, InterruptedException, TimeoutException {
        GenericMessage<?> message = new GenericMessage<>("asdf");
        //inboundInterceptor.postSend(message);

        String roomId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        doNothing().when(tokenProvider).validateTokenAndSetAuth(any());
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

}