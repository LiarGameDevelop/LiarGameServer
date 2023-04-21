package com.game.liar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.game.liar.exception.*;
import com.game.liar.game.config.TimerInfoThreadPoolTaskScheduler;
import com.game.liar.game.domain.*;
import com.game.liar.game.dto.LiarDesignateDto;
import com.game.liar.game.dto.MessageContainer;
import com.game.liar.game.dto.request.GameSettingsRequest;
import com.game.liar.game.dto.request.KeywordRequest;
import com.game.liar.game.dto.response.*;
import com.game.liar.game.repository.GameInfoRepository;
import com.game.liar.game.service.GameService;
import com.game.liar.game.service.GameSubjectService;
import com.game.liar.game.service.MessageService;
import com.game.liar.messagequeue.TimeoutManager;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.dto.*;
import com.game.liar.room.service.RoomService;
import com.game.liar.security.dto.TokenDto;
import com.game.liar.user.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class GameServiceTest {
    @InjectMocks
    GameService gameService;
    @Mock
    RoomService roomService;
    @Mock
    GameSubjectService gameSubjectService;
    @Autowired
    GameInfoRepository gameInfoRepository;
    @Mock
    MessageService messageService;
    @Mock
    TimerInfoThreadPoolTaskScheduler taskScheduler;
    @Mock
    TimeoutManager timeoutManager;

    @BeforeEach
    public void init() {
        gameService = new GameService(roomService, gameSubjectService, gameInfoRepository, messageService, timeoutManager);
        gameService.clearGame();
    }

    private String __addRoomMember(String roomId) {
        when(roomService.addRoomMember(any())).thenReturn(EnterRoomResponse.builder()
                .room(new RoomDto(roomId, "owner", new RoomSettings(5)))
                .token(new TokenDto("", "", "", 1L))
                .user(new UserDto("tester2", "guest", "password"))
                .build(
                ));
        EnterRoomResponse room = roomService.addRoomMember(new RoomIdUserNameRequest(roomId, "tester2", "password"));
        String userId = room.getUser().getUserId();
        String username = room.getUser().getUsername();

        gameService.addMember(room.getRoom().getRoomId(), new UserDataDto(username, userId));
        return userId;
    }

    private EnterRoomResponse __createRoom() throws MaxCountException {
        when(roomService.create(any())).thenReturn(EnterRoomResponse.builder()
                .room(new RoomDto("60ff79a6-a568-11ed-b9df-0242ac120003", "owner", new RoomSettings(5)))
                .token(new TokenDto("", "", "", 1L))
                .user(new UserDto("owner", "owner", "password"))
                .build());
        EnterRoomResponse room = roomService.create(new RoomInfoRequest(5, "owner", "password"));
        String userId = room.getUser().getUserId();
        String username = room.getUser().getUsername();

        gameService.addGame(room.getRoom().getRoomId(), room.getUser().getUserId());
        System.out.println("user id : " + userId + ", user name :" + username);
        gameService.addMember(room.getRoom().getRoomId(), new UserDataDto(username, userId));
        return room;
    }

    @Test
    @DisplayName("게임카테고리얻기")
    public void verify_get_game_category() {
        //Given
        gameService.addGame("room", "owner");
        when(gameSubjectService.getAllCategory()).thenReturn(Arrays.asList("animal", "sports"));

        //When
        GameCategoryResponse result = gameService.getGameCategory("room");

        //Then
        System.out.println(result);
        assertThat(result.getCategory()).isNotEmpty();
        assertThat(result.getCategory()).contains("animal", "sports");
    }

    @Test
    @DisplayName("게임시작을안해서_게임카테고리얻기_실패Error")
    public void verify_not_get_game_category_error() {
        //Given
        //When
        //Then
        assertThrows(NotExistException.class, () -> gameService.getGameCategory("animal"));
    }

    @Test
    @DisplayName("게임시작하면 정상적으로 게임이 진행된다")
    public void verify_startGame() {
        //Given
        gameService.addGame("room", "owner");
        when(gameSubjectService.getAllCategory()).thenReturn(Arrays.asList("food", "place"));
        when(gameSubjectService.getAllSubject()).thenReturn(new HashMap<String, List<String>>() {
            {
                put("food", Arrays.asList("pizza", "tteokbokki", "bibimbab", "chicken"));
                put("place", Arrays.asList("caffee", "stadium", "school"));
            }
        });

        //When
        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Collections.singletonList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId("owner")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        GameStateResponse response = gameService.getGameState("room");
        assertThat(response.getState()).isEqualTo(GameState.BEFORE_START);
        GameInfoResponse gameInfoResponse = gameService.startGame(messageContainer, "room");
        assertThat(gameInfoResponse.getState()).isEqualTo(GameState.BEFORE_ROUND);
        assertThat(gameInfoResponse.getRoomId().getId()).isEqualTo("room");
        assertThat(gameInfoResponse.getOwnerId().getUserId()).isEqualTo("owner");
        assertThat(gameInfoResponse.getGameSettings().getRound()).isEqualTo(5);
        assertThat(gameInfoResponse.getGameSettings().getTurn()).isEqualTo(2);
        assertThat(gameInfoResponse.getCurrentRound()).isEqualTo(0);
        assertThat(gameInfoResponse.getCurrentTurn()).isEqualTo(-1);
    }

    @Test
    @DisplayName("게임시작_잘못된라운드수Error")
    public void verify_startGame_wrong_round_param_error() {
        //Given
        gameService.addGame("room", "owner");

        //When
        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(8)
                .turn(2)
                .category(Collections.singletonList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId("owner")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(NotAllowedActionException.class, () -> gameService.startGame(messageContainer, "room"));
    }

    @Test
    @DisplayName("게임시작_잘못된턴수Error")
    public void verify_startGame_wrong_turn_param_error() {
        //Given
        gameService.addGame("room", "owner");

        //When
        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(5)
                .category(Collections.singletonList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId("owner")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(NotAllowedActionException.class, () -> gameService.startGame(messageContainer, "room"));
    }

    @Test
    @DisplayName("게임시작_라운드수없음Error")
    public void verify_startRound_round_param_error() {
        //Given
        gameService.addGame("room", "owner");

        //When
        GameSettingsRequest request = GameSettingsRequest.builder()
                .turn(2)
                .category(Collections.singletonList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId("owner")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(RequiredParameterMissingException.class, () -> gameService.startGame(messageContainer, "room"));
    }

    @Test
    @DisplayName("방장아닌사람이_게임시작Error")
    public void verify_startGame_wrong_owner_request_error() throws Exception {
        //Given
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.empty());
        gameService.addGame("room", "owner");
        GameInfo gameInfo = new GameInfo(RoomId.of("room"), UserId.of("owner"));
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.of(gameInfo));

        //When
        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Collections.singletonList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId("tester2")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(NotAllowedActionException.class, () -> {
            gameService.startGame(messageContainer, "room");
        });
    }


    @Test
    @DisplayName("라운드시작")
    public void verify_startRound() {
        //Given
        gameService.addGame("room", "owner");

        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Collections.singletonList("food"))
                .build();
        __startGame("owner", "room", request);

        //when
        __startRound("owner", "room");

        //Then
        GameInfo gameInfo = gameService.getGame(RoomId.of("room"));
        assertThat(gameInfo).isNotNull();
        assertThat(gameInfo.getCurrentRound()).isEqualTo(1);
        assertThat(gameInfo.getState()).isEqualTo(GameState.SELECT_LIAR);
    }

    @Test
    @DisplayName("라운드초과Error")
    public void verify_startRound_wrong_round_error() {
        //Given
        gameService.addGame("room", "owner");
        GameInfo gameInfo = gameInfoRepository.findById(RoomId.of("room")).orElseThrow(RuntimeException::new);
        gameInfo.setGameSettings(new GameSettings(5, 2, new ArrayList<>()));
        gameInfo.nextState();
        gameInfo.initialize(new HashMap<>(), new ArrayList<>());
        //when
        for (int i = 0; i < 5; ++i)
            gameInfo.nextRound();

        //Then
        assertThrows(NotAllowedActionException.class,
                () -> gameService.startRound(
                        MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                                .senderId("owner")
                                .uuid(UUID.randomUUID().toString())
                                .build(),
                        "room"));
    }

    @Test
    @DisplayName("라이어선정 성공")
    public void verify_selectLiar_success() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        __addRoomMember(roomId);

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        //when
        GameStateResponse stateResponse = __selectLiar(roomOwnerId, roomId);
        GameInfo gameInfo = gameInfoRepository.findById(RoomId.of(roomId)).orElseThrow(RuntimeException::new);

        //Then
        assertThat(gameInfo.getLiarId()).isNotBlank();
        verify(messageService, times(2)).sendPrivateMessage(any(), any(), any(), any());
        assertThat(stateResponse.getState()).isEqualTo(GameState.OPEN_KEYWORD);
    }

    private RoundResponse __startRound(String roomOwnerId, String roomId) {
        MessageContainer messageContainer;
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        return gameService.startRound(messageContainer, roomId);
    }

    private void __startGame(String roomOwnerId, String roomId, GameSettingsRequest gameSettings) {
        when(gameSubjectService.getAllCategory()).thenReturn(Arrays.asList("food", "place", "sports", "celebrity"));
        when(gameSubjectService.getAllSubject()).thenReturn(new HashMap<String, List<String>>() {
            {
                put("food", Arrays.asList("pizza", "tteokbokki", "bibimbab", "chicken"));
                put("place", Arrays.asList("caffee", "stadium", "school"));
                put("sports", Arrays.asList("baseball", "soccer"));
                put("celebrity", Arrays.asList("IU", "GD"));
            }
        });
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", gameSettings))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, roomId);
    }

    private void __startGame(String roomOwnerId, String roomId) {
        when(gameSubjectService.getAllCategory()).thenReturn(Arrays.asList("food", "place", "sports", "celebrity"));
        when(gameSubjectService.getAllSubject()).thenReturn(new HashMap<String, List<String>>() {
            {
                put("food", Arrays.asList("pizza", "tteokbokki", "bibimbab", "chicken"));
                put("place", Arrays.asList("caffee", "stadium", "school"));
                put("sports", Arrays.asList("baseball", "soccer"));
                put("celebrity", Arrays.asList("IU", "GD"));
            }
        });

        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Collections.singletonList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, roomId);
    }

    @Test
    @DisplayName("라이어선정 요청이 방장이 아니다 Error")
    public void verify_selectLiar_not_owner_request_error() {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
        String guestId = __addRoomMember(roomId);

        __startGame(roomOwnerId, roomId);
        MessageContainer messageContainer;
        __startRound(roomOwnerId, roomId);

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .senderId(guestId)
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        MessageContainer finalMessageContainer = messageContainer;
        assertThrows(NotAllowedActionException.class, () -> {
            gameService.selectLiarAndSendIsLiar(finalMessageContainer, roomId);
        });
    }

    @Test
    @DisplayName("라이어선정 요청시 현재 state가 라이어선정 state가 아니다 Error")
    public void verify_selectLiar_wrong_state_param_error() {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));

        __startGame(roomOwnerId, roomId);
        MessageContainer messageContainer;
        __startRound(roomOwnerId, roomId);

        //when
        gameService.nextGameState(roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        MessageContainer finalMessageContainer = messageContainer;
        assertThrows(StateNotAllowedExpcetion.class, () -> {
            gameService.selectLiarAndSendIsLiar(finalMessageContainer, roomId);
        });
    }

    @Test
    @DisplayName("키워드 공개 성공")
    public void verify_openKeyword() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        //when
        __selectLiar(roomOwnerId, roomId);
        __openKeyword(roomOwnerId, roomId);

        //Then
        assertThat(gameInfo.getCurrentRoundCategory()).containsAnyOf("food", "sports", "celebrity");
        ArrayList<String> candidate = new ArrayList<>();
        for (ArrayList<String> keyword : gameInfo.getGameSettings().getSelectedByRoomOwnerCategory().values()) {
            candidate.addAll(keyword);
        }
        assertThat(gameInfo.getCurrentRoundKeyword()).containsAnyOf(candidate.toArray(new String[0]));
        System.out.println(gameInfo.getTurnOrder());
        assertThat(gameInfo.getTurnOrder()).satisfiesAnyOf(
                param -> assertThat(gameInfo.getTurnOrder()).containsExactly(roomOwnerId, guestId),
                param -> assertThat(gameInfo.getTurnOrder()).containsExactly(guestId, roomOwnerId)
        );
    }

    private GameStateResponse __selectLiar(String roomOwnerId, String roomId) {
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList("owner", "guest"));
        MessageContainer messageContainer;
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        return gameService.selectLiarAndSendIsLiar(messageContainer, roomId);
    }

    @Test
    @DisplayName("턴 알림 성공")
    public void verify_notify_turn() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        //when
        __selectLiar(roomOwnerId, roomId);
        TurnOrderResponse turnOrderResponse = __openKeyword(roomOwnerId, roomId);

        List<String> turnOrder = turnOrderResponse.getTurnOrder();
        gameService.updateTurn(UUID.randomUUID().toString(), Global.SERVER_ID, roomId);

        //Then
        GameInfo gameInfo = gameInfoRepository.findById(RoomId.of(roomId)).orElseThrow(RuntimeException::new);
        assertThat(gameInfo.getCurrentTurnId()).isEqualTo(turnOrder.get(0));
        gameService.updateTurn(UUID.randomUUID().toString(), turnOrder.get(0), roomId);
        assertThat(gameInfo.getCurrentTurnId()).isEqualTo(turnOrder.get(1));
    }

    @Test
    @DisplayName("설명종료 요청 성공")
    public void verify_notifyFindingLiarEnd() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: OPEN_KEYWORD
        //when
        gameService.nextGameState(roomId); //after: IN_PROGRESS

        //Then
        gameService.notifyFindingLiarEnd(roomId);
        assertThat(gameInfo.getCurrentTurn()).isEqualTo(-1);
        assertThat(gameInfo.getState().toString()).isEqualTo("VOTE_LIAR");
    }

    @Test
    @DisplayName("현재턴이아닌사람의_턴알림_Error")
    public void verify_notifyTurn_not_current_turn_error() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        //when
        __selectLiar(roomOwnerId, roomId);
        __openKeyword(roomOwnerId, roomId);

        List<String> turnOrder = gameInfo.getTurnOrder();

        //Then
        assertThrows(RuntimeException.class, () -> {
            gameService.updateTurn(UUID.randomUUID().toString(), turnOrder.get(0), roomId);
        });
    }

    @Test
    @DisplayName("턴 초과 요청 시 Error")
    public void verify_requestTurnFinished_exceed_turn_error() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        //when
        __selectLiar(roomOwnerId, roomId);
        __openKeyword(roomOwnerId, roomId);

        List<String> turnOrder = gameInfo.getTurnOrder();
        gameService.updateTurn(UUID.randomUUID().toString(), Global.SERVER_ID, roomId);
        gameService.updateTurn(UUID.randomUUID().toString(), turnOrder.get(0), roomId);
        gameService.updateTurn(UUID.randomUUID().toString(), turnOrder.get(1), roomId);
        gameService.updateTurn(UUID.randomUUID().toString(), turnOrder.get(0), roomId);
        gameService.updateTurn(UUID.randomUUID().toString(), turnOrder.get(1), roomId);

        //Then
        assertThrows(StateNotAllowedExpcetion.class, () -> {
            gameService.updateTurn(UUID.randomUUID().toString(), turnOrder.get(0), roomId);
        });
    }

    @Test
    @DisplayName("라이어투표 성공")
    public void verify_voteLiar() {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        MessageContainer messageContainer;
        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: OPEN_KEYWORD
        gameService.nextGameState(roomId); //after: IN_PROGRESS

        //when
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateDto(guestId)))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateDto(guestId)))
                .senderId(guestId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);

        //Then
        assertThat(gameInfo.getVoteCount()).isEqualTo(2);
        assertThat(gameInfo.getMostVotedUserIdAndCount()).isEqualTo(Collections.singletonList(new AbstractMap.SimpleEntry<>(guestId, 2L)));
    }

    @Test
    @DisplayName("모두 투표를 안했을때 라이어 후보가 없어야한다")
    public void verify_voteLiar_no_vote() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));

        MessageContainer messageContainer;
        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: OPEN_KEYWORD
        gameService.nextGameState(roomId); //after: IN_PROGRESS

        //when
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateDto("")))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateDto("")))
                .senderId(guestId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);

        //Then
        assertThat(gameInfo.getVoteCount()).isEqualTo(2);
        assertThat(gameInfo.getMostVotedUserIdAndCount()).isEqualTo(null);
        assertThat(gameInfo.getState()).isEqualTo(GameState.VOTE_LIAR);
    }

    @Test
    public void 라이어투표_동표() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: OPEN_KEYWORD
        gameService.nextGameState(roomId); //after: IN_PROGRESS

        //when
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateDto(guestId)))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();

        gameService.voteLiar(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateDto(roomOwnerId)))
                .senderId(guestId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);

        //Then
        assertThat(gameInfo.getVoteCount()).isEqualTo(2);
        assertThat(gameInfo.getMostVotedUserIdAndCount()).containsAll(Arrays.asList(new AbstractMap.SimpleEntry<>(guestId, 1L), new AbstractMap.SimpleEntry<>(roomOwnerId, 1L)));
    }

    @Test
    public void 라이어투표_StateError() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: OPEN_KEYWORD
        gameService.nextGameState(roomId); //after: IN_PROGRESS

        //when
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        gameService.nextGameState(roomId); //after: VOTE_LIAR


        //Then
        assertThrows(StateNotAllowedExpcetion.class, () -> {
            gameService.voteLiar(MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR,
                            new LiarDesignateDto(guestId)))
                    .senderId(roomOwnerId)
                    .uuid(UUID.randomUUID().toString())
                    .build(), roomId);
        });
    }

    @Test
    public void 라이어투표_재투표Error() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: OPEN_KEYWORD
        gameService.nextGameState(roomId); //after: IN_PROGRESS

        //when
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateDto(guestId)))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);

        //Then
        assertThrows(NotAllowedActionException.class, () -> {
            gameService.voteLiar(MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR,
                            new LiarDesignateDto(roomOwnerId)))
                    .senderId(roomOwnerId)
                    .uuid(UUID.randomUUID().toString())
                    .build(), roomId);
        });
    }

    @Test
    public void 라이어발표() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        __selectLiar(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: IN_PROGRESS
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateDto(guestId)))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateDto(guestId)))
                .senderId(guestId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);
        gameService.nextGameState(roomId);

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_LIAR, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        OpenLiarResponse result = gameService.openLiar(messageContainer, roomId);

        assertThat(result.getState()).isEqualTo(GameState.LIAR_ANSWER);
        assertThat(result.getLiar()).satisfiesAnyOf(
                param -> {
                    assertThat(result.getLiar()).isEqualTo(roomOwnerId);
                    assertThat(result.isMatchLiar()).isEqualTo(false);
                },
                param -> {
                    assertThat(result.getLiar()).isEqualTo(guestId);
                    assertThat(result.isMatchLiar()).isEqualTo(true);
                }
        );
        gameService.resetLiarInfo(roomId);
        assertThat(gameInfo.getLiarId()).isNull();
    }

    @Test
    public void 라이어발표_방장아닌사람의요청Error() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        __selectLiar(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: IN_PROGRESS
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId, roomOwnerId, guestId);
        __voteLiar(roomId, guestId, guestId);
        gameService.getMostVoted(roomId);
        gameService.nextGameState(roomId);

        //when, then
        assertThrows(NotAllowedActionException.class, () -> gameService.openLiar(MessageContainer.builder(new MessageContainer.Message(Global.OPEN_LIAR, null))
                .senderId(guestId)
                .uuid(UUID.randomUUID().toString())
                .build(), roomId));
    }

    private void __voteLiar(String roomId, String senderId, String designatedId) throws JsonProcessingException {
        MessageContainer messageContainer;
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateDto(designatedId)))
                .senderId(senderId)
                .uuid(UUID.randomUUID().toString())
                .build();
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(designatedId));
        gameService.voteLiar(messageContainer, roomId);

    }

    @Test
    public void 라이어발표_StateError() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);
        __selectLiar(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: IN_PROGRESS
        gameService.nextGameState(roomId); //after: VOTE_LIAR

        __voteLiar(roomId, roomOwnerId, guestId);
        __voteLiar(roomId, guestId, guestId);
        gameService.getMostVoted(roomId);
        gameService.nextGameState(roomId);
        gameService.nextGameState(roomId);

        //when, then
        assertThrows(StateNotAllowedExpcetion.class, () -> gameService.openLiar(MessageContainer.builder(new MessageContainer.Message(Global.OPEN_LIAR, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build(), roomId));
    }

    @Test
    public void 라이어가_정답맞췄는지요청() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));

        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        MessageContainer messageContainer;
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);

        __selectLiar(roomOwnerId, roomId);

        __openKeyword(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId, roomOwnerId, guestId);
        __voteLiar(roomId, guestId, guestId);
        gameService.nextGameState(roomId);
        __openLiar(roomOwnerId, roomId);
        //when
        String liarId = gameInfo.getLiarId();
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.LIAR_ANSWER,
                        new KeywordRequest(gameInfo.getCurrentRoundKeyword())))
                .senderId(liarId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.checkKeywordCorrectAndSendResult(messageContainer, roomId);

        //then
        assertThat(gameInfo.isLiarAnswer()).isEqualTo(true);
    }

    private TurnOrderResponse __openKeyword(String roomOwnerId, String roomId) {
        MessageContainer messageContainer;
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_KEYWORD, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        TurnOrderResponse turnOrder = gameService.openAndSendKeyword(messageContainer, roomId);
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.of(gameInfo));
        return turnOrder;
    }

    @Test
    public void 라이어가_정답요청했으나_정답틀림() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));

        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);
        __selectLiar(roomOwnerId, roomId);
        __openKeyword(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId, roomOwnerId, guestId);
        __voteLiar(roomId, guestId, guestId);
        gameService.nextGameState(roomId);
        __openLiar(roomOwnerId, roomId);
        String liarId = gameInfo.getLiarId();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.LIAR_ANSWER,
                        new KeywordRequest()))
                .senderId(liarId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.checkKeywordCorrectAndSendResult(messageContainer, roomId);

        //then
        assertThat(gameInfo.isLiarAnswer()).isEqualTo(false);
    }

    @Test
    public void 라운드종료후_점수확인_게스트1라이어_라이어맞춤_라이어정답맞춤() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);

        gameInfo.selectLiar(guestId);
        gameService.nextGameState(roomId);
        __openKeyword(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId, roomOwnerId, guestId);
        __voteLiar(roomId, guestId, guestId);
        gameService.nextGameState(roomId);
        __openLiar(roomOwnerId, roomId);
        String liarId = gameInfo.getLiarId();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.LIAR_ANSWER,
                        new KeywordRequest(gameInfo.getCurrentRoundKeyword())))
                .senderId(liarId)
                .uuid(UUID.randomUUID().toString())
                .build();

        gameService.checkKeywordCorrectAndSendResult(messageContainer, roomId);
        ScoreboardResponse scoreBoardResponse = __getScores(roomOwnerId, roomId);

        //then
        ScoreboardResponse expectedScores = ScoreboardResponse.builder()
                .scoreboard(new HashMap<String, Integer>() {{
                    put(roomOwnerId, 1);
                    put(guestId, 1);
                }})
                .build();
        assertThat(scoreBoardResponse.getScoreboard()).isEqualTo(expectedScores.getScoreboard());
    }

    @Test
    public void 라운드종료후_점수확인_게스트1라이어_라이어맞춤_라이어정답틀림() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);

        gameInfo.selectLiar(guestId);
        gameService.nextGameState(roomId);
        __openKeyword(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId, roomOwnerId, guestId);
        __voteLiar(roomId, guestId, guestId);
        gameService.nextGameState(roomId);
        __openLiar(roomOwnerId, roomId);
        String liarId = gameInfo.getLiarId();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.LIAR_ANSWER,
                        new KeywordRequest("")))
                .senderId(liarId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.checkKeywordCorrectAndSendResult(messageContainer, roomId);
        ScoreboardResponse scoreBoardResponse = __getScores(roomOwnerId, roomId);

        //then
        ScoreboardResponse expectedScores = ScoreboardResponse.builder()
                .scoreboard(new HashMap<String, Integer>() {{
                    put(roomOwnerId, 1);
                    put(guestId, 0);
                }})
                .build();
        assertThat(scoreBoardResponse.getScoreboard()).isEqualTo(expectedScores.getScoreboard());
    }

    @Test
    public void 라운드종료후_점수확인_게스트1라이어_라이어틀림_라이어정답맞춤() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);

        gameInfo.selectLiar(guestId);
        gameService.nextGameState(roomId);
        __openKeyword(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId, roomOwnerId, roomOwnerId);
        __voteLiar(roomId, guestId, roomOwnerId);
        gameService.nextGameState(roomId);
        __openLiar(roomOwnerId, roomId);
        String liarId = gameInfo.getLiarId();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.LIAR_ANSWER,
                        new KeywordRequest(gameInfo.getCurrentRoundKeyword())))
                .senderId(liarId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.checkKeywordCorrectAndSendResult(messageContainer, roomId);
        ScoreboardResponse scoreBoardResponse = __getScores(roomOwnerId, roomId);

        //then
        ScoreboardResponse expectedScores = ScoreboardResponse.builder()
                .scoreboard(new HashMap<String, Integer>() {{
                    put(roomOwnerId, 0);
                    put(guestId, 3);
                }})
                .build();
        assertThat(scoreBoardResponse.getScoreboard()).isEqualTo(expectedScores.getScoreboard());
    }

    @Test
    public void 라운드종료후_점수확인_게스트1라이어_라이어틀림_라이어정답틀림() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);

        gameInfo.selectLiar(guestId);
        gameService.nextGameState(roomId);
        __openKeyword(roomOwnerId, roomId);
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId, roomOwnerId, roomOwnerId);
        __voteLiar(roomId, guestId, roomOwnerId);
        gameService.nextGameState(roomId);
        __openLiar(roomOwnerId, roomId);
        String liarId = gameInfo.getLiarId();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.LIAR_ANSWER,
                        (new KeywordRequest(""))))
                .senderId(liarId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.checkKeywordCorrectAndSendResult(messageContainer, roomId);
        ScoreboardResponse scoreBoardResponse = __getScores(roomOwnerId, roomId);

        //then
        ScoreboardResponse expectedScores = ScoreboardResponse.builder()
                .scoreboard(new HashMap<String, Integer>() {{
                    put(roomOwnerId, 0);
                    put(guestId, 2);
                }})
                .build();
        assertThat(scoreBoardResponse.getScoreboard()).isEqualTo(expectedScores.getScoreboard());
    }

    private void __openLiar(String roomOwnerId, String roomId) {
        MessageContainer messageContainer;
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_LIAR, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.openLiar(messageContainer, roomId);
    }

    private ScoreboardResponse __getScores(String roomOwnerId, String roomId) {
        MessageContainer messageContainer;
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_SCORES,
                        null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        ScoreboardResponse scoreBoardResponse = gameService.notifyScores(messageContainer, roomId);
        return scoreBoardResponse;
    }

    @Test
    public void 라운드종료요청했을때_전체라운드가끝나지않았으면_라운드시작요청으로다시돌아간다() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);

        gameInfo.selectLiar(guestId);
        gameService.nextGameState(roomId);
        __openKeyword(roomOwnerId, roomId);
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId, roomOwnerId, guestId);
        __voteLiar(roomId, guestId, guestId);
        gameService.nextGameState(roomId);
        //when
        __openLiar(roomOwnerId, roomId);
        String liarId = gameInfo.getLiarId();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.LIAR_ANSWER,
                        (new KeywordRequest(""))))
                .senderId(liarId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.checkKeywordCorrectAndSendResult(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_SCORES,
                        null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.notifyScores(messageContainer, roomId);

        //then
        gameService.notifyRoundEnd(roomId);
        assertThat(gameInfo.getState()).isEqualTo(GameState.BEFORE_ROUND);
    }

    @Test
    public void 라운드종료요청했을때_전체라운드가끝났으면_게임이종료된다() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);

        for (int i = 0; i < 5; ++i) {
            //when
            __startRound(roomOwnerId, roomId);

            gameInfo.selectLiar(guestId);
            gameService.nextGameState(roomId);
            __openKeyword(roomOwnerId, roomId);

            gameService.nextGameState(roomId); //after: VOTE_LIAR
            __voteLiar(roomId, roomOwnerId, guestId);
            __voteLiar(roomId, guestId, guestId);
            gameService.nextGameState(roomId);
            __openLiar(roomOwnerId, roomId);
            String liarId = gameInfo.getLiarId();
            MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.LIAR_ANSWER,
                            new KeywordRequest("")))
                    .senderId(liarId)
                    .uuid(UUID.randomUUID().toString())
                    .build();
            gameService.checkKeywordCorrectAndSendResult(messageContainer, roomId);
            messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_SCORES,
                            null))
                    .senderId(roomOwnerId)
                    .uuid(UUID.randomUUID().toString())
                    .build();
            gameService.notifyScores(messageContainer, roomId);
            gameService.notifyRoundEnd(roomId);
            gameInfo.resetVoteResult();
        }
        //then
        assertThat(gameInfo.getState()).isEqualTo(GameState.PUBLISH_RANKINGS);
    }

    @Test
    @DisplayName("순위알림요청이오면 순위를알려준다")
    public void verify_notify_rankings() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom();
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId);
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);

        for (int i = 0; i < 5; ++i) {
            //when
            __startRound(roomOwnerId, roomId);

            gameInfo.selectLiar(guestId);
            gameService.nextGameState(roomId);
            __openKeyword(roomOwnerId, roomId);

            gameService.nextGameState(roomId); //after: VOTE_LIAR
            __voteLiar(roomId, roomOwnerId, guestId);
            __voteLiar(roomId, guestId, guestId);
            gameService.nextGameState(roomId);
            __openLiar(roomOwnerId, roomId);
            String liarId = gameInfo.getLiarId();
            MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.LIAR_ANSWER,
                            new KeywordRequest("")))
                    .senderId(liarId)
                    .uuid(UUID.randomUUID().toString())
                    .build();
            gameService.checkKeywordCorrectAndSendResult(messageContainer, roomId);
            messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_SCORES,
                            null))
                    .senderId(roomOwnerId)
                    .uuid(UUID.randomUUID().toString())
                    .build();
            gameService.notifyScores(messageContainer, roomId);
            gameService.notifyRoundEnd(roomId);
            gameInfo.resetVoteResult();
        }

        //when
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.PUBLISH_RANKINGS,
                        null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        RankingsResponse result = gameService.publishRankings(messageContainer, roomId);

        //then
        Integer roomOwnerScore = gameInfo.getScoreboard().get(roomOwnerId);
        Integer guestScore = gameInfo.getScoreboard().get(guestId);
        if (roomOwnerScore > guestScore) {
            assertThat(result).isEqualTo(new RankingsResponse(Arrays.asList(
                    new RankingsResponse.RankingInfo(roomOwnerId, roomOwnerScore),
                    new RankingsResponse.RankingInfo(guestId, guestScore)
            )));
        } else {
            assertThat(result).isEqualTo(new RankingsResponse(Arrays.asList(
                    new RankingsResponse.RankingInfo(roomOwnerId, guestScore),
                    new RankingsResponse.RankingInfo(guestId, roomOwnerScore)
            )));
        }
        gameInfo.nextState();
        assertThat(gameInfo.getState()).isEqualTo(GameState.BEFORE_START);
    }
}