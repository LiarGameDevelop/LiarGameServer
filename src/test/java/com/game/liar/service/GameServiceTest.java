package com.game.liar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.config.GameCategoryProperties;
import com.game.liar.domain.GameState;
import com.game.liar.domain.Global;
import com.game.liar.domain.Room;
import com.game.liar.domain.User;
import com.game.liar.domain.request.MessageContainer;
import com.game.liar.domain.request.RoomIdAndUserIdRequest;
import com.game.liar.domain.request.RoomInfoRequest;
import com.game.liar.exception.MaxCountException;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.exception.StateNotAllowedExpcetion;
import com.game.liar.repository.RoomRepository;
import com.game.liar.utils.ApplicationContextProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@WebMvcTest({GameService.class, RoomService.class, RoomRepository.class, RoundService.class, GameCategoryProperties.class, ApplicationContextProvider.class})
class GameServiceTest {
    @Autowired
    GameService gameService;
    @Autowired
    RoundService roundService;
    ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    RoomRepository roomRepository;

    @AfterEach
    public void init() {
        gameService.clearGame();
        roomRepository.clearRooms();
    }

    private String 방인원추가(String roomId, String name) throws MaxCountException {
        Room room = roomRepository.addRoomMember(new RoomIdAndUserIdRequest(roomId, name));
        String userId = room.getUserList().stream().filter(user -> user.getUsername().equals(name)).findFirst().get().getUserId();
        String username = room.getUserList().stream().filter(user -> user.getUsername().equals(name)).findFirst().get().getUsername();
        gameService.addMember(room.getRoomId(), new User(username, userId));
        return userId;
    }

    private Room 방생성(String name) throws MaxCountException {
        Room room = roomRepository.create(new RoomInfoRequest(5, name));
        String userId = room.getUserList().stream().filter(user -> user.getUsername().equals(name)).findFirst().get().getUserId();
        String username = room.getUserList().stream().filter(user -> user.getUsername().equals(name)).findFirst().get().getUsername();
        GameInfo gameInfo = gameService.addGame(room.getRoomId(), room.getOwnerId());
        System.out.println("user id : " + userId + ", user name :" + username);

        gameService.addMember(room.getRoomId(), new User(username, userId));
        return room;
    }

    @Test
    public void 게임시작() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");


        //When
        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        GameInfo result = gameService.getGameState("room1");
        assertThat(result.getState()).isEqualTo(GameState.BEFORE_START);
        result = gameService.startGame(messageContainer, "room1");
        assertThat(result.getState()).isEqualTo(GameState.BEFORE_ROUND);
        assertThat(result.getRoomId()).isEqualTo("room1");
        assertThat(result.getOwnerId()).isEqualTo("tester1");
        assertThat(result.getGameSettings().getRound()).isEqualTo(5);
        assertThat(result.getGameSettings().getTurn()).isEqualTo(2);
        assertThat(result.getRound()).isEqualTo(0);
        assertThat(result.getTurn()).isEqualTo(-1);
        assertThat(result.getSelectedByRoomOwnerCategory()).containsKey("food");
        assertThat(result.getSelectedByRoomOwnerCategory()).containsValues(Arrays.asList("pizza", "tteokbokki", "bibimbab", "chicken"));
        assertThat(result.getSelectedByRoomOwnerCategory()).doesNotContainKey("sports");

    }

    @Test
    public void 게임시작_잘못된라운드수Error() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");

        //When
        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(8)
                .turn(2)
                .category(Arrays.asList("food"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(NotAllowedActionException.class, () -> {
            gameService.startGame(messageContainer, "room1");
        });
    }

    @Test
    public void 게임시작_잘못된턴수Error() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");

        //When
        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(3)
                .turn(5)
                .category(Arrays.asList("food"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(NotAllowedActionException.class, () -> {
            gameService.startGame(messageContainer, "room1");
        });
    }

    @Test
    public void 게임시작_라운드수없음Error() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");

        //When
        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .turn(5)
                .category(Arrays.asList("food", "sports"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(NullPointerException.class, () -> {
            gameService.startGame(messageContainer, "room1");
        });
    }

    @Test
    public void 방장아닌사람이_게임시작Error() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");

        //When
        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId("tester2")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(NotAllowedActionException.class, () -> {
            gameService.startGame(messageContainer, "room1");
        });
    }


    @Test
    public void 라운드시작() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");
        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, "room1");

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer, "room1");

        //Then
        assertThat(gameInfo).isNotNull();
        assertThat(gameInfo.getRound()).isEqualTo(1);
        assertThat(gameInfo.getState()).isEqualTo(GameState.SELECT_LIAR);
    }

    @Test
    public void 라운드초과Error() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");
        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, "room1");
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer, "room1");

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();
        gameInfo.nextRound();
        gameInfo.nextRound();
        gameInfo.nextRound();
        gameInfo.nextRound();
        gameInfo.nextRound();

        //Then
        MessageContainer finalMessageContainer = messageContainer;
        assertThrows(StateNotAllowedExpcetion.class, () -> gameService.startRound(finalMessageContainer, "room1"));
    }

    @Test
    public void 라이어선정() throws Exception {
        //Given
        Room room = 방생성("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        GameInfo gameInfo = gameService.getGame(roomId);
        String guestId = 방인원추가(roomId, "tester2");

        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer, roomId);

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.selectLiar(messageContainer, roomId);

        //Then
        assertThat(gameInfo.getRound()).isEqualTo(1);
        assertThat(gameInfo.getState()).isEqualTo(GameState.OPEN_KEYWORD);
        assertThat(gameInfo.getLiarId()).containsAnyOf(roomOwnerId, guestId);
    }

    @Test
    public void 라이어선정_방장아님Error() throws Exception {
        //Given
        Room room = 방생성("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        GameInfo gameInfo = gameService.getGame(roomId);
        String guestId = 방인원추가(roomId, "tester2");

        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer, roomId);

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
        Room room = 방생성("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        방인원추가(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_GAME, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer, roomId);

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
        Room room = 방생성("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = 방인원추가(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_GAME, objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer, roomId);

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.selectLiar(messageContainer, roomId);

        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_KEYWORD, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.openKeyword(messageContainer, roomId);

        //Then
        assertThat(gameInfo.getCurrentRoundCategory()).containsAnyOf("food", "sports", "celebrity");
        List<String> candidate = new ArrayList<>();
        for (List<String> val : gameInfo.getSelectedByRoomOwnerCategory().values()) {
            candidate.addAll(val);
        }
        assertThat(gameInfo.getCurrentRoundKeyword()).containsAnyOf(candidate.toArray(new String[0]));
        System.out.println(gameInfo.getTurnOrder());
        assertThat(gameInfo.getTurnOrder()).satisfiesAnyOf(
                param -> assertThat(gameInfo.getTurnOrder()).containsExactly(roomOwnerId, guestId),
                param -> assertThat(gameInfo.getTurnOrder()).containsExactly(guestId, roomOwnerId)
        );
    }

    @Test
    public void 턴알림() throws Exception {
        //Given
        Room room = 방생성("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = 방인원추가(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_GAME, objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer, roomId);

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.selectLiar(messageContainer, roomId);

        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_KEYWORD, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.openKeyword(messageContainer, roomId);

        List<String> turnOrder = gameInfo.getTurnOrder();
        gameService.updateTurn(Global.SERVER_ID, roomId);

        //Then
        assertThat(gameInfo.getCurrentTurnId()).isEqualTo(turnOrder.get(0));
        gameService.updateTurn(turnOrder.get(0), roomId);
        assertThat(gameInfo.getCurrentTurnId()).isEqualTo(turnOrder.get(1));
    }

    @Test
    public void 라운드종료() throws Exception {
        //Given
        Room room = 방생성("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = 방인원추가(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_GAME, objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer, roomId);

        gameService.nextGameState(roomId); //after: OPEN_KEYWORD
        //when
        gameService.nextGameState(roomId); //after: IN_PROGRESS

        //Then
        int currentRound = gameInfo.getRound();
        gameService.notifyRoundEnd(roomId);
        assertThat(gameInfo.getRound()).isEqualTo(currentRound + 1);
        assertThat(gameInfo.getTurn()).isEqualTo(-1);
        assertThat(gameInfo.getState().toString()).isEqualTo("VOTE_LIAR");
    }

    @Test
    public void 현재턴이아닌사람의_턴알림_Error() throws Exception {
        //Given
        Room room = 방생성("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = 방인원추가(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_GAME, objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer, roomId);

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.selectLiar(messageContainer, roomId);

        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_KEYWORD, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.openKeyword(messageContainer, roomId);

        List<String> turnOrder = gameInfo.getTurnOrder();

        //Then
        assertThrows(RuntimeException.class, () -> {
            gameService.updateTurn(turnOrder.get(0), roomId);
        });
    }

    @Test
    public void 턴초과_Error() throws Exception {
        //Given
        Room room = 방생성("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = 방인원추가(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        gameInfo.setGameSettings(GameInfo.GameSettings.gameSettingsBuilder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build());
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_GAME, objectMapper.writeValueAsString(gameInfo.getGameSettings())))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, roomId);
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer, roomId);

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.selectLiar(messageContainer, roomId);

        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_KEYWORD, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.openKeyword(messageContainer, roomId);

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
}