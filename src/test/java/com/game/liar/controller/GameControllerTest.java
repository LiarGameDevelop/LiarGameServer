package com.game.liar.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.config.GameCategoryProperties;
import com.game.liar.domain.GameState;
import com.game.liar.domain.Global;
import com.game.liar.domain.request.KeywordRequest;
import com.game.liar.domain.request.MessageContainer;
import com.game.liar.domain.request.RoomIdAndUserIdRequest;
import com.game.liar.domain.request.RoomInfoRequest;
import com.game.liar.domain.response.*;
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
import java.util.stream.Collectors;

import static com.game.liar.domain.Global.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameControllerTest {
    @LocalServerPort
    private Integer port;
    @Autowired
    GameController gameController;
    @Autowired
    GameService gameService;
    @Autowired
    RoomController roomController;
    @Autowired
    GameCategoryProperties gameCategoryProperties;

    WebSocketStompClient stompClient;
    StompSession stompSession;

    ObjectMapper objectMapper = new ObjectMapper();
    String roomId;
    String ownerId;

    List<SessionInfo> sessionInfoList = new ArrayList<>();

    @BeforeEach
    void init() {
        stompClient = new WebSocketStompClient(new SockJsClient(createTransportClient()));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        try {
            stompSession = stompClient.connect("ws://localhost:" + port + "/ws-connection", new StompSessionHandlerAdapter() {
            }).get(3, SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        assertThat(stompSession).isNotNull();
        assertThat(stompSession.isConnected()).isTrue();
        gameController.setTimeout(5000);

        try {
            방생성("roomOwner");
            게임참가("user1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class SessionInfo {
        StompSession session;
        RoomInfoResponseDto roomInfo;
        String guestId;
        TestStompHandlerChain<MessageContainer> privateStompHandler;
        TestStompHandlerChain<MessageContainer> publicStompHandler;
    }

    private void 게임참가(String name) throws Exception {
        SessionInfo s = new SessionInfo();
        s.session = createStompSession();
        s.roomInfo = roomController.enterRoom(new RoomIdAndUserIdRequest(roomId, name), null);
        s.guestId = s.roomInfo.getUserList().get(s.roomInfo.getUserList().size() - 1).getUserId();

        sessionInfoList.add(s);

        assertThat(s.session).isNotNull();
        assertThat(s.session.isConnected()).isTrue();
    }

    private void 방생성(String roomOwner) throws Exception {
        RoomInfoResponseDto roomInfo = roomController.create(new RoomInfoRequest(5, roomOwner), null);
        roomId = roomInfo.getRoomId();
        ownerId = roomInfo.getOwnerId();
    }

    //@Test
    public void 게임시작() throws Exception {
        __게임시작();
    }

    private void __게임시작() throws Exception {
        //given

        //when
        String uuid = UUID.randomUUID().toString();

        GameInfo.GameSettings settings = GameInfo.GameSettings.builder()
                .round(2)
                .turn(2)
                .category(Arrays.asList("food", "sports"))
                .build();
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(START_GAME, objectMapper.writeValueAsString(settings)))
                .build();
        System.out.println("sendMessage : " + sendMessage);
        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        StompSession.Subscription sub = stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
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
        sub.unsubscribe();
    }

    //@Test
    public void 라운드시작() throws Exception {
        __라운드시작();
    }

    private void __라운드시작() throws Exception {
        //when
        __게임시작();
        System.out.println("라운드시작=================================================================================================");
        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);

        String uuid = UUID.randomUUID().toString();
        __sendStartRound(uuid);

        //then
        MessageContainer message = handler1.getCompletableFuture(0);
        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_ROUND_STARTED, "{\"state\":\"SELECT_LIAR\",\"round\":1}"))
                .uuid(uuid)
                .build();

        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectMessage);
    }

    private void __sendStartRound(String uuid) {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(Global.START_ROUND, "{}"))
                .build();
        stompSession.send(String.format("/publish/system/private/%s", roomId), sendMessage);
        System.out.println("sendMessage : " + sendMessage);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    //@Test
    public void 라이어선정요청하면_private_라이어여부를_알려준다() throws Exception {
        __라이어선정();
    }

    private void __라이어선정() throws Exception {
        //Given
        __라운드시작();
        System.out.println("라이어선정=================================================================================================");
        //When
        String uuid = UUID.randomUUID().toString();
        __sendSelectLiar(uuid);
        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        StompSession.Subscription sub1 = stompSession.subscribe(String.format("/subscribe/system/private/%s", ownerId), handler1);
        TestSingleStompHandler<MessageContainer> handler2 = new TestSingleStompHandler<>(MessageContainer.class);
        StompSession.Subscription sub2 = sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/private/%s", sessionInfoList.get(0).guestId), handler2);

        //then
        Thread.sleep(1000);
        MessageContainer messageToOwner = handler1.getCompletableFuture().get(3, SECONDS);
        MessageContainer expectMessageToOwner = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_LIAR_SELECTED, objectMapper.writeValueAsString(new LiarResponse(false, GameState.OPEN_KEYWORD))))
                .uuid(uuid)
                .build();

        MessageContainer messageToUser = handler2.getCompletableFuture().get(3, SECONDS);
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
        assertThat(messageToUser.getSenderId()).isEqualTo(expectMessageToUser.getSenderId());
        assertThat(messageToUser.getUuid()).isEqualTo(expectMessageToUser.getUuid());
        assertThat(messageToUser.getMessage().getMethod()).isEqualTo(expectMessageToUser.getMessage().getMethod());
        if (gameService.getGame(roomId).getLiarId().equals(ownerId)) {
            assertThat(messageToOwner.getMessage().getBody()).contains(objectMapper.writeValueAsString(new LiarResponse(true, GameState.OPEN_KEYWORD)));
            assertThat(messageToUser.getMessage().getBody()).contains(objectMapper.writeValueAsString(new LiarResponse(false, GameState.OPEN_KEYWORD)));
        } else {
            assertThat(messageToOwner.getMessage().getBody()).contains(objectMapper.writeValueAsString(new LiarResponse(false, GameState.OPEN_KEYWORD)));
            assertThat(messageToUser.getMessage().getBody()).contains(objectMapper.writeValueAsString(new LiarResponse(true, GameState.OPEN_KEYWORD)));
        }

        sub1.unsubscribe();
        sub2.unsubscribe();
    }

    private void __sendSelectLiar(String uuid) {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(Global.SELECT_LIAR, "{}"))
                .build();
        stompSession.send(String.format("/publish/system/private/%s", roomId), sendMessage);

    }

    //@Test
    public void 키워드공개를요청하면_각사람에게키워드를_private전달하고_첫번째턴사람을공개한다() throws Exception {
        __카테고리알림();
    }

    private OpenedGameInfo __카테고리알림() throws Exception {
        //Given
        __라이어선정();
        System.out.println("카테고리알림=================================================================================================");
        //When
        String uuid = UUID.randomUUID().toString();
        __SendOpenKeyword(uuid);

        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        TestStompHandlerChain<MessageContainer> handler2 = new TestStompHandlerChain<>(MessageContainer.class);
        StompSession.Subscription sub1 = stompSession.subscribe(String.format("/subscribe/system/private/%s", ownerId), handler1);
        StompSession.Subscription sub2 = sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/private/%s", sessionInfoList.get(0).guestId), handler2);

        TestSingleStompHandler<MessageContainer> handler3 = new TestSingleStompHandler<>(MessageContainer.class);
        TestSingleStompHandler<MessageContainer> handler4 = new TestSingleStompHandler<>(MessageContainer.class);
        StompSession.Subscription sub3 = stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler3);
        StompSession.Subscription sub4 = sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler4);

        Thread.sleep(1500);
        MessageContainer messageToOwner = handler1.getCompletableFuture(0);
        MessageContainer expectMessageToOwner = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_KEYWORD_OPENED, "{}"))
                .uuid(uuid)
                .build();
        MessageContainer messageToUser = handler2.getCompletableFuture(0);

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

        sub1.unsubscribe();
        sub2.unsubscribe();
        sub3.unsubscribe();
        sub4.unsubscribe();
        return gameInfoResultFromOwner;
    }

    private void __SendOpenKeyword(String uuid) {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(Global.OPEN_KEYWORD, "{}"))
                .build();
        stompSession.send(String.format("/publish/system/private/%s", roomId), sendMessage);
    }

    //@Test
    public void 현재턴의사람이_대답후에_다음턴의사람을_알려준다() throws Exception {
        __턴알림();
    }

    private void __턴알림() throws Exception {
        //Given
        OpenedGameInfo gameInfoResultFromOwner = __카테고리알림();
        System.out.println("턴알림=================================================================================================");
        //When
        String uuid = UUID.randomUUID().toString();

        __sendRequestTurnFinished(gameService.getGame(roomId).getTurnOrder().get(0), uuid);

        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        TestSingleStompHandler<MessageContainer> handler2 = new TestSingleStompHandler<>(MessageContainer.class);
        StompSession.Subscription sub1 = stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        StompSession.Subscription sub2 = sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);

        Thread.sleep(1000);
        //Then
        MessageContainer message1 = handler1.getCompletableFuture().get(5, SECONDS);
        MessageContainer message2 = handler2.getCompletableFuture().get(5, SECONDS);
        TurnResponse publicMessageToOwner = objectMapper.readValue(message1.getMessage().getBody(), TurnResponse.class);
        TurnResponse publicMessageToUser = objectMapper.readValue(message2.getMessage().getBody(), TurnResponse.class);
        assertThat(message1.getMessage().getMethod()).isEqualTo(Global.NOTIFY_TURN);
        assertThat(message2.getMessage().getMethod()).isEqualTo(Global.NOTIFY_TURN);
        assertThat(publicMessageToOwner.getTurnId()).isEqualTo(gameInfoResultFromOwner.getTurnOrder().get(1));
        assertThat(publicMessageToUser.getTurnId()).isEqualTo(gameInfoResultFromOwner.getTurnOrder().get(1));

        sub1.unsubscribe();
        sub2.unsubscribe();
    }

    @Test
    public void 턴알림_TimeOut시간을넘기면_턴이넘어간다() throws Exception {
        //Given
        __카테고리알림();
        System.out.println("턴알림=================================================================================================");
        //When
        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        TestStompHandlerChain<MessageContainer> handler2 = new TestStompHandlerChain<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);
        Thread.sleep(5000);

        //Then
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler2.getCompletableFuture(0);
        MessageContainer message3 = handler1.getCompletableFuture(1);
        MessageContainer message4 = handler2.getCompletableFuture(1);

        //둘중 한명은 라이어인데, 걔는 타임아웃을 받는다.
        assertThat(message1.getMessage().getMethod()).isEqualTo(NOTIFY_TURN_TIMEOUT);
        assertThat(message2.getMessage().getMethod()).isEqualTo(NOTIFY_TURN_TIMEOUT);
        assertThat(message3.getMessage().getMethod()).isEqualTo(NOTIFY_TURN);
        assertThat(message4.getMessage().getMethod()).isEqualTo(NOTIFY_TURN);
    }

    //@Test
    public void 모두의턴이끝나면_설명종료를알린다() throws Exception {
        __설명종료();
    }

    private void __설명종료() throws Exception {
        //Given
        __턴알림();
        System.out.println("라운드종료=================================================================================================");

        //When
        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        TestStompHandlerChain<MessageContainer> handler2 = new TestStompHandlerChain<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);

        GameInfo gameInfo = gameService.getGame(roomId);
        System.out.println("turn order:" + gameInfo.getTurnOrder());
        String uuid = UUID.randomUUID().toString();
        //1번차례임
        requestTurnFinishedAndVerify(gameInfo, handler1, handler2,
                gameInfo.getTurnOrder().get(1).equals(ownerId) ? stompSession : sessionInfoList.get(0).session, 0);
        requestTurnFinishedAndVerify(gameInfo, handler1, handler2,
                gameInfo.getTurnOrder().get(0).equals(ownerId) ? stompSession : sessionInfoList.get(0).session, 1);

        //Then
        __sendRequestTurnFinished(gameInfo.getTurnOrder().get(1), uuid);
        Thread.sleep(500);
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler2.getCompletableFuture(0);

        MessageContainer expectMessage = Util.getExpectedMessageContainer(NOTIFY_FINDING_LIAR_END, new RoundInfoResponse(GameState.VOTE_LIAR, 1));
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getSenderId()).isEqualTo(SERVER_ID);
        assertThat(message1.getMessage()).isEqualTo(expectMessage.getMessage());
    }

    private void __sendRequestTurnFinished(String senderId, String uuid) {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(senderId)
                .message(new MessageContainer.Message(Global.REQUEST_TURN_FINISH, "{}"))
                .build();

        if (senderId.equals(ownerId)) {
            stompSession.send(String.format("/publish/system/public/%s", roomId), sendMessage);
        } else {
            sessionInfoList.get(0).session.send(String.format("/publish/system/public/%s", roomId), sendMessage);
        }
    }

    //@Test
    public void 모두가_투표를하면_투표결과를알려준다() throws Exception {
        __모두가_투표를하면_투표결과를알려준다();
    }

    private void __모두가_투표를하면_투표결과를알려준다() throws Exception {
        //Given
        __설명종료();
        System.out.println("투표=================================================================================================");

        //When
        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        TestStompHandlerChain<MessageContainer> handler2 = new TestStompHandlerChain<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);

        String uuid = UUID.randomUUID().toString();
        String guestId = sessionInfoList.get(0).guestId;
        String liarDesignatedId = sessionInfoList.get(0).guestId;
        __sendVoteLiar(uuid, ownerId, liarDesignatedId);
        __sendVoteLiar(uuid, guestId, liarDesignatedId);
        Thread.sleep(500);
        //Then
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler2.getCompletableFuture(0);

        VoteResult expectedVoteResult = VoteResult.builder()
                .voteResult(new HashMap<String, String>() {
                    {
                        put(ownerId, guestId);
                        put(guestId, guestId);
                    }
                })
                .mostVoted(Collections.singletonList(new AbstractMap.SimpleEntry<>(liarDesignatedId, 2L)))
                .build();
        MessageContainer expectMessage = Util.getExpectedMessageContainer(NOTIFY_VOTE_RESULT, expectedVoteResult);
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getSenderId()).isEqualTo(SERVER_ID);
        assertThat(message1.getMessage()).isEqualTo(expectMessage.getMessage());

        message1 = handler1.getCompletableFuture(1);
        message2 = handler2.getCompletableFuture(1);
        expectMessage = Util.getExpectedMessageContainer(NOTIFY_LIAR_OPENED, new GameStateResponse(GameState.OPEN_LIAR));
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getSenderId()).isEqualTo(SERVER_ID);
        assertThat(message1.getMessage()).isEqualTo(expectMessage.getMessage());
    }

    @Test
    public void 투표를안하면_타임아웃이난다() throws Exception {
        //Given
        __설명종료();
        System.out.println("투표=================================================================================================");

        //When
        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        TestStompHandlerChain<MessageContainer> handler2 = new TestStompHandlerChain<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);

        String uuid = UUID.randomUUID().toString();
        String guestId = sessionInfoList.get(0).guestId;
        String liarDesignatedId = sessionInfoList.get(0).guestId;
        __sendVoteLiar(uuid, ownerId, liarDesignatedId);

        Thread.sleep(5000);
        //Then
        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId(SERVER_ID)
                .message(new MessageContainer.Message(NOTIFY_VOTE_TIMEOUT, "{}"))
                .build();
        MessageContainer message1 = handler1.getCompletableFuture(-1);
        MessageContainer message2 = handler2.getCompletableFuture(-1);
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getMessage()).isEqualTo(expectMessage.getMessage());

        Thread.sleep(1000);
        MessageContainer message3 = handler1.getCompletableFuture(0);
        MessageContainer message4 = handler2.getCompletableFuture(0);

        VoteResult expectedVoteResult = VoteResult.builder()
                .voteResult(new HashMap<String, String>() {{
                    put(ownerId, guestId);
                    put(guestId, "");
                }})
                .mostVoted(Collections.singletonList(new AbstractMap.SimpleEntry<>(liarDesignatedId, 1L)))
                .build();
        expectMessage = Util.getExpectedMessageContainer(NOTIFY_VOTE_RESULT, expectedVoteResult);
        assertThat(message3).isEqualTo(message4);
        assertThat(message3.getSenderId()).isEqualTo(SERVER_ID);
        assertThat(message3.getMessage()).isEqualTo(expectMessage.getMessage());

        message3 = handler1.getCompletableFuture(1);
        message4 = handler2.getCompletableFuture(1);
        expectMessage = Util.getExpectedMessageContainer(NOTIFY_LIAR_OPENED, new GameStateResponse(GameState.OPEN_LIAR));
        assertThat(message3).isEqualTo(message4);
        assertThat(message3.getSenderId()).isEqualTo(SERVER_ID);
        assertThat(message3.getMessage()).isEqualTo(expectMessage.getMessage());
    }

    private void __sendVoteLiar(String uuid, String senderId, String liarDesignatedId) {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(senderId)
                .message(new MessageContainer.Message(VOTE_LIAR, String.format("{\"liar\":\"%s\"}", liarDesignatedId)))
                .build();
        stompSession.send(String.format("/publish/system/public/%s", roomId), sendMessage);
    }

    @Test
    public void 동투표가_나올경우_재투표해야한다() throws Exception {
        //Given
        __설명종료();
        System.out.println("투표=================================================================================================");

        //When
        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        TestStompHandlerChain<MessageContainer> handler2 = new TestStompHandlerChain<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);

        String uuid = UUID.randomUUID().toString();
        String guestId = sessionInfoList.get(0).guestId;
        String liarDesignatedId = sessionInfoList.get(0).guestId;
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(VOTE_LIAR, String.format("{\"liar\":\"%s\"}", liarDesignatedId)))
                .build();
        stompSession.send(String.format("/publish/system/public/%s", roomId), sendMessage);

        sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(guestId)
                .message(new MessageContainer.Message(VOTE_LIAR, String.format("{\"liar\":\"%s\"}", ownerId)))
                .build();
        sessionInfoList.get(0).session.send(String.format("/publish/system/public/%s", roomId), sendMessage);

        System.out.println("send Message : " + sendMessage);
        Thread.sleep(400);

        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler2.getCompletableFuture(0);

        //Then
        VoteResult expectedVoteResult = VoteResult.builder()
                .voteResult(new HashMap<String, String>() {
                    {
                        put(ownerId, guestId);
                        put(guestId, ownerId);
                    }
                })
                .mostVoted(Arrays.asList(new AbstractMap.SimpleEntry<>(ownerId, 1L), new AbstractMap.SimpleEntry<>(liarDesignatedId, 1L)))
                .build();
        MessageContainer expectMessage = Util.getExpectedMessageContainer(NOTIFY_VOTE_RESULT, expectedVoteResult);
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getSenderId()).isEqualTo(SERVER_ID);
        assertThat(message1.getMessage().getMethod()).isEqualTo(expectMessage.getMessage().getMethod());

        VoteResult result = objectMapper.readValue(message1.getMessage().getBody(), VoteResult.class);
        assertThat(result.getMostVoted()).satisfiesAnyOf(
                param -> assertThat(result.getMostVoted()).isEqualTo(Arrays.asList(new AbstractMap.SimpleEntry<>(ownerId, 1L), new AbstractMap.SimpleEntry<>(liarDesignatedId, 1L))),
                param -> assertThat(result.getMostVoted()).isEqualTo(Arrays.asList(new AbstractMap.SimpleEntry<>(liarDesignatedId, 1L), new AbstractMap.SimpleEntry<>(ownerId, 1L)))
        );

        Thread.sleep(500);
        message1 = handler1.getCompletableFuture(1);
        message2 = handler2.getCompletableFuture(1);
        expectMessage = Util.getExpectedMessageContainer(NOTIFY_NEW_VOTE_NEEDED, new GameStateResponse(GameState.VOTE_LIAR));
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getSenderId()).isEqualTo(SERVER_ID);
        assertThat(message1.getMessage()).isEqualTo(expectMessage.getMessage());
    }

    //@Test
    public void 라이어공개요청하면_라이어를모두에게알리고_라이어에게정답요청을한다() throws Exception {
        __라이어공개요청();
    }

    private void __라이어공개요청() throws Exception {
        //Given
        __모두가_투표를하면_투표결과를알려준다();
        System.out.println("라이어공개요청==================================================================");

        //When
        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        TestSingleStompHandler<MessageContainer> handler2 = new TestSingleStompHandler<>(MessageContainer.class);
        TestSingleStompHandler<MessageContainer> handler3 = new TestSingleStompHandler<>(MessageContainer.class);
        TestSingleStompHandler<MessageContainer> handler4 = new TestSingleStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        stompSession.subscribe(String.format("/subscribe/system/private/%s", ownerId), handler3);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/private/%s", sessionInfoList.get(0).guestId), handler4);

        __openLiar();

        MessageContainer message1 = handler1.getCompletableFuture().get(5, SECONDS);
        MessageContainer message2 = handler2.getCompletableFuture().get(5, SECONDS);

        String guestId = sessionInfoList.get(0).guestId;
        OpenLiarResponse expectResultLiarIsRoomOwner = OpenLiarResponse.builder()
                .liar(ownerId)
                .matchLiar(false)
                .state(GameState.LIAR_ANSWER)
                .build();
        MessageContainer expectMessageRoomOwner = Util.getExpectedMessageContainer(NOTIFY_VOTE_RESULT, expectResultLiarIsRoomOwner);
        OpenLiarResponse expectResultLiarIsGuest = OpenLiarResponse.builder()
                .liar(guestId)
                .matchLiar(true)
                .state(GameState.LIAR_ANSWER)
                .build();
        MessageContainer expectMessageGuest = Util.getExpectedMessageContainer(NOTIFY_VOTE_RESULT, expectResultLiarIsGuest);

        //Then
        assertThat(message1).isEqualTo(message2);
        assertThat(expectMessageRoomOwner.getMessage()).satisfiesAnyOf(
                param -> assertThat(message1.getMessage().getBody()).isEqualTo(expectMessageRoomOwner.getMessage().getBody()),
                param -> assertThat(message1.getMessage().getBody()).isEqualTo(expectMessageGuest.getMessage().getBody())
        );
        String liarId = gameService.getGame(roomId).getLiarId();
        if (liarId.equals(ownerId)) {
            assertThat(message1.getMessage().getBody()).isEqualTo(expectMessageRoomOwner.getMessage().getBody());
            MessageContainer message3 = handler3.getCompletableFuture().get(5, SECONDS);
            assertThat(message3.getMessage().getMethod()).isEqualTo(NOTIFY_LIAR_ANSWER_NEEDED);
        } else {
            assertThat(message1.getMessage().getBody()).isEqualTo(expectMessageGuest.getMessage().getBody());
            MessageContainer message4 = handler4.getCompletableFuture().get(5, SECONDS);
            assertThat(message4.getMessage().getMethod()).isEqualTo(NOTIFY_LIAR_ANSWER_NEEDED);
        }
    }

    private void __openLiar() {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(UUID.randomUUID().toString())
                .senderId(ownerId)
                .message(new MessageContainer.Message(OPEN_LIAR, "{}"))
                .build();
        stompSession.send(String.format("/publish/system/private/%s", roomId), sendMessage);
    }

    //@Test
    public void 라이어정답제출했을때_맞춤() throws Exception {
        __라이어정답제출();
    }

    private void __라이어정답제출() throws Exception {
        //Given
        __라이어공개요청();
        gameService.cancelAnswerTimer(roomId);
        //When
        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);

        String expectKeyword = gameService.getGame(roomId).getCurrentRoundKeyword();
        __checkIfLiarAnswerIsCorrect(handler1, expectKeyword);
        Thread.sleep(500);

        MessageContainer messageFromServer = handler1.getCompletableFuture(0);
        LiarAnswerResponse expectResultLiarIsRoomOwner = LiarAnswerResponse.builder()
                .answer(true)
                .state(GameState.PUBLISH_SCORE)
                .build();
        MessageContainer expectMessageRoomOwner = Util.getExpectedMessageContainer(NOTIFY_LIAR_ANSWER_CORRECT, expectResultLiarIsRoomOwner);

        //Then
        assertThat(messageFromServer.getMessage()).isEqualTo(expectMessageRoomOwner.getMessage());
    }

    private void __checkIfLiarAnswerIsCorrect(TestStompHandlerChain<MessageContainer> handler1, String keyword) throws JsonProcessingException {
        String liarId = gameService.getGame(roomId).getLiarId();
        String body = objectMapper.writeValueAsString(new KeywordRequest(keyword));
        if (liarId.equals(ownerId)) {
            stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
            MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                    .uuid(UUID.randomUUID().toString())
                    .senderId(ownerId)
                    .message(new MessageContainer.Message(CHECK_KEYWORD_CORRECT, body))
                    .build();
            stompSession.send(String.format("/publish/system/private/%s", roomId), sendMessage);
        } else {
            sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
            MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                    .uuid(UUID.randomUUID().toString())
                    .senderId(sessionInfoList.get(0).guestId)
                    .message(new MessageContainer.Message(CHECK_KEYWORD_CORRECT, body))
                    .build();
            sessionInfoList.get(0).session.send(String.format("/publish/system/private/%s", roomId), sendMessage);
        }
    }

    @Test
    public void 라이어가정답을말하지못하면_타임아웃이_난다() throws Exception {
        //Given
        __라이어공개요청();
        //When
        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        TestStompHandlerChain<MessageContainer> handler2 = new TestStompHandlerChain<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);
        Thread.sleep(5500);

        //Then
        MessageContainer messageFromServerToOwner = handler1.getCompletableFuture(0);
        MessageContainer messageFromServerToGuest = handler2.getCompletableFuture(0);
        MessageContainer expectMessageRoomOwner = MessageContainer.messageContainerBuilder()
                .senderId(SERVER_ID)
                .message(new MessageContainer.Message(NOTIFY_LIAR_ANSWER_TIMEOUT, "{}"))
                .build();
        assertThat(messageFromServerToOwner).isEqualTo(messageFromServerToGuest);
        assertThat(messageFromServerToOwner.getMessage()).isEqualTo(expectMessageRoomOwner.getMessage());

        Thread.sleep(500);
        messageFromServerToOwner = handler1.getCompletableFuture(0);
        LiarAnswerResponse expectResultLiarIsRoomOwner = LiarAnswerResponse.builder()
                .answer(false)
                .state(GameState.PUBLISH_SCORE)
                .build();
        expectMessageRoomOwner = Util.getExpectedMessageContainer(NOTIFY_LIAR_ANSWER_CORRECT, expectResultLiarIsRoomOwner);

        assertThat(messageFromServerToOwner.getMessage()).isEqualTo(expectMessageRoomOwner.getMessage());
    }

    @Test
    public void 라이어정답제출했을때_틀림() throws Exception {
        //Given
        __라이어공개요청();
        gameService.cancelAnswerTimer(roomId);
        //When
        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        __checkIfLiarAnswerIsCorrect(handler1, "");
        Thread.sleep(500);

        MessageContainer messageFromServer = handler1.getCompletableFuture(0);

        String guestId = sessionInfoList.get(0).guestId;
        LiarAnswerResponse expectResultLiarIsRoomOwner = LiarAnswerResponse.builder()
                .answer(false)
                .state(GameState.PUBLISH_SCORE)
                .build();
        MessageContainer expectMessageRoomOwner = Util.getExpectedMessageContainer(NOTIFY_LIAR_ANSWER_CORRECT, expectResultLiarIsRoomOwner);

        //Then
        assertThat(messageFromServer.getMessage()).isEqualTo(expectMessageRoomOwner.getMessage());
    }

    //@Test
    public void 라이어맞추고_라이어키워드맞췄을때_점수공개() throws Exception {
        __라운드점수공개();
    }

    private void __라운드점수공개() throws Exception {
        //Given
        __라이어정답제출();
        System.out.println("라운드점수공개=====================================================================");
        //When
        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        TestStompHandlerChain<MessageContainer> handler2 = new TestStompHandlerChain<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);

        __openScore();
        Thread.sleep(500);

        //Then
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler2.getCompletableFuture(0);

        ScoreBoardResponse scoreBoardResponse;
        if (gameService.getGame(roomId).getLiarId().equals(ownerId)) {
            scoreBoardResponse = ScoreBoardResponse.builder()
                    .scoreBoard(new HashMap<String, Integer>() {{
                        put(ownerId, 3);
                        put(sessionInfoList.get(0).guestId, 0);
                    }})
                    .build();
        } else {
            scoreBoardResponse = ScoreBoardResponse.builder()
                    .scoreBoard(new HashMap<String, Integer>() {{
                        put(ownerId, 1);
                        put(sessionInfoList.get(0).guestId, 1);
                    }})
                    .build();
        }
        MessageContainer expectMessageOwner = Util.getExpectedMessageContainer(NOTIFY_SCORES, scoreBoardResponse);
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getMessage()).isEqualTo(expectMessageOwner.getMessage());

        MessageContainer message3 = handler1.getCompletableFuture(1);
        MessageContainer message4 = handler2.getCompletableFuture(1);
        RoundInfoResponse expectedRoundInfo = new RoundInfoResponse(GameState.BEFORE_ROUND, 1);
        expectMessageOwner = Util.getExpectedMessageContainer(NOTIFY_ROUND_END, expectedRoundInfo);
        assertThat(message3).isEqualTo(message4);
        assertThat(message3.getMessage()).isEqualTo(expectMessageOwner.getMessage());
        System.out.println("message3.getMessage():" + message3.getMessage());
    }

    private void __openScore() {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(UUID.randomUUID().toString())
                .senderId(ownerId)
                .message(new MessageContainer.Message(OPEN_SCORES, "{}"))
                .build();
        stompSession.send(String.format("/publish/system/private/%s", roomId), sendMessage);
    }

    @Test
    public void 게임이_종료될때까지진행하면_게임종료를알린다() throws Exception {
        //Given
        __라운드점수공개();
        System.out.println("1라운드종료===================================================================");

        //When
        GameInfo gameInfo = gameService.getGame(roomId);
        gameController.setTimeout(20000);

        //TODO:completableFuture 사용하기
        __sendStartRound(UUID.randomUUID().toString());
        Thread.sleep(1000);
        __sendSelectLiar(UUID.randomUUID().toString());
        Thread.sleep(1000);
        __SendOpenKeyword(UUID.randomUUID().toString());
        Thread.sleep(1000);
        __sendRequestTurnFinished(gameInfo.getTurnOrder().get(0), UUID.randomUUID().toString());
        Thread.sleep(1000);
        __sendRequestTurnFinished(gameInfo.getTurnOrder().get(1), UUID.randomUUID().toString());
        Thread.sleep(1000);
        __sendRequestTurnFinished(gameInfo.getTurnOrder().get(0), UUID.randomUUID().toString());
        Thread.sleep(1000);
        __sendRequestTurnFinished(gameInfo.getTurnOrder().get(1), UUID.randomUUID().toString());
        Thread.sleep(1000);

        String guestId = sessionInfoList.get(0).guestId;
        String liarDesignatedId = sessionInfoList.get(0).guestId;
        __sendVoteLiar(UUID.randomUUID().toString(), ownerId, liarDesignatedId);
        __sendVoteLiar(UUID.randomUUID().toString(), guestId, liarDesignatedId);

        Thread.sleep(1000);
        __openLiar();
        Thread.sleep(1000);
        __checkIfLiarAnswerIsCorrect(new TestStompHandlerChain<>(MessageContainer.class), gameService.getGame(roomId).getCurrentRoundKeyword());
        Thread.sleep(1000);

        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        TestStompHandlerChain<MessageContainer> handler2 = new TestStompHandlerChain<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);
        __openScore();
        Thread.sleep(1000);

        handler1.getCompletableFuture(0);
        handler2.getCompletableFuture(0);
        MessageContainer message3 = handler1.getCompletableFuture(1);
        MessageContainer message4 = handler2.getCompletableFuture(1);

        if (gameInfo.getState().equals(GameState.PUBLISH_RANKINGS)) {
            RoundInfoResponse expectedRoundInfo = new RoundInfoResponse(GameState.PUBLISH_RANKINGS, 2);
            MessageContainer expectMessage = Util.getExpectedMessageContainer(NOTIFY_ROUND_END, expectedRoundInfo);
            assertThat(message3).isEqualTo(message4);
            assertThat(message3.getMessage()).isEqualTo(expectMessage.getMessage());
        }

        //Then
        handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        handler2 = new TestStompHandlerChain<>(MessageContainer.class);
        stompSession.subscribe(String.format("/subscribe/system/public/%s", roomId), handler1);
        sessionInfoList.get(0).session.subscribe(String.format("/subscribe/system/public/%s", roomId), handler2);

        __sendPublishRankings();
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler2.getCompletableFuture(0);
        assertThat(message1).isEqualTo(message2);
        Rankings expectedRankings = new Rankings(gameInfo.getScoreBoard().entrySet().stream().sorted(Map.Entry.comparingByValue((entry1, entry2) -> entry2.compareTo(entry1)))
                .map(entry -> new Rankings.RankingInfo(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
        MessageContainer expectMessage = Util.getExpectedMessageContainer(NOTIFY_RANKINGS_PUBLISHED, expectedRankings);
        assertThat(message1.getMessage()).isEqualTo(expectMessage.getMessage());

    }

    private void __sendPublishRankings() {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(UUID.randomUUID().toString())
                .senderId(ownerId)
                .message(new MessageContainer.Message(PUBLISH_RANKINGS, "{}"))
                .build();
        stompSession.send(String.format("/publish/system/private/%s", roomId), sendMessage);
        System.out.println("sendMessage : " + sendMessage);
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void requestTurnFinishedAndVerify(GameInfo gameInfo, TestStompHandlerChain<MessageContainer> handler1, TestStompHandlerChain<MessageContainer> handler2, StompSession session, int i) throws InterruptedException, ExecutionException, TimeoutException {
        String uuid = UUID.randomUUID().toString();
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(gameInfo.getTurnOrder().get(1 - i))
                .message(new MessageContainer.Message(Global.REQUEST_TURN_FINISH, "{}"))
                .build();

        session.send(String.format("/publish/system/public/%s", roomId), sendMessage);
        Thread.sleep(500);
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler2.getCompletableFuture(0);

        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(SERVER_ID)
                .message(new MessageContainer.Message(NOTIFY_TURN,
                        String.format("{\"turnId\":\"%s\",\"state\":\"IN_PROGRESS\"}", gameInfo.getTurnOrder().get(i))))
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

    private static class TestStompHandlerChain<T> implements StompFrameHandler {
        public final Queue<CompletableFuture<T>> completableFuture = new ConcurrentLinkedQueue<>();

        private CompletableFuture<T> __getCompletableFuture() {
            System.out.println(LocalDateTime.now() + ":[TestStompHandlerChain] getCompletableFuture size:" + completableFuture.size());
            return completableFuture.peek();
        }

        public T getCompletableFuture(int index) {
            try {
                T ret = __getCompletableFuture().get(5, SECONDS);
                completableFuture.remove();
                return ret;
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }

        }

        private final Class<T> tClass;

        public TestStompHandlerChain(Class<T> tClass) {
            this.tClass = tClass;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            System.out.println(LocalDateTime.now() + "payload : " + headers);
            return this.tClass;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            synchronized (this) {
                CompletableFuture<T> future = new CompletableFuture<>();
                future.complete((T) payload);
                completableFuture.add(future);
                System.out.println(LocalDateTime.now() + ":[TestStompHandlerChain] handleFrame headers: " + headers + ", payload: " + payload + ", completableFuture.size() :" + completableFuture.size());
            }
        }
    }

    private static class TestSingleStompHandler<T> implements StompFrameHandler {
        public final CompletableFuture<T> completableFuture = new CompletableFuture<>();

        public CompletableFuture<T> getCompletableFuture() {
            //System.out.println(LocalDateTime.now() + ":[TestSingleStompHandler] getCompletableFuture");
            return completableFuture;
        }

        private final Class<T> tClass;

        public TestSingleStompHandler(Class<T> tClass) {
            this.tClass = tClass;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            //System.out.println("payload : " + headers);
            return this.tClass;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            //System.out.println("[TestSingleStompHandler] handleFrame headers: " + headers + ", payload: " + payload);
            completableFuture.complete((T) payload);
        }
    }

    private static class Util<T> {
        static ObjectMapper objectMapper = new ObjectMapper();

        public static <T> MessageContainer getExpectedMessageContainer(String state, T expectedResponse) throws JsonProcessingException {
            return MessageContainer.messageContainerBuilder()
                    .senderId(SERVER_ID)
                    .message(new MessageContainer.Message(state, objectMapper.writeValueAsString(expectedResponse)))
                    .build();
        }
    }
}