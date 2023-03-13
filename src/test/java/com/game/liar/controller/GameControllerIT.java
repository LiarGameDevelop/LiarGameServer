package com.game.liar.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.exception.*;
import com.game.liar.game.config.GameCategoryProperties;
import com.game.liar.game.controller.GameController;
import com.game.liar.game.domain.GameInfo;
import com.game.liar.game.domain.GameSettings;
import com.game.liar.game.domain.GameState;
import com.game.liar.game.domain.Global;
import com.game.liar.game.dto.MessageBody;
import com.game.liar.game.dto.MessageContainer;
import com.game.liar.game.dto.request.KeywordRequest;
import com.game.liar.game.dto.request.LiarDesignateRequest;
import com.game.liar.game.dto.response.*;
import com.game.liar.game.service.GameService;
import com.game.liar.room.dto.EnterRoomResponse;
import com.game.liar.room.dto.UserDataDto;
import com.game.liar.utils.BeanUtils;
import com.game.liar.websocket.InboundInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.game.liar.Util.*;
import static com.game.liar.game.domain.Global.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ExtendWith(MockitoExtension.class)
//@ActiveProfiles("test")
@Disabled
class GameControllerIT {
    @LocalServerPort
    private Integer port;
    @Autowired
    GameController gameController;
    @Autowired
    GameService gameService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    GameCategoryProperties gameCategoryProperties;
    @MockBean
    private InboundInterceptor inboundInterceptor;

    WebSocketStompClient stompClient;
    StompSession stompSession;

    ObjectMapper objectMapper = new ObjectMapper();
    String roomId;
    String ownerId;

    List<SessionInfo> sessionInfoList = new ArrayList<>();

    TestStompObject stompObject;
    TestStompObject stompObject2;

    public static final String SUBSCRIBE_PUBLIC = "/topic/room.";
    public static final String SUBSCRIBE_PRIVATE = "/exchange/message.direct/room.";
    public static final String PUBLISH_PRIVATE = "/publish/private.";
    public static final String SUBSCRIBE_ERRORS = "/exchange/message.error/user.";

    TestStompHandlerChain<MessageContainer> handler1;
    TestStompHandlerChain<MessageContainer> handler2;
    TestStompHandlerChain<MessageContainer> handler3;
    TestStompHandlerChain<MessageContainer> handler4;

    //@BeforeEach
    void init() {
        stompClient = new WebSocketStompClient(new SockJsClient(createTransportClient()));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        when(inboundInterceptor.preSend(any(), any())).thenAnswer(i -> i.getArguments()[0]);

        try {
            stompSession = stompClient.connect("ws://localhost:" + port + "/ws-connection", new StompSessionHandlerAdapter() {
            }).get(3, SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        assertThat(stompSession).isNotNull();
        assertThat(stompSession.isConnected()).isTrue();
        gameController.setTimeout(7000);

        try {
            String roomId = 방생성("roomOwner");
            게임참가("user1", roomId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void afterEachTest() {

    }

    private class SessionInfo {
        StompSession session;
        EnterRoomResponse roomInfo;
        String guestId;
    }

    private void 게임참가(String username, String roomId) throws Exception {
        stompObject2 = createStompObjAndEnterRoom(mockMvc, port, roomId);

        SessionInfo s = new SessionInfo();
        s.session = stompObject2.getStompSession();
        s.roomInfo = stompObject2.getRoomInfo();
        s.guestId = s.roomInfo.getUser().getUserId();

        sessionInfoList.add(s);

        assertThat(s.session).isNotNull();
        assertThat(s.session.isConnected()).isTrue();

        gameService.addMember(roomId, new UserDataDto(username, s.guestId));
    }

    private String 방생성(String roomOwner) throws Exception {
        stompObject = createStompObj(mockMvc, port);

        EnterRoomResponse roomInfo = stompObject.getRoomInfo();
        roomId = roomInfo.getRoom().getRoomId();
        ownerId = roomInfo.getRoom().getOwnerId();
        gameService.addMember(roomId, new UserDataDto(roomOwner, ownerId));
        return roomId;
    }

//    @Test
//    public void 게임시작() throws Exception {
//        __게임시작();
//    }

    void teardown() {
        gameService.getGame(roomId).cancelTurnTimer();
        gameService.getGame(roomId).cancelVoteTimer();
        gameService.getGame(roomId).cancelAnswerTimer();
        stompObject.getStompSession().disconnect();
        sessionInfoList.clear();
    }

    private void __게임시작() throws Exception {
        init();
        //given
        connectSubscribe(roomId, ownerId);

        //when
        String uuid = UUID.randomUUID().toString();

        GameSettings settings = GameSettings.builder()
                .round(2)
                .turn(2)
                .category(Arrays.asList("food", "sports"))
                .build();
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(START_GAME, settings))
                .build();
        System.out.println("sendMessage : " + sendMessage);

        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));

        //then
        MessageContainer message = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler3.getCompletableFuture(0);

        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(NOTIFY_GAME_STARTED, new GameStateResponse(GameState.BEFORE_ROUND) {
                }))
                .uuid(uuid)
                .build();

        System.out.println(message);
        assertThat(message).isEqualTo(message2);
        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectMessage);
    }

    private void connectSubscribe(String roomId, String userId) {
        handler1 = new TestStompHandlerChain<>(MessageContainer.class);
        handler2 = new TestStompHandlerChain<>(MessageContainer.class);
        stompSession.subscribe(String.format("%s%s.user.*", SUBSCRIBE_PUBLIC, roomId), handler1);
        stompSession.subscribe(String.format("%s%s.user.%s", SUBSCRIBE_PRIVATE, roomId, userId), handler2);

        handler3 = new TestStompHandlerChain<>(MessageContainer.class);
        handler4 = new TestStompHandlerChain<>(MessageContainer.class);
        sessionInfoList.get(0).session.subscribe(String.format("%s%s.user.*", SUBSCRIBE_PUBLIC, roomId), handler3);
        sessionInfoList.get(0).session.subscribe(String.format("%s%s.user.%s", SUBSCRIBE_PRIVATE, roomId, sessionInfoList.get(0).guestId), handler4);
    }

    @Test
    public void 라운드를초과한세팅이면_게임시작에_실패한다() throws Exception {
        init();
        System.out.println("owner id " + ownerId);
        //given
        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("%s%s", SUBSCRIBE_ERRORS, ownerId), handler1);
        TestSingleStompHandler<MessageContainer> handler2 = new TestSingleStompHandler<>(MessageContainer.class);
        sessionInfoList.get(0).session.subscribe(String.format("%s%s", SUBSCRIBE_ERRORS, sessionInfoList.get(0).guestId), handler2);

        //when
        String uuid = UUID.randomUUID().toString();

        GameSettings settings = GameSettings.builder()
                .round(6)
                .turn(2)
                .category(Arrays.asList("food", "sports"))
                .build();
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(START_GAME, settings))
                .build();
        System.out.println("sendMessage : " + sendMessage);
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));

        //then
        MessageContainer message = handler1.getCompletableFuture().get(5, SECONDS);

        LiarGameException expectedException = new NotAllowedActionException("Round can be 1 to 5");
        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(NOTIFY_GAME_STARTED,
                        new ErrorResponse(objectMapper.writeValueAsString(new ErrorResult(expectedException.getCode(), expectedException.getMessage()))) {
                        }))
                .uuid(uuid)
                .build();

        System.out.println(message);
        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectMessage);
        assertThrows(TimeoutException.class, () -> handler2.getCompletableFuture().get(3, SECONDS));
    }

    @Test
    public void 게임카테고리를요청하면_reply한다() throws Exception {
        init();
        //given
        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        StompSession.Subscription sub = stompSession.subscribe(String.format("%s%s.user.%s", SUBSCRIBE_PRIVATE, roomId, ownerId), handler1);
        //when
        String uuid = UUID.randomUUID().toString();
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(GET_GAME_CATEGORY, null))
                .build();
        System.out.println("sendMessage : " + sendMessage);
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));

        //then
        MessageContainer message = handler1.getCompletableFuture().get(3, SECONDS);
        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(NOTIFY_GAME_CATEGORY,
                        new GameCategoryResponse(new ArrayList<>((
                                (GameCategoryProperties) BeanUtils.getBean(GameCategoryProperties.class))
                                .getKeywords().keySet().stream().sorted().collect(Collectors.toList()))) {
                        }))
                .uuid(uuid)
                .build();

        System.out.println(message);
        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectMessage);
    }

    @Test
    public void 스테이지를_잘못_설정하여_게임시작요청하면_예외처리한다() throws Exception {
        init();
        //given
        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("%s%s", SUBSCRIBE_ERRORS, ownerId), handler1);
        TestSingleStompHandler<MessageContainer> handler2 = new TestSingleStompHandler<>(MessageContainer.class);
        sessionInfoList.get(0).session.subscribe(String.format("%s%s", SUBSCRIBE_ERRORS, sessionInfoList.get(0).guestId), handler2);

        //when
        String uuid = UUID.randomUUID().toString();

        GameSettings settings = GameSettings.builder()
                .round(6)
                .turn(2)
                .category(Arrays.asList("food", "sports"))
                .build();
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(START_ROUND, settings))
                .build();
        System.out.println("sendMessage : " + sendMessage);
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));

        //then
        MessageContainer message = handler1.getCompletableFuture().get(3, SECONDS);
        LiarGameException expectedException = new StateNotAllowedExpcetion("Current State is not BEFORE_ROUND");
        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(NOTIFY_ROUND_STARTED,
                        new ErrorResponse(objectMapper.writeValueAsString(new ErrorResult(expectedException.getCode(), expectedException.getMessage()))) {
                        }))
                .uuid(uuid)
                .build();

        System.out.println(message);
        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectMessage);
        assertThrows(TimeoutException.class, () -> handler2.getCompletableFuture().get(3, SECONDS));


    }

    @Test
    @Disabled("방장 아닌 사람에게 메세지 보내는 방법 필요")
    public void 방장이아닌사람이_게임시작요청하면_예외처리한다() throws Exception {
        //given
        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("%s%s", SUBSCRIBE_ERRORS, ownerId), handler1);
        TestSingleStompHandler<MessageContainer> handler2 = new TestSingleStompHandler<>(MessageContainer.class);
        sessionInfoList.get(0).session.subscribe(String.format("%s%s", SUBSCRIBE_ERRORS, sessionInfoList.get(0).guestId), handler2);

        //when
        String uuid = UUID.randomUUID().toString();

        GameSettings settings = GameSettings.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports"))
                .build();
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(sessionInfoList.get(0).guestId)
                .message(new MessageContainer.Message(START_GAME, settings))
                .build();
        System.out.println("sendMessage : " + sendMessage);
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));

        //then
        MessageContainer message = handler1.getCompletableFuture().get(3, SECONDS);
        LiarGameException expectedException = new NotAllowedActionException("You are not owner of this room");
        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(NOTIFY_GAME_STARTED,
                        new ErrorResponse(objectMapper.writeValueAsString(new ErrorResult(expectedException.getCode(), expectedException.getMessage()))) {
                        }))
                .uuid(uuid)
                .build();
        assertThat(message).isEqualTo(expectMessage);
        assertThrows(TimeoutException.class, () -> handler2.getCompletableFuture().get(3, SECONDS));
    }

    @Test
    public void 게임시작요청시_필수파라미터가없으면_예외처리한다() throws Exception {
        init();
        //given
        TestSingleStompHandler<MessageContainer> handler1 = new TestSingleStompHandler<>(MessageContainer.class);
        stompSession.subscribe(String.format("%s%s", SUBSCRIBE_ERRORS, ownerId), handler1);
        TestSingleStompHandler<MessageContainer> handler2 = new TestSingleStompHandler<>(MessageContainer.class);
        sessionInfoList.get(0).session.subscribe(String.format("%s%s", SUBSCRIBE_ERRORS, sessionInfoList.get(0).guestId), handler2);

        //when
        String uuid = UUID.randomUUID().toString();

        GameSettings settings = GameSettings.builder()
                .round(5)
                .category(Arrays.asList("food", "sports"))
                .build();
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(START_GAME, settings))
                .build();
        System.out.println("sendMessage : " + sendMessage);
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));

        //then
        MessageContainer message = handler1.getCompletableFuture().get(3, SECONDS);
        LiarGameException expectedException = new RequiredParameterMissingException("Game round, turn, and category field are required");
        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(NOTIFY_GAME_STARTED,
                        new ErrorResponse(objectMapper.writeValueAsString(new ErrorResult(expectedException.getCode(), expectedException.getMessage()))) {
                        }))
                .uuid(uuid)
                .build();

        System.out.println(message);
        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectMessage);
        assertThrows(TimeoutException.class, () -> handler2.getCompletableFuture().get(3, SECONDS));

    }

//    @Test
//    public void 라운드시작() throws Exception {
//        __라운드시작();
//    }

    private void __라운드시작() throws Exception {
        //when
        __게임시작();
        System.out.println("라운드시작=================================================================================================");

        String uuid = UUID.randomUUID().toString();
        __sendStartRound(uuid);

        //then
        MessageContainer message = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler3.getCompletableFuture(0);
        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_ROUND_STARTED, new RoundResponse(GameState.SELECT_LIAR, 1)))
                .uuid(uuid)
                .build();

        assertThat(message).isEqualTo(message2);
        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectMessage);
    }

    private void __sendStartRound(String uuid) throws JsonProcessingException {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(Global.START_ROUND, null))
                .build();
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));
        System.out.println("sendMessage : " + sendMessage);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

//    @Test
//    public void 라이어선정요청하면_private_라이어여부를_알려준다() throws Exception {
//        __라이어선정();
//    }

    private void __라이어선정() throws Exception {
        //Given
        __라운드시작();
        System.out.println("라이어선정=================================================================================================");
        //When
        String uuid = UUID.randomUUID().toString();
        __sendSelectLiar(uuid);

        //then
        MessageContainer messageToOwner = handler2.getCompletableFuture(0);
        MessageContainer expectMessageToOwner = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_LIAR_SELECTED, new LiarResponse(GameState.OPEN_KEYWORD, false)))
                .uuid(uuid)
                .build();

        MessageContainer messageToUser = handler4.getCompletableFuture(0);
        MessageContainer expectMessageToUser = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_LIAR_SELECTED, new LiarResponse(GameState.OPEN_KEYWORD, true)))
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
            assertThat(messageToOwner.getMessage().getBody()).isEqualTo(new LiarResponse(GameState.OPEN_KEYWORD, true));
            assertThat(messageToUser.getMessage().getBody()).isEqualTo(new LiarResponse(GameState.OPEN_KEYWORD, false));
        } else {
            assertThat(messageToOwner.getMessage().getBody()).isEqualTo(new LiarResponse(GameState.OPEN_KEYWORD, false));
            assertThat(messageToUser.getMessage().getBody()).isEqualTo(new LiarResponse(GameState.OPEN_KEYWORD, true));
        }
    }

    private void __sendSelectLiar(String uuid) throws JsonProcessingException {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .build();
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));
    }

//    @Test
//    public void 키워드공개를요청하면_각사람에게키워드를_private전달하고_첫번째턴사람을공개한다() throws Exception {
//        __카테고리알림();
//    }

    private OpenedGameInfo __카테고리알림() throws Exception {
        //Given
        __라이어선정();
        System.out.println("카테고리알림=================================================================================================");
        //When
        String uuid = UUID.randomUUID().toString();

        __SendOpenKeyword(uuid);
        MessageContainer messageToOwner = handler2.getCompletableFuture(0);
        MessageContainer expectMessageToOwner = MessageContainer.messageContainerBuilder()
                .senderId("SERVER")
                .message(new MessageContainer.Message(Global.NOTIFY_KEYWORD_OPENED, null))
                .uuid(uuid)
                .build();
        MessageContainer messageToGuest = handler4.getCompletableFuture(0);

        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler3.getCompletableFuture(0);

        //Then
        OpenedGameInfo gameInfoResultFromOwner = (OpenedGameInfo) messageToOwner.getMessage().getBody();
        OpenedGameInfo gameInfoResultFromGuest = (OpenedGameInfo) messageToGuest.getMessage().getBody();

        assertThat(messageToOwner).isNotNull();
        assertThat(messageToOwner.getSenderId()).isEqualTo(expectMessageToOwner.getSenderId());
        assertThat(messageToOwner.getUuid()).isEqualTo(expectMessageToOwner.getUuid());
        assertThat(messageToOwner.getMessage().getMethod()).isEqualTo(expectMessageToOwner.getMessage().getMethod());

        assertThat(new ArrayList<>(gameCategoryProperties.getKeywords().keySet())).containsAnyOf(gameInfoResultFromOwner.getCategory());
        assertThat(gameInfoResultFromOwner.getCategory()).isEqualTo(gameInfoResultFromGuest.getCategory());

        assertThat(gameInfoResultFromOwner.getTurnOrder()).isEqualTo(gameInfoResultFromGuest.getTurnOrder());

        assertThat(gameInfoResultFromOwner.getKeyword()).satisfiesAnyOf(
                param -> assertThat(gameInfoResultFromOwner.getKeyword()).containsAnyOf(gameCategoryProperties.loadKeywords(gameInfoResultFromOwner.getCategory()).toArray(new String[0])),
                param -> assertThat(gameInfoResultFromOwner.getKeyword()).isEqualTo("")
        );
        assertThat(gameInfoResultFromOwner.getTurnOrder()).satisfiesAnyOf(
                param -> assertThat(gameInfoResultFromOwner.getTurnOrder()).containsExactly(ownerId, sessionInfoList.get(0).guestId),
                param -> assertThat(gameInfoResultFromOwner.getTurnOrder()).containsExactly(sessionInfoList.get(0).guestId, ownerId)
        );
        System.out.println(LocalDateTime.now() + ":gameInfoResultFromOwner.getTurnOrder(): " + gameInfoResultFromOwner.getTurnOrder());
        System.out.println(LocalDateTime.now() + ":before public message arrived");

        System.out.println("message1 : "+message1);
        System.out.println("message2 : "+message2);
        TurnResponse publicMessageToOwner = (TurnResponse) message1.getMessage().getBody();
        TurnResponse publicMessageToUser = (TurnResponse) message2.getMessage().getBody();
        assertThat(message1.getMessage().getMethod()).isEqualTo(Global.NOTIFY_TURN);
        assertThat(message2.getMessage().getMethod()).isEqualTo(Global.NOTIFY_TURN);
        assertThat(publicMessageToOwner.getTurnId()).isEqualTo(gameInfoResultFromOwner.getTurnOrder().get(0));
        assertThat(publicMessageToUser.getTurnId()).isEqualTo(gameInfoResultFromOwner.getTurnOrder().get(0));

        return gameInfoResultFromOwner;
    }

    private void __SendOpenKeyword(String uuid) throws JsonProcessingException {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(Global.OPEN_KEYWORD, null))
                .build();
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));
    }

//    @Test
//    public void 현재턴의사람이_대답후에_다음턴의사람을_알려준다() throws Exception {
//        __턴알림();
//    }

    private void __턴알림() throws Exception {
        //Given
        OpenedGameInfo gameInfoResultFromOwner = __카테고리알림();
        System.out.println("턴알림=================================================================================================");
        //When
        String uuid = UUID.randomUUID().toString();

        __sendRequestTurnFinished(gameService.getGame(roomId).getTurnOrder().get(0), uuid);
        Thread.sleep(1000);
        //Then
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler3.getCompletableFuture(0);
        TurnResponse publicMessageToOwner = (TurnResponse) message1.getMessage().getBody();
        TurnResponse publicMessageToUser = (TurnResponse) message2.getMessage().getBody();
        assertThat(message1.getMessage().getMethod()).isEqualTo(Global.NOTIFY_TURN);
        assertThat(message2.getMessage().getMethod()).isEqualTo(Global.NOTIFY_TURN);
        assertThat(publicMessageToOwner.getTurnId()).isEqualTo(gameInfoResultFromOwner.getTurnOrder().get(1));
        assertThat(publicMessageToUser.getTurnId()).isEqualTo(gameInfoResultFromOwner.getTurnOrder().get(1));
    }

    @Test
    public void 턴알림_TimeOut시간을넘기면_턴이넘어간다() throws Exception {
        //Given
        __카테고리알림();
        System.out.println("턴알림=================================================================================================");

        Thread.sleep(6500);

        //Then
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler3.getCompletableFuture(0);
        MessageContainer message3 = handler1.getCompletableFuture(1);
        MessageContainer message4 = handler3.getCompletableFuture(1);

        //둘중 한명은 라이어인데, 걔는 타임아웃을 받는다.
        assertThat(message1.getMessage().getMethod()).isEqualTo(NOTIFY_TURN_TIMEOUT);
        assertThat(message2.getMessage().getMethod()).isEqualTo(NOTIFY_TURN_TIMEOUT);
        assertThat(message3.getMessage().getMethod()).isEqualTo(NOTIFY_TURN);
        assertThat(message4.getMessage().getMethod()).isEqualTo(NOTIFY_TURN);

        teardown();
    }

//    @Test
//    public void 모두의턴이끝나면_설명종료를알린다() throws Exception {
//        __설명종료();
//    }

    private void __설명종료() throws Exception {
        //Given
        __턴알림();
        System.out.println("라운드종료=================================================================================================");

        //When
        GameInfo gameInfo = gameService.getGame(roomId);
        System.out.println("turn order:" + gameInfo.getTurnOrder());
        String uuid = UUID.randomUUID().toString();
        //1번차례임
        requestTurnFinishedAndVerify(gameInfo, handler1, handler3,
                gameInfo.getTurnOrder().get(1).equals(ownerId) ? stompSession : sessionInfoList.get(0).session, 0);
        Thread.sleep(500);
        requestTurnFinishedAndVerify(gameInfo, handler1, handler3,
                gameInfo.getTurnOrder().get(0).equals(ownerId) ? stompSession : sessionInfoList.get(0).session, 1);

        //Then
        __sendRequestTurnFinished(gameInfo.getTurnOrder().get(1), uuid);
        Thread.sleep(500);
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler3.getCompletableFuture(0);

        MessageContainer expectMessage = Util.getExpectedMessageContainer(NOTIFY_FINDING_LIAR_END, new RoundResponse(GameState.VOTE_LIAR, 1));
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getSenderId()).isEqualTo(SERVER_ID);
        assertThat(message1.getMessage()).isEqualTo(expectMessage.getMessage());
    }

    private void __sendRequestTurnFinished(String senderId, String uuid) throws JsonProcessingException {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(senderId)
                .message(new MessageContainer.Message(Global.REQUEST_TURN_FINISH, null))
                .build();

        if (senderId.equals(ownerId)) {
            stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));
        } else {
            sessionInfoList.get(0).session.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));
        }
    }

//    @Test
//    public void 모두가_투표를하면_투표결과를알려준다() throws Exception {
//        __모두가_투표를하면_투표결과를알려준다();
//    }

    private void __모두가_투표를하면_투표결과를알려준다() throws Exception {
        //Given
        __설명종료();
        System.out.println("투표=================================================================================================");

        //When
        String uuid = UUID.randomUUID().toString();
        String guestId = sessionInfoList.get(0).guestId;
        String liarDesignatedId = sessionInfoList.get(0).guestId;
        __sendVoteLiar(uuid, ownerId, liarDesignatedId);
        __sendVoteLiar(uuid, guestId, liarDesignatedId);
        Thread.sleep(500);
        //Then
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler3.getCompletableFuture(0);

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

        message1 = handler2.getCompletableFuture(1);
        expectMessage = Util.getExpectedMessageContainer(NOTIFY_LIAR_OPEN_REQUEST, new GameStateResponse(GameState.OPEN_LIAR));
        assertThat(message1.getSenderId()).isEqualTo(SERVER_ID);
        assertThat(message1.getMessage()).isEqualTo(expectMessage.getMessage());
    }

    @Test
    public void 투표를안하면_타임아웃이난다() throws Exception {
        //Given
        __설명종료();
        System.out.println("투표=================================================================================================");

        //When
//        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
//        TestStompHandlerChain<MessageContainer> handler3 = new TestStompHandlerChain<>(MessageContainer.class);
//        stompSession.subscribe(String.format("%s%s.user.*", SUBSCRIBE_PUBLIC, roomId), handler1);
//        sessionInfoList.get(0).session.subscribe(String.format("%s%s.user.*", SUBSCRIBE_PUBLIC, roomId), handler3);

        String uuid = UUID.randomUUID().toString();
        String guestId = sessionInfoList.get(0).guestId;
        String liarDesignatedId = sessionInfoList.get(0).guestId;
        __sendVoteLiar(uuid, ownerId, liarDesignatedId);

        Thread.sleep(6500);
        MessageContainer message1 = handler1.getCompletableFuture(-1);
        MessageContainer message2 = handler3.getCompletableFuture(-1);
        //Then
        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .senderId(SERVER_ID)
                .message(new MessageContainer.Message(NOTIFY_VOTE_TIMEOUT, null))
                .build();
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getMessage()).isEqualTo(expectMessage.getMessage());

        Thread.sleep(1000);
        MessageContainer message3 = handler1.getCompletableFuture(0);
        MessageContainer message4 = handler3.getCompletableFuture(0);

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

        teardown();

        //openLiar API is needed, so server does not send notify_liar_opened automatically
//        message3 = handler1.getCompletableFuture(1);
//        message4 = handler2.getCompletableFuture(1);
//        expectMessage = Util.getExpectedMessageContainer(NOTIFY_LIAR_OPENED, new GameStateResponse(GameState.OPEN_LIAR));
//        assertThat(message3).isEqualTo(message4);
//        assertThat(message3.getSenderId()).isEqualTo(SERVER_ID);
//        assertThat(message3.getMessage()).isEqualTo(expectMessage.getMessage());
    }

    private void __sendVoteLiar(String uuid, String senderId, String liarDesignatedId) throws JsonProcessingException {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(senderId)
                .message(new MessageContainer.Message(VOTE_LIAR,
                        new LiarDesignateRequest(liarDesignatedId)))
                .build();
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));
    }

    @Test
    public void 동투표가_나올경우_재투표해야한다() throws Exception {
        //Given
        __설명종료();
        System.out.println("투표=================================================================================================");

        //When
//        TestStompHandlerChain<MessageContainer> handler1 = new TestStompHandlerChain<>(MessageContainer.class);
//        TestStompHandlerChain<MessageContainer> handler3 = new TestStompHandlerChain<>(MessageContainer.class);
//        stompSession.subscribe(String.format("%s%s.user.*", SUBSCRIBE_PUBLIC, roomId), handler1);
//        sessionInfoList.get(0).session.subscribe(String.format("%s%s.user.*", SUBSCRIBE_PUBLIC, roomId), handler3);

        String uuid = UUID.randomUUID().toString();
        String guestId = sessionInfoList.get(0).guestId;
        String liarDesignatedId = sessionInfoList.get(0).guestId;
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(ownerId)
                .message(new MessageContainer.Message(VOTE_LIAR, new LiarDesignateRequest(liarDesignatedId)))
                .build();
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));

        sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(guestId)
                .message(new MessageContainer.Message(VOTE_LIAR, new LiarDesignateRequest(ownerId)))
                .build();
        sessionInfoList.get(0).session.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));

        System.out.println("send Message : " + sendMessage);
        Thread.sleep(400);

        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler3.getCompletableFuture(0);

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

        VoteResult result = (VoteResult) message1.getMessage().getBody();
        assertThat(result.getMostVoted()).satisfiesAnyOf(
                param -> assertThat(result.getMostVoted()).isEqualTo(Arrays.asList(new AbstractMap.SimpleEntry<String, Object>(ownerId, 1L), new AbstractMap.SimpleEntry<String, java.io.Serializable>(liarDesignatedId, 1L))),
                param -> assertThat(result.getMostVoted()).isEqualTo(Arrays.asList(new AbstractMap.SimpleEntry<String, java.io.Serializable>(liarDesignatedId, 1L), new AbstractMap.SimpleEntry<String, java.io.Serializable>(ownerId, 1L)))
        );

        Thread.sleep(500);
        message1 = handler1.getCompletableFuture(1);
        message2 = handler3.getCompletableFuture(1);
        expectMessage = Util.getExpectedMessageContainer(NOTIFY_NEW_VOTE_NEEDED, new GameStateResponse(GameState.VOTE_LIAR));
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getSenderId()).isEqualTo(SERVER_ID);
        assertThat(message1.getMessage().getMethod()).isEqualTo(expectMessage.getMessage().getMethod());
        assertThat(message1.getMessage()).isEqualTo(expectMessage.getMessage());

        teardown();
    }

//    @Test
//    public void 라이어공개요청하면_라이어를모두에게알리고_라이어에게정답요청을한다() throws Exception {
//        __라이어공개요청();
//    }

    private void __라이어공개요청() throws Exception {
        //Given
        __모두가_투표를하면_투표결과를알려준다();
        System.out.println("라이어공개요청==================================================================");

        //When
        __openLiar();

        MessageContainer publicMessage1 = handler1.getCompletableFuture(0);
        MessageContainer publicMessage2 = handler3.getCompletableFuture(0);

        String guestId = sessionInfoList.get(0).guestId;
        OpenLiarResponse expectResultLiarIsRoomOwner = OpenLiarResponse.builder()
                .liar(ownerId)
                .matchLiar(false)
                .state(GameState.LIAR_ANSWER)
                .build();
        MessageContainer expectMessageRoomOwner = Util.getExpectedMessageContainer(NOTIFY_LIAR_OPENED, expectResultLiarIsRoomOwner);
        OpenLiarResponse expectResultLiarIsGuest = OpenLiarResponse.builder()
                .liar(guestId)
                .matchLiar(true)
                .state(GameState.LIAR_ANSWER)
                .build();
        MessageContainer expectMessageGuest = Util.getExpectedMessageContainer(NOTIFY_LIAR_OPENED, expectResultLiarIsGuest);

        //Then
        assertThat(publicMessage1).isEqualTo(publicMessage2);
        assertThat(expectMessageRoomOwner.getMessage()).satisfiesAnyOf(
                param -> assertThat(publicMessage1.getMessage().getBody()).isEqualTo(expectMessageRoomOwner.getMessage().getBody()),
                param -> assertThat(publicMessage1.getMessage().getBody()).isEqualTo(expectMessageGuest.getMessage().getBody())
        );
        String liarId = gameService.getGame(roomId).getLiarId();
        if (liarId.equals(ownerId)) {
            assertThat(publicMessage1.getMessage().getBody()).isEqualTo(expectMessageRoomOwner.getMessage().getBody());
            MessageContainer message3 = handler2.getCompletableFuture(0);
            assertThat(message3.getMessage().getMethod()).isEqualTo(NOTIFY_LIAR_ANSWER_NEEDED);
        } else if (liarId.equals(guestId)) {
            assertThat(publicMessage1.getMessage().getBody()).isEqualTo(expectMessageGuest.getMessage().getBody());
            MessageContainer message4 = handler4.getCompletableFuture(0);
            assertThat(message4.getMessage().getMethod()).isEqualTo(NOTIFY_LIAR_ANSWER_NEEDED);
        }
    }

    private void __openLiar() throws JsonProcessingException {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(UUID.randomUUID().toString())
                .senderId(ownerId)
                .message(new MessageContainer.Message(OPEN_LIAR, null))
                .build();
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));
    }

//    @Test
//    public void 라이어정답제출했을때_맞춤() throws Exception {
//        __라이어정답제출();
//    }

    private void __라이어정답제출() throws Exception {
        //Given
        __라이어공개요청();
        gameService.cancelAnswerTimer(roomId);
        //When
        String expectKeyword = gameService.getGame(roomId).getCurrentRoundKeyword();
        __checkIfLiarAnswerIsCorrect(expectKeyword);

        MessageContainer messageFromServer = handler1.getCompletableFuture(0);
        MessageContainer messageFromServerToGuest = handler3.getCompletableFuture(0);
        LiarAnswerResponse expectResultLiarIsRoomOwner = LiarAnswerResponse.builder()
                .answer(true)
                .state(GameState.PUBLISH_SCORE)
                .keyword(gameService.getGame(roomId).getCurrentRoundKeyword())
                .build();
        MessageContainer expectMessageRoomOwner = Util.getExpectedMessageContainer(NOTIFY_LIAR_ANSWER_CORRECT, expectResultLiarIsRoomOwner);

        //Then
        assertThat(messageFromServer.getMessage()).isEqualTo(messageFromServerToGuest.getMessage());
        assertThat(messageFromServer.getMessage()).isEqualTo(expectMessageRoomOwner.getMessage());
    }

    private void __checkIfLiarAnswerIsCorrect(String keyword) throws JsonProcessingException {
        String liarId = gameService.getGame(roomId).getLiarId();
        KeywordRequest body = new KeywordRequest(keyword);
        if (liarId.equals(ownerId)) {
            MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                    .uuid(UUID.randomUUID().toString())
                    .senderId(ownerId)
                    .message(new MessageContainer.Message(CHECK_KEYWORD_CORRECT, body))
                    .build();
            stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));
        } else {
            MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                    .uuid(UUID.randomUUID().toString())
                    .senderId(sessionInfoList.get(0).guestId)
                    .message(new MessageContainer.Message(CHECK_KEYWORD_CORRECT, body))
                    .build();
            sessionInfoList.get(0).session.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));
        }
    }

    @Test
    public void 라이어가정답을말하지못하면_타임아웃이_난다() throws Exception {
        //Given
        __라이어공개요청();
        //When
        Thread.sleep(6500);

        //Then
        MessageContainer messageFromServerToOwner = handler1.getCompletableFuture(0);
        MessageContainer messageFromServerToGuest = handler3.getCompletableFuture(0);
        MessageContainer expectMessageRoomOwner = MessageContainer.messageContainerBuilder()
                .senderId(SERVER_ID)
                .message(new MessageContainer.Message(NOTIFY_LIAR_ANSWER_TIMEOUT, null))
                .build();
        assertThat(messageFromServerToOwner).isEqualTo(messageFromServerToGuest);
        assertThat(messageFromServerToOwner.getMessage()).isEqualTo(expectMessageRoomOwner.getMessage());

        messageFromServerToOwner = handler1.getCompletableFuture(0);
        LiarAnswerResponse expectResultLiarIsRoomOwner = LiarAnswerResponse.builder()
                .answer(false)
                .state(GameState.PUBLISH_SCORE)
                .keyword(gameService.getGame(roomId).getCurrentRoundKeyword())
                .build();
        expectMessageRoomOwner = Util.getExpectedMessageContainer(NOTIFY_LIAR_ANSWER_CORRECT, expectResultLiarIsRoomOwner);

        assertThat(messageFromServerToOwner.getMessage()).isEqualTo(expectMessageRoomOwner.getMessage());

        teardown();
    }

    @Test
    public void 라이어정답제출했을때_틀림() throws Exception {
        //Given
        __라이어공개요청();

        //When
        __checkIfLiarAnswerIsCorrect("");

        String liarId = gameService.getGame(roomId).getLiarId();
        MessageContainer messageFromServer= handler1.getCompletableFuture(0);
        MessageContainer messageFromServerToGuest = handler3.getCompletableFuture(0);

        LiarAnswerResponse expectResultLiarIsRoomOwner = LiarAnswerResponse.builder()
                .answer(false)
                .state(GameState.PUBLISH_SCORE)
                .keyword(gameService.getGame(roomId).getCurrentRoundKeyword())
                .build();
        MessageContainer expectMessageRoomOwner = Util.getExpectedMessageContainer(NOTIFY_LIAR_ANSWER_CORRECT, expectResultLiarIsRoomOwner);

        //Then
        assertThat(messageFromServer.getMessage()).isEqualTo(messageFromServerToGuest.getMessage());
        assertThat(messageFromServer.getMessage()).isEqualTo(expectMessageRoomOwner.getMessage());
        teardown();
    }

//    @Test
//    public void 라이어맞추고_라이어키워드맞췄을때_점수공개() throws Exception {
//        __라운드점수공개();
//    }

    private void __라운드점수공개() throws Exception {
        //Given
        __라이어정답제출();
        System.out.println("라운드점수공개=====================================================================");
        //When
        __openScore();

        //Then
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler3.getCompletableFuture(0);

        ScoreboardResponse scoreBoardResponse;
        if (gameService.getGame(roomId).getLiarId().equals(ownerId)) {
            scoreBoardResponse = ScoreboardResponse.builder()
                    .scoreboard(new HashMap<String, Integer>() {{
                        put(ownerId, 3);
                        put(sessionInfoList.get(0).guestId, 0);
                    }})
                    .build();
        } else {
            scoreBoardResponse = ScoreboardResponse.builder()
                    .scoreboard(new HashMap<String, Integer>() {{
                        put(ownerId, 1);
                        put(sessionInfoList.get(0).guestId, 1);
                    }})
                    .build();
        }
        MessageContainer expectMessageOwner = Util.getExpectedMessageContainer(NOTIFY_SCORES, scoreBoardResponse);
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getMessage()).isEqualTo(expectMessageOwner.getMessage());

        MessageContainer message3 = handler1.getCompletableFuture(1);
        MessageContainer message4 = handler3.getCompletableFuture(1);
        RoundResponse expectedRoundInfo = new RoundResponse(GameState.BEFORE_ROUND, 1);
        expectMessageOwner = Util.getExpectedMessageContainer(NOTIFY_ROUND_END, expectedRoundInfo);
        assertThat(message3).isEqualTo(message4);
        assertThat(message3.getMessage()).isEqualTo(expectMessageOwner.getMessage());
    }

    private void __openScore() throws JsonProcessingException {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(UUID.randomUUID().toString())
                .senderId(ownerId)
                .message(new MessageContainer.Message(OPEN_SCORES, null))
                .build();
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));
    }

    @Test
    //@Disabled("openScore 전에 time out 나는 것으로 보임")
    public void 게임이_종료될때까지진행하면_게임종료를알린다() throws Exception {
        //Given
        __라운드점수공개();
        gameController.setTimeout(20000);
        System.out.println("1라운드종료===================================================================");

        //When
        //TODO:completableFuture 사용하기
        __sendStartRound(UUID.randomUUID().toString());
        Thread.sleep(1000);
        __sendSelectLiar(UUID.randomUUID().toString());
        Thread.sleep(1000);
        __SendOpenKeyword(UUID.randomUUID().toString());
        Thread.sleep(1000);
        GameInfo gameInfo = gameService.getGame(roomId);
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
        __checkIfLiarAnswerIsCorrect(gameService.getGame(roomId).getCurrentRoundKeyword());
        Thread.sleep(1000);

        handler1.clear();
        handler3.clear();
        __openScore();

        handler1.getCompletableFuture(0);
        handler3.getCompletableFuture(0);
        MessageContainer message3 = handler1.getCompletableFuture(1);
        MessageContainer message4 = handler3.getCompletableFuture(1);

        if (gameInfo.getState().equals(GameState.PUBLISH_RANKINGS)) {
            RoundResponse expectedRoundInfo = new RoundResponse(GameState.PUBLISH_RANKINGS, 2);
            MessageContainer expectMessage = Util.getExpectedMessageContainer(NOTIFY_ROUND_END, expectedRoundInfo);
            assertThat(message3).isEqualTo(message4);
            assertThat(message3.getMessage()).isEqualTo(expectMessage.getMessage());
        }

        //Then
        gameInfo = gameService.getGame(roomId);

        __sendPublishRankings();
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler3.getCompletableFuture(0);
        assertThat(message1).isEqualTo(message2);
        RankingsResponse expectedRankingsResponse = new RankingsResponse(gameInfo.getScoreboard().entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .map(entry -> new RankingsResponse.RankingInfo(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
        MessageContainer expectMessage = Util.getExpectedMessageContainer(NOTIFY_RANKINGS_PUBLISHED, expectedRankingsResponse);
        assertThat(message1.getMessage()).isEqualTo(expectMessage.getMessage());
    }

    private void __sendPublishRankings() throws JsonProcessingException {
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(UUID.randomUUID().toString())
                .senderId(ownerId)
                .message(new MessageContainer.Message(PUBLISH_RANKINGS, null))
                .build();
        stompSession.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));
        System.out.println("sendMessage : " + sendMessage);
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void requestTurnFinishedAndVerify(GameInfo gameInfo, TestStompHandlerChain<MessageContainer> handler1, TestStompHandlerChain<MessageContainer> handler2, StompSession session, int i)
            throws InterruptedException, ExecutionException, TimeoutException, JsonProcessingException {
        String uuid = UUID.randomUUID().toString();
        MessageContainer sendMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(gameInfo.getTurnOrder().get(1 - i))
                .message(new MessageContainer.Message(Global.REQUEST_TURN_FINISH, null))
                .build();

        session.send(String.format("%s%s", PUBLISH_PRIVATE, roomId), objectMapper.writeValueAsString(sendMessage));
        System.out.println("sendMessage : " + sendMessage);
        MessageContainer message1 = handler1.getCompletableFuture(0);
        MessageContainer message2 = handler2.getCompletableFuture(0);

        MessageContainer expectMessage = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId(SERVER_ID)
                .message(new MessageContainer.Message(NOTIFY_TURN,
                        new TurnResponse(GameState.IN_PROGRESS, gameInfo.getTurnOrder().get(i))))
                .build();
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.getSenderId()).isEqualTo(expectMessage.getSenderId());
        assertThat(message1.getMessage().getMethod()).isEqualTo(expectMessage.getMessage().getMethod());
        assertThat(message1.getMessage().getBody()).isEqualTo(expectMessage.getMessage().getBody());
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
        //public final Queue<CompletableFuture<T>> completableFuture = new ConcurrentLinkedQueue ();
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

        public void clear(){
            completableFuture.clear();
        }

        private final Class<T> tClass;

        public TestStompHandlerChain(Class<T> tClass) {
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
//            boolean ret = false;
//            try {
//                ret = completableFuture.offer(future,5,SECONDS);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
            try {
                completableFuture.put(future);
            } catch (InterruptedException e) {
                System.out.println("데이터 저장에 실패했삼 : " + headers.getDestination());
                throw new RuntimeException(e);
            }

            System.out.println(LocalDateTime.now() + ":[TestStompHandlerChain] handleFrame headers: " + headers + ", payload: " + payload + ", completableFuture.size() :" + completableFuture.size());
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
            //System.out.println("payload : " + headers);
            return this.tClass;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            System.out.println("[TestSingleStompHandler] handleFrame headers: " + headers + ", payload: " + payload);
            completableFuture.complete((T) payload);
        }
    }

    private static class Util<T> {
        public static <T extends MessageBody> MessageContainer getExpectedMessageContainer(String state, T expectedResponse) {
            return MessageContainer.messageContainerBuilder()
                    .senderId(SERVER_ID)
                    .message(new MessageContainer.Message(state, expectedResponse))
                    .build();
        }
    }
}