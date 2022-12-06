package com.game.liar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.config.GameCategoryProperties;
import com.game.liar.domain.GameState;
import com.game.liar.domain.Global;
import com.game.liar.domain.request.MessageContainer;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.repository.RoomRepository;
import com.game.liar.utils.ApplicationContextProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@WebMvcTest({GameService.class, RoundService.class, RoomService.class, RoomRepository.class, GameCategoryProperties.class, ApplicationContextProvider.class})
class GameServiceTest {
    @Autowired
    GameService gameService;
    @Autowired
    RoundService roundService;
    ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    public void init() {
        gameService.clearGame();
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
        assertThat(result.getTurn()).isEqualTo(0);
        assertThat(result.getCategory()).containsKey("food");
        assertThat(result.getCategory()).containsValues(Arrays.asList("pizza","tteokbokki","bibimbab","chicken"));
        assertThat(result.getCategory()).doesNotContainKey("sports");

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
    public void 라운드시작 () throws Exception{
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
        messageContainer = MessageContainer.builder(new MessageContainer.Message("startRound", null))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer,"room1");

        //Then
        assertThat(gameInfo).isNotNull();
        assertThat(gameInfo.getRound()).isEqualTo(1);
        assertThat(gameInfo.getState()).isEqualTo(GameState.SELECT_LIAR);
    }

    @Test
    public void 라운드초과Error () throws Exception{
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
        messageContainer = MessageContainer.builder(new MessageContainer.Message("startRound", null))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer,"room1");

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message("startRound", null))
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
        assertThrows(NotAllowedActionException.class,()->gameService.startRound(finalMessageContainer,"room1"));
    }

    @Test
    public GameInfo 라이어선정 () throws Exception{
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
        messageContainer = MessageContainer.builder(new MessageContainer.Message("startRound", null))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer,"room1");

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.selectLiar(messageContainer,"room1");

        //Then
        assertThat(gameInfo).isNotNull();
        assertThat(gameInfo.getRound()).isEqualTo(1);
        assertThat(gameInfo.getState()).isEqualTo(GameState.SELECT_LIAR);
        assertThat(gameInfo.getLiarId()).isNotBlank();

        return gameInfo;
    }

    @Test
    public GameInfo 키워드공개 () throws Exception{
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
        messageContainer = MessageContainer.builder(new MessageContainer.Message("startRound", null))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.startRound(messageContainer,"room1");

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.SELECT_LIAR, null))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();
        gameService.selectLiar(messageContainer,"room1");

        //when
        messageContainer = MessageContainer.builder(new MessageContainer.Message(Global.OPEN_KEYWORD, null))
                .senderId("tester1")
                .uuid(UUID.randomUUID().toString())
                .build();

        //Then

        return gameInfo;
    }
}