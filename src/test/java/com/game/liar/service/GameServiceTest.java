package com.game.liar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.config.GameCategoryProperties;
import com.game.liar.domain.*;
import com.game.liar.dto.MessageContainer;
import com.game.liar.dto.request.*;
import com.game.liar.dto.response.GameCategoryResponse;
import com.game.liar.dto.response.OpenLiarResponse;
import com.game.liar.dto.response.RankingsResponse;
import com.game.liar.dto.response.ScoreboardResponse;
import com.game.liar.exception.*;
import com.game.liar.repository.RoomRepository;
import com.game.liar.utils.ApplicationContextProvider;
import com.game.liar.utils.BeanUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@WebMvcTest({GameService.class, RoomService.class, RoomRepository.class, RoundService.class, GameCategoryProperties.class, ApplicationContextProvider.class})
class GameServiceTest {
    @Autowired
    GameService gameService;
    @Autowired
    RoundService roundService;
    @Autowired
    RoomRepository roomRepository;

    @AfterEach
    public void init() {
        gameService.clearGame();
        roomRepository.clearRooms();
    }

    private String __addRoomMember(String roomId, String name) throws MaxCountException {
        Room room = roomRepository.addRoomMember(new RoomIdUserNameRequest(roomId, name));
        String userId = room.getUserList().stream().filter(user -> user.getUsername().equals(name)).findFirst().get().getUserId();
        String username = room.getUserList().stream().filter(user -> user.getUsername().equals(name)).findFirst().get().getUsername();
        gameService.addMember(room.getRoomId(), new User(username, userId));
        return userId;
    }

    private Room __createRoom(String name) throws MaxCountException {
        Room room = roomRepository.create(new RoomInfoRequest(5, name));
        String userId = room.getUserList().stream().filter(user -> user.getUsername().equals(name)).findFirst().get().getUserId();
        String username = room.getUserList().stream().filter(user -> user.getUsername().equals(name)).findFirst().get().getUsername();
        GameInfo gameInfo = gameService.addGame(room.getRoomId(), room.getOwnerId());
        System.out.println("user id : " + userId + ", user name :" + username);

        gameService.addMember(room.getRoomId(), new User(username, userId));
        return room;
    }

    @Test
    public void ????????????????????????() throws Exception {
        //Given
        gameService.addGame("room1", "tester1");

        //When
        GameCategoryResponse result = gameService.getGameCategory("room1");

        //Then
        System.out.println(result);
        assertThat(result).isEqualTo(new GameCategoryResponse(new ArrayList<>(((GameCategoryProperties) BeanUtils.getBean(GameCategoryProperties.class)).getKeywords().keySet())));
    }

    @Test
    public void ????????????????????????_????????????????????????_??????Error() throws Exception {
        //Given
        //When
        //Then
        assertThrows(NotExistException.class,()->gameService.getGameCategory("room1"));
    }

    @Test
    public void ????????????() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");

        //When
        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
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
    public void ????????????_?????????????????????Error() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");

        //When
        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(8)
                .turn(2)
                .category(Arrays.asList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(NotAllowedActionException.class, () -> {
            gameService.startGame(messageContainer, "room1");
        });
    }

    @Test
    public void ????????????_???????????????Error() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");

        //When
        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(5)
                .turn(5)
                .category(Arrays.asList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(NotAllowedActionException.class, () -> {
            gameService.startGame(messageContainer, "room1");
        });
    }

    @Test
    public void ????????????_??????????????????Error() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");

        //When
        GameSettingsRequest request=GameSettingsRequest.builder()
                .turn(2)
                .category(Arrays.asList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then
        assertThrows(RequiredParameterMissingException.class, () -> {
            gameService.startGame(messageContainer, "room1");
        });
    }

    @Test
    public void ?????????????????????_????????????Error() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");

        //When
        GameSettingsRequest request=GameSettingsRequest.builder()
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
            gameService.startGame(messageContainer, "room1");
        });
    }


    @Test
    public void ???????????????() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");
        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food"))
                .build();
        __startGame("tester1", "room1", request);
        MessageContainer messageContainer;

        //when
        __startRound("tester1", "room1");

        //Then
        assertThat(gameInfo).isNotNull();
        assertThat(gameInfo.getRound()).isEqualTo(1);
        assertThat(gameInfo.getState()).isEqualTo(GameState.SELECT_LIAR);
    }

    @Test
    public void ???????????????Error() throws Exception {
        //Given
        GameInfo gameInfo = gameService.addGame("room1", "tester1");
        int round=5;
        MessageContainer messageContainer;
        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(round)
                .turn(2)
                .category(Arrays.asList("food"))
                .build();
        __startGame("tester1", "room1", request);
        __startRound("tester1", "room1");

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();
        for(int i=0;i<5;++i)
            gameInfo.nextRound();

        //Then
        MessageContainer finalMessageContainer = messageContainer;
        assertThrows(StateNotAllowedExpcetion.class, () -> gameService.startRound(finalMessageContainer, "room1"));
    }

    @Test
    public void ???????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        GameInfo gameInfo = gameService.getGame(roomId);
        String guestId = __addRoomMember(roomId, "tester2");
        
        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        //when
        __selectLiar(roomOwnerId, roomId);

        //Then
        assertThat(gameInfo.getRound()).isEqualTo(1);
        assertThat(gameInfo.getState()).isEqualTo(GameState.OPEN_KEYWORD);
        assertThat(gameInfo.getLiarId()).containsAnyOf(roomOwnerId, guestId);
    }

    private void __startRound(String roomOwnerId, String roomId) {
        MessageContainer messageContainer;
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.START_ROUND, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer, roomId);
    }

    private void __startGame(String roomOwnerId, String roomId, GameSettingsRequest gameSettings) throws JsonProcessingException {
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", gameSettings))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, roomId);
    }

    private void __startGame(String roomOwnerId, String roomId) throws JsonProcessingException {
        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food"))
                .build();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message("startGame", request))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startGame(messageContainer, roomId);
    }

    @Test
    public void ???????????????_????????????Error() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
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
    public void ???????????????_StateError() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
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
    public void ???????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        
        __startGame(roomOwnerId,roomId);
        __startRound(roomOwnerId, roomId);

        //when
        __selectLiar(roomOwnerId, roomId);
        __openKeyword(roomOwnerId, roomId);

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

    private void __selectLiar(String roomOwnerId, String roomId) {
        MessageContainer messageContainer;
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.selectLiar(messageContainer, roomId);
    }

    @Test
    public void ?????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        
        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        //when
        __selectLiar(roomOwnerId, roomId);
        __openKeyword(roomOwnerId, roomId);

        List<String> turnOrder = gameInfo.getTurnOrder();
        gameService.updateTurn(Global.SERVER_ID, roomId);

        //Then
        assertThat(gameInfo.getCurrentTurnId()).isEqualTo(turnOrder.get(0));
        gameService.updateTurn(turnOrder.get(0), roomId);
        assertThat(gameInfo.getCurrentTurnId()).isEqualTo(turnOrder.get(1));
    }

    @Test
    public void ????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        
        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: OPEN_KEYWORD
        //when
        gameService.nextGameState(roomId); //after: IN_PROGRESS

        //Then
        gameService.notifyFindingLiarEnd(roomId);
        assertThat(gameInfo.getTurn()).isEqualTo(-1);
        assertThat(gameInfo.getState().toString()).isEqualTo("VOTE_LIAR");
    }

    @Test
    public void ???????????????????????????_?????????_Error() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
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
    public void ?????????_Error() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
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
    public void ???????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        
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
    public void ?????????????????????????????????_() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
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
    public void ???????????????_??????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        
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
    public void ???????????????_StateError() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        
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
    public void ???????????????_?????????Error() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        
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
    public void ???????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        
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
    public void ???????????????_???????????????????????????Error() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
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
        gameService.voteLiar(messageContainer, roomId);
    }

    @Test
    public void ???????????????_StateError() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);
        
        __startGame(roomOwnerId, roomId);
        __startRound(roomOwnerId, roomId);
        __selectLiar(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: IN_PROGRESS
        gameService.nextGameState(roomId); //after: VOTE_LIAR

        __voteLiar(roomId,roomOwnerId,guestId);
        __voteLiar(roomId,guestId,guestId);
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
    public void ????????????_????????????????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        GameSettingsRequest request=GameSettingsRequest.builder()
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
        __voteLiar(roomId,roomOwnerId,guestId);
        __voteLiar(roomId,guestId,guestId);
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

    private void __openKeyword(String roomOwnerId, String roomId) {
        MessageContainer messageContainer;
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_KEYWORD, null))
                .senderId(roomOwnerId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.openKeyword(messageContainer, roomId);
    }

    @Test
    public void ????????????_?????????????????????_????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);
        __selectLiar(roomOwnerId, roomId);
        __openKeyword(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId,roomOwnerId,guestId);
        __voteLiar(roomId,guestId,guestId);
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
    public void ??????????????????_????????????_?????????1?????????_???????????????_?????????????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);

        gameInfo.setLiar(guestId);
        gameService.nextGameState(roomId);
        __openKeyword(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId,roomOwnerId,guestId);
        __voteLiar(roomId,guestId,guestId);
        gameService.nextGameState(roomId);
        __openLiar(roomOwnerId, roomId);
        String liarId = gameInfo.getLiarId();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.LIAR_ANSWER,
                       new KeywordRequest(gameInfo.getCurrentRoundKeyword())))
                .senderId(liarId)
                .uuid(UUID.randomUUID().toString())
                .build();

        gameService.checkKeywordCorrect(messageContainer, roomId);
        ScoreboardResponse scoreBoardResponse = __getScores(roomOwnerId,roomId);

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
    public void ??????????????????_????????????_?????????1?????????_???????????????_?????????????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);

        gameInfo.setLiar(guestId);
        gameService.nextGameState(roomId);
        __openKeyword(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId,roomOwnerId,guestId);
        __voteLiar(roomId,guestId,guestId);
        gameService.nextGameState(roomId);
        __openLiar(roomOwnerId, roomId);
        String liarId = gameInfo.getLiarId();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.LIAR_ANSWER,
                        new KeywordRequest("")))
                .senderId(liarId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.checkKeywordCorrect(messageContainer, roomId);
        ScoreboardResponse scoreBoardResponse = __getScores(roomOwnerId,roomId);

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
    public void ??????????????????_????????????_?????????1?????????_???????????????_?????????????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);

        gameInfo.setLiar(guestId);
        gameService.nextGameState(roomId);
        __openKeyword(roomOwnerId, roomId);

        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId,roomOwnerId,roomOwnerId);
        __voteLiar(roomId,guestId,roomOwnerId);
        gameService.nextGameState(roomId);
        __openLiar(roomOwnerId, roomId);
        String liarId = gameInfo.getLiarId();
        MessageContainer messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.LIAR_ANSWER,
                        new KeywordRequest(gameInfo.getCurrentRoundKeyword())))
                .senderId(liarId)
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.checkKeywordCorrect(messageContainer, roomId);
        ScoreboardResponse scoreBoardResponse = __getScores(roomOwnerId,roomId);

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
    public void ??????????????????_????????????_?????????1?????????_???????????????_?????????????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);

        gameInfo.setLiar(guestId);
        gameService.nextGameState(roomId);
        __openKeyword(roomOwnerId, roomId);
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId,roomOwnerId,roomOwnerId);
        __voteLiar(roomId,guestId,roomOwnerId);
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
    public void ??????????????????????????????_???????????????????????????????????????_?????????????????????????????????????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);
        __startRound(roomOwnerId, roomId);

        gameInfo.setLiar(guestId);
        gameService.nextGameState(roomId);
        __openKeyword(roomOwnerId, roomId);
        gameService.nextGameState(roomId); //after: VOTE_LIAR
        __voteLiar(roomId,roomOwnerId,guestId);
        __voteLiar(roomId,guestId,guestId);
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
    public void ??????????????????????????????_??????????????????????????????_?????????????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);

        for(int i=0;i<5;++i) {
            //when
            __startRound(roomOwnerId, roomId);

            gameInfo.setLiar(guestId);
            gameService.nextGameState(roomId);
            __openKeyword(roomOwnerId, roomId);

            gameService.nextGameState(roomId); //after: VOTE_LIAR
            __voteLiar(roomId,roomOwnerId,guestId);
            __voteLiar(roomId,guestId,guestId);
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
    public void ???????????????????????????_?????????????????????() throws Exception {
        //Given
        Room room = __createRoom("tester1");
        String roomOwnerId = room.getOwnerId();
        String roomId = room.getRoomId();
        String guestId = __addRoomMember(roomId, "tester2");
        GameInfo gameInfo = gameService.getGame(roomId);

        GameSettingsRequest request=GameSettingsRequest.builder()
                .round(5)
                .turn(2)
                .category(Arrays.asList("food", "sports", "celebrity"))
                .build();
        __startGame(roomOwnerId, roomId, request);

        for(int i=0;i<5;++i) {
            //when
            __startRound(roomOwnerId, roomId);

            gameInfo.setLiar(guestId);
            gameService.nextGameState(roomId);
            __openKeyword(roomOwnerId, roomId);

            gameService.nextGameState(roomId); //after: VOTE_LIAR
            __voteLiar(roomId,roomOwnerId,guestId);
            __voteLiar(roomId,guestId,guestId);
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
        RankingsResponse result = gameService.publishRankings(messageContainer,roomId);

        //then
        Integer roomOwnerScore = gameInfo.getScoreboard().get(roomOwnerId);
        Integer guestScore = gameInfo.getScoreboard().get(guestId);
        if(roomOwnerScore>guestScore) {
            assertThat(result).isEqualTo(new RankingsResponse(Arrays.asList(
                    new RankingsResponse.RankingInfo(roomOwnerId, roomOwnerScore),
                    new RankingsResponse.RankingInfo(guestId, guestScore)
            )));
        }
        else{
            assertThat(result).isEqualTo(new RankingsResponse(Arrays.asList(
                    new RankingsResponse.RankingInfo(roomOwnerId, guestScore),
                    new RankingsResponse.RankingInfo(guestId, roomOwnerScore)
            )));
        }
        gameInfo.nextState();
        assertThat(gameInfo.getState()).isEqualTo(GameState.END_GAME);
    }
}