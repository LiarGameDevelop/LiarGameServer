package com.game.liar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.room.dto.EnterRoomResponse;
import com.game.liar.room.dto.RoomIdUserNameRequest;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

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

        public void subscribe(PrivateStompHandler<?> handler, String destination) {
            StompHeaders headers = new StompHeaders();
            headers.setDestination(destination);
            headers.add("Authorization", "Bearer " + this.getRoomInfo().getToken().getAccessToken());
            stompSession.subscribe(headers, handler);
        }

        public void subscribe(ChainStompHandler<?> handler, String destination) {
            StompHeaders headers = new StompHeaders();
            headers.setDestination(destination);
            headers.add("Authorization", "Bearer " + this.getRoomInfo().getToken().getAccessToken());
            stompSession.subscribe(headers, handler);
        }
    }

    public static TestStompObject createRoomAndStompObj(MockMvc mockMvc, Integer port) throws Exception {
        EnterRoomResponse roomInfo = createRoom(mockMvc);

        WebSocketStompClient stompClient = new WebSocketStompClient(new SockJsClient(createTransportClient()));
        //WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.add("Authorization", "Bearer " + roomInfo.getToken().getAccessToken());
        StompSession stompSession = stompClient.connect("ws://localhost:" + port + "/ws-connection", headers, stompHeaders, new StompSessionHandlerAdapter() {
        }).get(5, SECONDS);

        TestStompObject obj = new TestStompObject(stompClient, stompSession, roomInfo);

        return obj;
    }

    public static TestStompObject createStompObjAndEnterRoom(MockMvc mockMvc, Integer port, String roomId) throws Exception {
        EnterRoomResponse roomInfo = enterRoom(mockMvc, roomId);

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

    public static class ChainStompHandler<T> implements StompFrameHandler {
        public final LinkedBlockingQueue<CompletableFuture<T>> completableFuture = new LinkedBlockingQueue<>();

        private CompletableFuture<T> __getCompletableFuture() throws InterruptedException {
            //System.out.println(LocalDateTime.now() + ":[TestStompHandlerChain] getCompletableFuture size:" + completableFuture.size());
            return completableFuture.poll(10, SECONDS);
        }

        public T getCompletableFuture(int index) throws ExecutionException, InterruptedException, TimeoutException {
            CompletableFuture<T> future = __getCompletableFuture();
            if (future != null) {
                T ret = future.get(5, SECONDS);
                return ret;
            } else throw new RuntimeException();
        }

        private final Class<T> tClass;

        public ChainStompHandler(Class<T> tClass) {
            this.tClass = tClass;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            //System.out.println(LocalDateTime.now() + "payload : " + headers);
            return this.tClass;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            CompletableFuture<T> future = new CompletableFuture<>();
            future.complete((T) payload);
            try {
                completableFuture.put(future);
            } catch (InterruptedException e) {
                System.out.println("데이터 저장에 실패했쌈 : " + headers.getDestination());
                throw new RuntimeException(e);
            }

            System.out.println(LocalDateTime.now() + ":[TestStompHandlerChain] handleFrame headers: " + headers + ", payload: " + payload + ", completableFuture.size() :" + completableFuture.size());
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

    public static EnterRoomResponse enterRoom(MockMvc mockMvc, String roomId) throws Exception {
        ObjectMapper om = new ObjectMapper();
        RoomIdUserNameRequest request = new RoomIdUserNameRequest(roomId, "guest", "password");
        String result = mockMvc.perform(
                        post("/room/enter")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("result =" + result);
        return om.readValue(result, EnterRoomResponse.class);
    }
}
