package com.game.liar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.chat.domain.ChatMessageDto;
import com.game.liar.room.dto.EnterRoomResponse;
import com.game.liar.room.dto.RoomInfoRequest;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class Util {
    public static class TestStompObject {
        public WebSocketStompClient getStompClient() {
            return stompClient;
        }

        public StompSession getStompSession() {
            return stompSession;
        }

        public EnterRoomResponse getRoomInfo() {
            return roomInfo;
        }

        WebSocketStompClient stompClient;
        StompSession stompSession;
        EnterRoomResponse roomInfo;

        public TestStompObject(WebSocketStompClient stompClient, StompSession stompSession, EnterRoomResponse roomInfo) {
            this.stompClient = stompClient;
            this.stompSession = stompSession;
            this.roomInfo = roomInfo;
        }

        public void subscribe(PrivateStompHandler<ChatMessageDto> handler, String destination) {
            StompHeaders headers = new StompHeaders();
            headers.setDestination(destination);
            headers.add("Authorization", "Bearer " + this.getRoomInfo().getToken().getAccessToken());
            stompSession.subscribe(headers, handler);
        }
    }

    public static TestStompObject createStompObj(MockMvc mockMvc, Integer port) throws Exception {
        EnterRoomResponse roomInfo = createRoom(mockMvc);

        WebSocketStompClient stompClient = new WebSocketStompClient(new SockJsClient(createTransportClient()));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.add("Authorization", "Bearer " + roomInfo.getToken().getAccessToken());
        StompSession stompSession = stompClient.connect("ws://localhost:" + port + "/ws-connection", headers, stompHeaders, new StompSessionHandlerAdapter() {
        }).get(5, SECONDS);

        TestStompObject obj = new TestStompObject(stompClient, stompSession, roomInfo);

        return obj;
    }

    public static List<Transport> createTransportClient() {
        List<Transport> transports = new ArrayList<>(1);
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        return transports;
    }

    public static class PrivateStompHandler<T> implements StompFrameHandler {
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

    public static EnterRoomResponse createRoom(MockMvc mockMvc) throws Exception {
        ObjectMapper om = new ObjectMapper();
        RoomInfoRequest request = new RoomInfoRequest(5, "tester", "password");
        String result = mockMvc.perform(
                        post("/room/create")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("result =" + result);
        return om.readValue(result, EnterRoomResponse.class);
    }
}
