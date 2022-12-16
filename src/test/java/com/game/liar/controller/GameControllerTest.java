package com.game.liar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.config.GameCategoryProperties;
import com.game.liar.domain.GameState;
import com.game.liar.domain.Global;
import com.game.liar.domain.request.MessageContainer;
import com.game.liar.domain.request.RoomIdAndUserIdRequest;
import com.game.liar.domain.request.RoomInfoRequest;
import com.game.liar.domain.response.LiarResponse;
import com.game.liar.domain.response.OpenedGameInfo;
import com.game.liar.domain.response.RoomInfoResponseDto;
import com.game.liar.domain.response.TurnResponse;
import com.game.liar.service.GameInfo;
import com.game.liar.service.GameService;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.game.liar.domain.Global.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameControllerTest {
    @LocalServerPort
    private Integer port;
    WebSocketStompClient stompClient;
    StompSession stompSession;
    //TestStompHandler<MessageContainer> roomMessageHandler;
    //TestStompHandler<MessageContainer> privateMessageHandler;
    //TestStompHandler<MessageContainer> publicMessageHandler;

    @Autowired
    GameController gameController;

    @Autowired
    GameService gameService;

    @Autowired
    RoomController roomController;

    ObjectMapper objectMapper = new ObjectMapper();
    String roomId;
    String ownerId;

    List<SessionInfo> sessionInfoList = new ArrayList<>();

    @Autowired
    GameCategoryProperties gameCategoryProperties;

    @BeforeEach
    void init() throws ExecutionException, InterruptedException, TimeoutException {
        stompClient = new WebSocketStompClient(new SockJsClient(createTransportClient()));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        stompSession = stompClient.connect("ws://localhost:" + port + "/ws-connection", new StompSessionHandlerAdapter() {
        }).get(3, SECONDS);

        assertThat(stompSession).isNotNull();
        assertThat(stompSession.isConnected()).isTrue();
        gameController.setTimeout(5000);
    }

    @AfterEach
    public void initFuture() {
        //roomMessageHandler.completableFuture.clear();
    }

    private class SessionInfo {
        StompSession session;
        RoomInfoResponseDto roomInfo;
        String guestId;
        TestStompHandler<MessageContainer> privateStompHandler;
        TestStompHandler<MessageContainer> publicStompHandler;
    }

    private void 게임참가(String name) throws Exception {
        SessionInfo s = new SessionInfo();
        s.session = createStompSession();
        s.roomInfo = roomController.enterRoom(new RoomIdAndUserIdRequest(roomId, name), null);
        s.guestId = s.roomInfo.getUserList().get(s.roomInfo.getUserList().size() - 1).getUserId();

//        s.privateStompHandler = new TestStompHandler<>(MessageContainer.class);
//        s.session.subscribe(String.format("/subscribe/system/private/%s", s.guestId), s.privateStompHandler);
//
//        s.publicStompHandler = new TestStompHandler<>(MessageContainer.class);
//        s.session.subscribe(String.format("/subscribe/system/public/%s", roomId), s.publicStompHandler);

        sessionInfoList.add(s);

        assertThat(s.session).isNotNull();
        assertThat(s.session.isConnected()).isTrue();
    }

    private void 방생성() throws Exception {
        RoomInfoResponseDto roomInfo = roomController.create(new RoomInfoRequest(5, "roomOwner"), null);
        roomId = roomInfo.getRoomId();
        ownerId = roomInfo.getOwnerId();
    }

    @Test
    public void 게임시작() throws Exception {
        __게임시작();
    }

    private void __게임시작() throws Exception {
        //given
        방생성();
        게임참가("user1");

        //when
        String uuid = UUID.randomUUID().toString();

        GameInfo.GameSettings settings = GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports"))
                .build();
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message("startGame", String.format("%s", objectMapper.writeValueAsString(settings))))
                .build();
        System.out.println("sendMessage : " + sendMessage);
        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        stompSession.send(String.format("/publish/system/private/%s", roomId), sendMessage);

        //then
        MessageContainer message = handler1.getCompletableFuture().get(3, SECONDS);

        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_GAME_STARTED, "{\"state\":\"BEFORE_ROUND\"}"))
                .uuid(uuid)
                .build();

        System.out.println(message);
        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectMessage);
    }

    @Test
    public void 라운드시작() throws Exception {
        __라운드시작();
    }

    private void __라운드시작() throws Exception {
        게임시작();
        System.out.println("라운드시작=================================================================================================");

        String uuid = UUID.randomUUID().toString();

        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(Global.START_ROUND, "{}"))
                .build();
        System.out.println("sendMessage : " + sendMessage);
        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/private/%s", roomId), handler1);
        stompSession.send(String.format("/publish/system/private/%s", roomId), sendMessage);
        //then
        MessageContainer message = handler1.getCompletableFuture().get(3, SECONDS);
        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_ROUND_STARTED, "{\"state\":\"SELECT_LIAR\",\"round\":1}"))
                .uuid(uuid)
                .build();

        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectMessage);
    }

    @Test
    public void 라이어선정요청하면_private_라이어여부를_알려준다() throws Exception {
        __라이어선정();
    }

    private void __라이어선정() throws Exception {
        //Given
        __라운드시작();
        System.out.println("라이어선정=================================================================================================");
        //When
        String uuid = UUID.randomUUID().toString();

        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(Global.SELECT_LIAR, "{}"))
                .build();

        System.out.println("sendMessage : " + sendMessage);
        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/private/%s", ownerId), handler1);
        TestSingleStompHandler<MessageContainer> handler2 = new TestSingleStompHandler<>(MessageContainer.class);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/private/%s", sessionInfoList.get(0).guestId), handler2);

        stompSession.send(String.format("/publish/system/private/%s", roomId), sendMessage);
        //then
        MessageContainer messageToOwner = handler1.getCompletableFuture().get(5, SECONDS);
        MessageContainer expectMessageToOwner = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_LIAR_SELECTED, objectMapper.writeValueAsString(new LiarResponse(false, GameState.OPEN_KEYWORD))))
                .uuid(uuid)
                .build();

        MessageContainer messageToUser = handler2.getCompletableFuture().get(5, SECONDS);
        MessageContainer expectMessageToUser = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_LIAR_SELECTED, objectMapper.writeValueAsString(new LiarResponse(true, GameState.OPEN_KEYWORD))))
                .uuid(uuid)
                .build();

        //Then
        assertThat(messageToOwner).isNotNull();
        assertThat(messageToOwner.getSenderId()).isEqualTo(expectMessageToOwner.getSenderId());
        assertThat(messageToOwner.getUuid()).isEqualTo(expectMessageToOwner.getUuid());
        assertThat(messageToOwner.getMessage().getMethod()).isEqualTo(expectMessageToOwner.getMessage().getMethod());
        assertThat(messageToOwner.getMessage().getBody()).containsAnyOf(
                objectMapper.writeValueAsString(new LiarResponse(false, GameState.OPEN_KEYWORD)),
                objectMapper.writeValueAsString(new LiarResponse(true, GameState.OPEN_KEYWORD)));
        assertThat(messageToUser.getMessage().getBody()).containsAnyOf(
                objectMapper.writeValueAsString(new LiarResponse(false, GameState.OPEN_KEYWORD)),
                objectMapper.writeValueAsString(new LiarResponse(true, GameState.OPEN_KEYWORD)));
    }

    @Test
    public void 키워드공개를요청하면_각사람에게키워드를_private전달하고_첫번째턴사람을공개한다() throws Exception {
        __카테고리알림();
    }

    private OpenedGameInfo __카테고리알림() throws Exception {
        //Given
        __라이어선정();
        System.out.println("카테고리알림=================================================================================================");
        //When
        String uuid = UUID.randomUUID().toString();

        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(Global.OPEN_KEYWORD, "{}"))
                .build();

        System.out.println("sendMessage : " + sendMessage);
        TestStompHandler<MessageContainer> handler1 = new TestStompHandler<>(MessageContainer.class);
        TestStompHandler<MessageContainer> handler2 = new TestStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/private/%s", ownerId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/private/%s", sessionInfoList.get(0).guestId), handler2);

        TestSingleStompHandler<MessageContainer> handler3 = new TestSingleStompHandler<>(MessageContainer.class);
        TestSingleStompHandler<MessageContainer> handler4 = new TestSingleStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler3);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler4);
        stompSession.send(String.format("/publish/system/private/%s", roomId), sendMessage);

        Thread.sleep(500);
        MessageContainer messageToOwner = handler1.getCompletableFuture(0).get(3, SECONDS);
        MessageContainer expectMessageToOwner = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_KEYWORD_OPENED, "{}"))
                .uuid(uuid)
                .build();

        MessageContainer messageToUser = handler2.getCompletableFuture(0).get(3, SECONDS);

        //Then
        OpenedGameInfo gameInfoResultFromOwner = objectMapper.readValue(messageToOwner.getMessage().getBody(), OpenedGameInfo.class);
        OpenedGameInfo gameInfoResultFromGuest = objectMapper.readValue(messageToUser.getMessage().getBody(), OpenedGameInfo.class);

        assertThat(messageToOwner).isNotNull();
        assertThat(messageToOwner.getSenderId()).isEqualTo(expectMessageToOwner.getSenderId());
        assertThat(messageToOwner.getUuid()).isEqualTo(expectMessageToOwner.getUuid());
        assertThat(messageToOwner.getMessage().getMethod()).isEqualTo(expectMessageToOwner.getMessage().getMethod());

        assertThat(gameInfoResultFromOwner.getCategory()).containsAnyOf(new ArrayList<>(gameCategoryProperties.getKeywords().keySet()).toArray(new String[0]));
        assertThat(gameInfoResultFromOwner.getCategory()).isEqualTo(gameInfoResultFromGuest.getCategory());

        assertThat(gameInfoResultFromOwner.getTurnOrder()).isEqualTo(gameInfoResultFromGuest.getTurnOrder());

        assertThat(gameInfoResultFromOwner.getKeyword()).satisfiesAnyOf(
                param -> assertThat(gameInfoResultFromOwner.getKeyword()).containsAnyOf(gameCategoryProperties.loadKeywords(gameInfoResultFromOwner.getCategory()).toArray(new String[0])),
                param -> assertThat(gameInfoResultFromOwner.getKeyword()).isEqualTo("LIAR")
        );
        assertThat(gameInfoResultFromOwner.getTurnOrder()).satisfiesAnyOf(
                param -> assertThat(gameInfoResultFromOwner.getTurnOrder()).containsExactly(ownerId, sessionInfoList.get(0).guestId),
                param -> assertThat(gameInfoResultFromOwner.getTurnOrder()).containsExactly(sessionInfoList.get(0).guestId, ownerId)
        );
        System.out.println(LocalDateTime.now() + ":gameInfoResultFromOwner.getTurnOrder(): " + gameInfoResultFromOwner.getTurnOrder());
        System.out.println(LocalDateTime.now() + ":before public message arrived");
        MessageContainer message1 = handler3.getCompletableFuture().get(5, SECONDS);
        MessageContainer message2 = handler4.getCompletableFuture().get(5, SECONDS);

        TurnResponse publicMessageToOwner = objectMapper.readValue(message1.getMessage().getBody(), TurnResponse.class);
        TurnResponse publicMessageToUser = objectMapper.readValue(message2.getMessage().getBody(), TurnResponse.class);
        assertThat(message1.getMessage().getMethod()).isEqualTo(Global.NOTIFY_TURN);
        assertThat(message2.getMessage().getMethod()).isEqualTo(Global.NOTIFY_TURN);
        assertThat(publicMessageToOwner.getTurnId()).isEqualTo(gameInfoResultFromOwner.getTurnOrder().get(0));
        assertThat(publicMessageToUser.getTurnId()).isEqualTo(gameInfoResultFromOwner.getTurnOrder().get(0));

        return gameInfoResultFromOwner;
    }

    @Test
    public void 현재턴의사람이_대답후에_다음턴의사람을_알려준다() throws Exception {
        __턴알림();
    }

    private OpenedGameInfo __턴알림() throws Exception {
        //Given
        OpenedGameInfo gameInfoResultFromOwner = __카테고리알림();
        System.out.println("턴알림=================================================================================================");
        //When
        String uuid = UUID.randomUUID().toString();

        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(gameInfoResultFromOwner.getTurnOrder().get(0))
                .message(new MessageContainer.Message(Global.REQUEST_TURN_FINISH, "{}"))
                .build();
        System.out.println(String.format("턴알림 Send message: %s", sendMessage));
        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        TestSingleStompHandler<MessageContainer> handler2 = new TestSingleStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);

        stompSession.send(String.format("/publish/system/public/%s", roomId), sendMessage);

        //Then
        MessageContainer message1 = handler1.getCompletableFuture().get(5, SECONDS);
        MessageContainer message2 = handler2.getCompletableFuture().get(5, SECONDS);
        TurnResponse publicMessageToOwner = objectMapper.readValue(message1.getMessage().getBody(), TurnResponse.class);
        TurnResponse publicMessageToUser = objectMapper.readValue(message2.getMessage().getBody(), TurnResponse.class);
        assertThat(message1.getMessage().getMethod()).isEqualTo(Global.NOTIFY_TURN);
        assertThat(message2.getMessage().getMethod()).isEqualTo(Global.NOTIFY_TURN);
        assertThat(publicMessageToOwner.getTurnId()).isEqualTo(gameInfoResultFromOwner.getTurnOrder().get(1));
        assertThat(publicMessageToUser.getTurnId()).isEqualTo(gameInfoResultFromOwner.getTurnOrder().get(1));
        return gameInfoResultFromOwner;
    }

    @Test
    public void 턴알림_TimeOut시간을넘기면턴이넘어간다_Error() throws Exception {
        //Given
        __카테고리알림();
        System.out.println("턴알림=================================================================================================");
        //When
        TestStompHandler<MessageContainer> handler1 = new TestStompHandler<>(MessageContainer.class);
        TestStompHandler<MessageContainer> handler2 = new TestStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);
        Thread.sleep(5000);

        //Then
        MessageContainer message1 = handler1.getCompletableFuture(0).get(5, SECONDS);
        MessageContainer message2 = handler2.getCompletableFuture(0).get(5, SECONDS);
        MessageContainer message3 = handler1.getCompletableFuture(1).get(5, SECONDS);
        MessageContainer message4 = handler2.getCompletableFuture(1).get(5, SECONDS);

        //둘중 한명은 라이어인데, 걔는 타임아웃을 받는다.
        assertThat(message1.getMessage().getMethod()).isEqualTo(NOTIFY_TURN_TIMEOUT);
        assertThat(message2.getMessage().getMethod()).isEqualTo(NOTIFY_TURN_TIMEOUT);
        assertThat(message3.getMessage().getMethod()).isEqualTo(NOTIFY_TURN);
        assertThat(message4.getMessage().getMethod()).isEqualTo(NOTIFY_TURN);
    }

    @Test
    public void 라운드종료() throws Exception {
        //Given
        OpenedGameInfo gameInfoResultFromOwner = __턴알림();
        System.out.println("라운드종료=================================================================================================");

        //When
        TestStompHandler<MessageContainer> handler1 = new TestStompHandler<>(MessageContainer.class);
        TestStompHandler<MessageContainer> handler2 = new TestStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);

        System.out.println("turn order:"+gameInfoResultFromOwner.getTurnOrder());
        String uuid = UUID.randomUUID().toString();
        //1번차례임
        requestTurnFinishedAndVerify(gameInfoResultFromOwner, handler1, handler2,
                gameInfoResultFromOwner.getTurnOrder().get(1).equals(ownerId) ? stompSession: sessionInfoList.get(0).session, 0);
        requestTurnFinishedAndVerify(gameInfoResultFromOwner, handler1, handler2,
                gameInfoResultFromOwner.getTurnOrder().get(0).equals(ownerId) ? stompSession: sessionInfoList.get(0).session, 1);

        //Then
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(gameInfoResultFromOwner.getTurnOrder().get(1))
                .message(new MessageContainer.Message(Global.REQUEST_TURN_FINISH, "{}"))
                .build();

        if(gameInfoResultFromOwner.getTurnOrder().get(1).equals(ownerId)){
            stompSession.send(String.format("/publish/system/public/%s", roomId), sendMessage);
        }
        else{
            sessionInfoList.get(0).session.send(String.format("/publish/system/public/%s", roomId), sendMessage);
        }
        Thread.sleep(500);
        MessageContainer message1 = handler1.getCompletableFuture(0).get(5, SECONDS);
        MessageContainer message2 = handler2.getCompletableFuture(0).get(5, SECONDS);

        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId(SERVER_ID)
                .message(new MessageContainer.Message(NOTIFY_ROUND_END, "{\"state\":\"VOTE_LIAR\",\"round\":2}"))
                .build();
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getSenderId()).isEqualTo(SERVER_ID);
        assertThat(message1.getMessage()).isEqualTo(expectMessage.getMessage());
    }

    private void requestTurnFinishedAndVerify(OpenedGameInfo gameInfoResultFromOwner, TestStompHandler<MessageContainer> handler1, TestStompHandler<MessageContainer> handler2, StompSession session, int i) throws InterruptedException, ExecutionException, TimeoutException {
        String uuid = UUID.randomUUID().toString();
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(gameInfoResultFromOwner.getTurnOrder().get(1 - i))
                .message(new MessageContainer.Message(Global.REQUEST_TURN_FINISH, "{}"))
                .build();

        session.send(String.format("/publish/system/public/%s", roomId), sendMessage);
        Thread.sleep(1000);
        MessageContainer message1 = handler1.getCompletableFuture(0).get(5, SECONDS);
        MessageContainer message2 = handler2.getCompletableFuture(0).get(5, SECONDS);

        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(SERVER_ID)
                .message(new MessageContainer.Message(NOTIFY_TURN,
                        String.format("{\"turnId\":\"%s\",\"state\":\"IN_PROGRESS\"}", gameInfoResultFromOwner.getTurnOrder().get(i))))
                .build();
        assertThat(message1).isEqualTo(message2);
        assertThat(message1).isEqualTo(expectMessage);
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

    private static class TestStompHandler<T> implements StompFrameHandler {
        public final Queue<CompletableFuture<T>> completableFuture = new ConcurrentLinkedQueue<>();

        public CompletableFuture<T> getCompletableFuture(int index) {
            System.out.println(LocalDateTime.now() + ":[TestStompHandler] getCompletableFuture index:" + index);
            CompletableFuture<T> ret = completableFuture.peek();
            completableFuture.remove();
            return ret;
        }

        private final Class<T> tClass;

        public TestStompHandler(Class<T> tClass) {
            this.tClass = tClass;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            System.out.println(LocalDateTime.now() + "payload : " + headers);
            return this.tClass;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.complete((T) payload);
            completableFuture.add(future);
            System.out.println(LocalDateTime.now() + ":[TestStompHandler] handleFrame headers: " + headers + ", payload: " + payload + ", completableFuture.size() :" + completableFuture.size());
        }
    }

    private static class TestSingleStompHandler<T> implements StompFrameHandler {
        public final CompletableFuture<T> completableFuture = new CompletableFuture<>();

        public CompletableFuture<T> getCompletableFuture() {
            System.out.println(LocalDateTime.now() + ":[TestSingleStompHandler] getCompletableFuture");
            return completableFuture;
        }

        private final Class<T> tClass;

        public TestSingleStompHandler(Class<T> tClass) {
            this.tClass = tClass;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            System.out.println("payload : " + headers);
            return this.tClass;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            System.out.println("[TestSingleStompHandler] handleFrame headers: " + headers + ", payload: " + payload);
            completableFuture.complete((T) payload);
        }
    }
}

