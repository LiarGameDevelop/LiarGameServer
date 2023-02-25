package com.game.liar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.game.liar.exception.*;
import com.game.liar.game.domain.GameInfo;
import com.game.liar.game.domain.GameState;
import com.game.liar.game.domain.Global;
import com.game.liar.game.domain.RoomSettings;
import com.game.liar.game.dto.MessageContainer;
import com.game.liar.game.dto.request.GameSettingsRequest;
import com.game.liar.game.dto.request.KeywordRequest;
import com.game.liar.game.dto.request.LiarDesignateRequest;
import com.game.liar.game.dto.response.GameCategoryResponse;
import com.game.liar.game.dto.response.OpenLiarResponse;
import com.game.liar.game.dto.response.RankingsResponse;
import com.game.liar.game.dto.response.ScoreboardResponse;
import com.game.liar.game.repository.GameInfoRepository;
import com.game.liar.game.service.GameService;
import com.game.liar.game.service.GameSubjectService;
import com.game.liar.room.dto.*;
import com.game.liar.room.service.RoomService;
import com.game.liar.security.dto.TokenDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Disabled
class GameServiceTest {
    @InjectMocks
    GameService gameService;
    @Mock
    RoomService roomService;
    @Mock
    GameSubjectService gameSubjectService;
    @Autowired
    GameInfoRepository gameInfoRepository;

    @BeforeEach
    public void init() {
        gameService = new GameService(roomService, gameSubjectService, gameInfoRepository);
        gameService.clearGame();
    }

    private String __addRoomMember(String roomId, String name) throws MaxCountException {
        when(roomService.addRoomMember(any())).thenReturn(EnterRoomResponse.builder()
                .room(new RoomDto(roomId, "owner", new RoomSettings(5)))
                .token(new TokenDto("", "", "", 1L))
                .user(new UserDto(name, "guest", "password"))
                .build(
                ));
        EnterRoomResponse room = roomService.addRoomMember(new RoomIdUserNameRequest(roomId, name, "password"));
        String userId = room.getUser().getUserId();
        String username = room.getUser().getUsername();

        GameInfo gameInfo = new GameInfo(roomId, "owner");
        gameService.addMember(room.getRoom().getRoomId(), new UserDataDto(username, userId));
        return userId;
    }

    private EnterRoomResponse __createRoom(String name) throws MaxCountException {
        when(roomService.create(any())).thenReturn(EnterRoomResponse.builder()
                .room(new RoomDto("60ff79a6-a568-11ed-b9df-0242ac120003", name, new RoomSettings(5)))
                .token(new TokenDto("", "", "", 1L))
                .user(new UserDto(name, "owner", "password"))
                .build());
        EnterRoomResponse room = roomService.create(new RoomInfoRequest(5, name, "password"));
        String userId = room.getUser().getUserId();
        String username = room.getUser().getUsername();

        GameInfo gameInfo = gameService.addGame(room.getRoom().getRoomId(), room.getUser().getUserId());

        System.out.println("user id : " + userId + ", user name :" + username);

        gameService.addMember(room.getRoom().getRoomId(), new UserDataDto(username, userId));
        return room;
    }

    @Test
    public void 게임카테고리얻기() throws Exception {
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
    public void 게임시작을안해서_게임카테고리얻기_실패Error() throws Exception {
        //Given
        //When
        //Then
        assertThrows(NotExistException.class, () -> gameService.getGameCategory("animal"));
    }

    @Test
    public void 게임시작() throws Exception {
        //Given
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.empty());
        gameService.addGame("room", "owner");
        GameInfo gameInfo = new GameInfo("room", "owner");
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.of(gameInfo));

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
                .category(Arrays.asList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId("owner")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        GameInfo result = gameService.getGameState("room");
        assertThat(result.getState()).isEqualTo(GameState.BEFORE_START);
        result = gameService.startGame(messageContainer, "room");
        assertThat(result.getState()).isEqualTo(GameState.BEFORE_ROUND);
        assertThat(result.getRoomId()).isEqualTo("room");
        assertThat(result.getOwnerId()).isEqualTo("owner");
        assertThat(result.getGameSettings().getRound()).isEqualTo(5);
        assertThat(result.getGameSettings().getTurn()).isEqualTo(2);
        assertThat(result.getCurrentRound()).isEqualTo(0);
        assertThat(result.getCurrentTurn()).isEqualTo(-1);
        assertThat(result.getGameSettings().getSelectedByRoomOwnerCategory()).containsKey("food");
        assertThat(result.getGameSettings().getSelectedByRoomOwnerCategory())
                .containsValues(new ArrayList<String>() {{
                    add("pizza");
                    add("tteokbokki");
                    add("bibimbab");
                    add("chicken");
                }});
        assertThat(result.getGameSettings().getSelectedByRoomOwnerCategory()).doesNotContainKey("place");
    }

    @Test
    public void 게임시작_잘못된라운드수Error() throws Exception {
        //Given
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.empty());
        gameService.addGame("room", "owner");
        GameInfo gameInfo = new GameInfo("room", "owner");
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.of(gameInfo));

        //When
        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(8)
                .turn(2)
                .category(Arrays.asList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId("owner")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(NotAllowedActionException.class, () -> {
            gameService.startGame(messageContainer, "room");
        });
    }

    @Test
    public void 게임시작_잘못된턴수Error() throws Exception {
        //Given
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.empty());
        gameService.addGame("room", "owner");
        GameInfo gameInfo = new GameInfo("room", "owner");
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.of(gameInfo));

        //When
        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(5)
                .category(Arrays.asList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId("owner")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(NotAllowedActionException.class, () -> {
            gameService.startGame(messageContainer, "room");
        });
    }

    @Test
    public void 게임시작_라운드수없음Error() throws Exception {
        //Given
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.empty());
        gameService.addGame("room", "owner");
        GameInfo gameInfo = new GameInfo("room", "owner");
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.of(gameInfo));

        //When
        GameSettingsRequest request = GameSettingsRequest.builder()
                .turn(2)
                .category(Arrays.asList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId("owner")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(RequiredParameterMissingException.class, () -> {
            gameService.startGame(messageContainer, "room");
        });
    }

    @Test
    public void 방장아닌사람이_게임시작Error() throws Exception {
        //Given
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.empty());
        gameService.addGame("room", "owner");
        GameInfo gameInfo = new GameInfo("room", "owner");
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.of(gameInfo));

        //When
        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food"))
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
    public void 라운드시작() throws Exception {
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
        GameInfo gameInfo = gameService.getGame("room");
        assertThat(gameInfo).isNotNull();
        assertThat(gameInfo.getCurrentRound()).isEqualTo(1);
        assertThat(gameInfo.getState()).isEqualTo(GameState.SELECT_LIAR);
    }

    @Test
    public void 라운드초과Error() throws Exception {
        //Given
        gameService.addGame("room", "owner");

        int round = 5;
        MessageContainer messageContainer;
        GameSettingsRequest request = GameSettingsRequest.builder()
                .round(round)
                .turn(2)
                .category(Arrays.asList("food"))
                .build();
        __startGame("owner", "room", request);
        GameInfo gameInfo = __startRound("owner", "room");

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId("owner")
                .uuid(UUID.randomUUID().toString())
                .build();
        for (int i = 0; i < 5; ++i)
            gameInfo.nextRound();

        //Then
        MessageContainer finalMessageContainer = messageContainer;
        assertThrows(StateNotAllowedExpcetion.class, () -> gameService.startRound(finalMessageContainer, "room"));
    }

    @Test
    public void 라이어선정() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        //when
        GameInfo gameInfo = __selectLiar(roomOwnerId, roomId);

        //Then
        assertThat(gameInfo.getCurrentRound()).isEqualTo(1);
        assertThat(gameInfo.getState()).isEqualTo(GameState.OPEN_KEYWORD);
        assertThat(gameInfo.getLiarId()).containsAnyOf(roomOwnerId, guestId);
    }

    private GameInfo __startRound(String roomOwnerId, String roomId) {
        MessageContainer messageContainer;
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        GameInfo gameInfo = gameService.startRound(messageContainer, roomId);
        return gameInfo;
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
    public void 라이어선정_방장아님Error() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        GameInfo gameInfo = gameService.getGame(roomId);
        String guestId = __addRoomMember(roomId, "tester2");

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
            gameService.selectLiar(finalMessageContainer, roomId);
        });
    }

    @Test
    public void 라이어선정_StateError() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

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
            gameService.selectLiar(finalMessageContainer, roomId);
        });
    }

    @Test
    public void 키워드공개() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

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

    private GameInfo __selectLiar(String roomOwnerId, String roomId) {
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList("owner", "guest"));
        MessageContainer messageContainer;
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        GameInfo gameInfo = gameService.selectLiar(messageContainer, roomId);
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.of(gameInfo));
        return gameInfo;
    }

    @Test
    public void 턴알림() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        //when
        __selectLiar(roomOwnerId, roomId);
        GameInfo gameInfo = __openKeyword(roomOwnerId, roomId);

        List<String> turnOrder = gameInfo.getTurnOrder();
        gameService.updateTurn(Global.SERVER_ID, roomId);

        //Then
        assertThat(gameInfo.getCurrentTurnId()).isEqualTo(turnOrder.get(0));
        gameService.updateTurn(turnOrder.get(0), roomId);
        assertThat(gameInfo.getCurrentTurnId()).isEqualTo(turnOrder.get(1));
    }

    @Test
    public void 설명종료() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

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
    public void 현재턴이아닌사람의_턴알림_Error() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        //when
        __selectLiar(roomOwnerId, roomId);
        __openKeyword(roomOwnerId, roomId);

        List<String> turnOrder = gameInfo.getTurnOrder();

        //Then
        assertThrows(RuntimeException.class, () -> {
            gameService.updateTurn(turnOrder.get(0), roomId);
        });
    }

    @Test
    public void 턴초과_Error() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        //when
        __selectLiar(roomOwnerId, roomId);
        __openKeyword(roomOwnerId, roomId);

        List<String> turnOrder = gameInfo.getTurnOrder();
        gameService.updateTurn(Global.SERVER_ID, roomId);
        gameService.updateTurn(turnOrder.get(0), roomId);
        gameService.updateTurn(turnOrder.get(1), roomId);
        gameService.updateTurn(turnOrder.get(0), roomId);
        gameService.updateTurn(turnOrder.get(1), roomId);

        //Then
        assertThrows(NotAllowedActionException.class, () -> {
            gameService.updateTurn(turnOrder.get(0), roomId);
        });
    }

    @Test
    public void 라이어투표() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId,guestId));

        MessageContainer messageContainer;
        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: OPEN_KEYWORD
        gameService.nextGameState(roomId); //after: IN_PROGRESS

        //when
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateRequest(guestId)))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateRequest(guestId)))
                .senderId(guestId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);

        //Then
        assertThat(gameInfo.getVoteCount()).isEqualTo(2);
        assertThat(gameInfo.getMostVotedUserIdAndCount()).isEqualTo(Arrays.asList(new AbstractMap.SimpleEntry<>(guestId, 2L)));
    }

    @Test
    public void 라이어모두투표안했을때_() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        MessageContainer messageContainer;
        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: OPEN_KEYWORD
        gameService.nextGameState(roomId); //after: IN_PROGRESS

        //when
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateRequest("")))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateRequest("")))
                .senderId(guestId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);

        //Then
        assertThat(gameInfo.getVoteCount()).isEqualTo(2);
        assertThat(gameInfo.getMostVotedUserIdAndCount()).isEqualTo(null);
    }

    @Test
    public void 라이어투표_동표() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: OPEN_KEYWORD
        gameService.nextGameState(roomId); //after: IN_PROGRESS

        //when
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateRequest(guestId)))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();

        gameService.voteLiar(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateRequest(roomOwnerId)))
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
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");

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
                            new LiarDesignateRequest(guestId)))
                    .senderId(roomOwnerId)
                    .uuid(UUID.randomUUID().toString())
                    .build(), roomId);
        });
    }

    @Test
    public void 라이어투표_재투표Error() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: OPEN_KEYWORD
        gameService.nextGameState(roomId); //after: IN_PROGRESS

        //when
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateRequest(guestId)))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);

        //Then
        assertThrows(NotAllowedActionException.class, () -> {
            gameService.voteLiar(MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR,
                            new LiarDesignateRequest(roomOwnerId)))
                    .senderId(roomOwnerId)
                    .uuid(UUID.randomUUID().toString())
                    .build(), roomId);
        });
    }

    @Test
    public void 라이어발표() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId, guestId));

        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        __selectLiar(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: IN_PROGRESS
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateRequest(guestId)))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.voteLiar(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateRequest(guestId)))
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
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

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
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.VOTE_LIAR, new LiarDesignateRequest(designatedId)))
                .senderId(senderId)
                .uuid(UUID.randomUUID().toString())
                .build();
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(designatedId));
        gameService.voteLiar(messageContainer, roomId);

    }

    @Test
    public void 라이어발표_StateError() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

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
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

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
        gameService.checkKeywordCorrect(messageContainer, roomId);

        //then
        assertThat(gameInfo.isLiarAnswer()).isEqualTo(true);
    }

    private GameInfo __openKeyword(String roomOwnerId, String roomId) {
        MessageContainer messageContainer;
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_KEYWORD, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        GameInfo gameInfo = gameService.openKeyword(messageContainer, roomId);
        //when(gameInfoRepository.findById(any())).thenReturn(Optional.of(gameInfo));
        return gameInfo;
    }

    @Test
    public void 라이어가_정답요청했으나_정답틀림() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

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
        gameService.checkKeywordCorrect(messageContainer, roomId);

        //then
        assertThat(gameInfo.isLiarAnswer()).isEqualTo(false);
    }

    @Test
    public void 라운드종료후_점수확인_게스트1라이어_라이어맞춤_라이어정답맞춤() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId,guestId));

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

        gameService.checkKeywordCorrect(messageContainer, roomId);
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
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId,guestId));

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
        gameService.checkKeywordCorrect(messageContainer, roomId);
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
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId,guestId));

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
        gameService.checkKeywordCorrect(messageContainer, roomId);
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
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId,guestId));

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
        gameService.checkKeywordCorrect(messageContainer, roomId);
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
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId,guestId));

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
        gameService.checkKeywordCorrect(messageContainer, roomId);
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
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId,guestId));

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
            gameService.checkKeywordCorrect(messageContainer, roomId);
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
    public void 순위알림요청이오면_순위를알려준다() throws Exception {
        //Given
        EnterRoomResponse room = __createRoom("owner");
        String roomOwnerId = room.getRoom().getOwnerId();
        String roomId = room.getRoom().getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        when(roomService.getUsersId(any())).thenReturn(Arrays.asList(roomOwnerId,guestId));

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
            gameService.checkKeywordCorrect(messageContainer, roomId);
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
        assertThat(gameInfo.getState()).isEqualTo(GameState.END_GAME);
    }
}