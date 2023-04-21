package com.game.liar.game.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.exception.ErrorResult;
import com.game.liar.exception.JsonDeserializeException;
import com.game.liar.exception.LiarGameException;
import com.game.liar.game.domain.GameInfo;
import com.game.liar.game.domain.Global;
import com.game.liar.game.dto.MessageContainer;
import com.game.liar.game.dto.response.*;
import com.game.liar.game.service.GameService;
import com.game.liar.game.service.MessageService;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.dto.UserDataDto;
import com.game.liar.room.event.UserAddedEvent;
import com.game.liar.room.event.UserRemovedEvent;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.game.liar.game.domain.Global.*;

@RestController
@Slf4j
//@RequiredArgsConstructor
public class GameController {
    private MessageService messageService;
    private GameService gameService;

    public GameController(MessageService messageService, GameService gameService) {
        this.messageService = messageService;
        this.gameService = gameService;
    }

    //TODO : refactoring
    @SneakyThrows
    @MessageExceptionHandler
    //@SendToUser(destinations = "/subscribe/errors", broadcast = false)
    public void LiarGameExceptionHandler(LiarGameException ex, String requestStr) {
        log.info("LiarGameExceptionHandler error {} from [request:{}]", ex.getMessage(), requestStr);
        MessageContainer request;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            request = objectMapper.readValue(requestStr, MessageContainer.class);
        } catch (JsonProcessingException e) {
            //TODO: Find how to send message to user directly
            log.error("Json Parsing error : ex from [request:{}]", requestStr);
//            return MessageContainer.messageContainerBuilder()
//                    .senderId("SERVER")
//                    .message(new MessageContainer.Message(null, new ErrorResponse(objectMapper.writeValueAsString(new ErrorResult(ex.getCode(), ex.getMessage())))))
//                    .build();
            return;
        }
        messageService.sendErrorMessage(request.getUuid(), new MessageContainer.Message(
                apiRequestMapper.get(request.getMessage().getMethod()) != null ? apiRequestMapper.get(request.getMessage().getMethod()) : "METHOD_ERROR"
                , new ErrorResponse(objectMapper.writeValueAsString(new ErrorResult(ex.getCode(), ex.getMessage())))), request.getSenderId());
    }

    @MessageMapping("private.{roomId}")
    public void handlePrivateMessage(@Payload String requestStr, @DestinationVariable("roomId") String roomId) throws JsonDeserializeException {
        log.info("[private] message from room id({}) : {}", roomId, requestStr);
        messageHandler(roomId, requestStr);
    }

    private void messageHandler(String roomId, String requestStr) throws JsonDeserializeException {
        if (gameService.checkRoomExist(RoomId.of(roomId))) {
            MessageContainer request;
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                request = objectMapper.readValue(requestStr, MessageContainer.class);
            } catch (JsonProcessingException e) {
                throw new JsonDeserializeException("JSON format doest not fit for JAVA object. Please check reference");
            }
            String method = request.getMessage().getMethod();
            messageMapper.get(method).process(request, roomId);
        } else {
            log.error("mapped room id does not exist, room id : {}", roomId);
        }
    }

    @EventListener
    @Async
    public void onUserAdded(UserAddedEvent event) {
        log.info("[onUserAdded] user Added. event :{}", event);
        String roomId = event.getRoomId();
        UserDataDto user = event.getUser();
        if (roomId == null || user == null)
            throw new IllegalArgumentException("room id/user info should be required");
        gameService.addMember(roomId, user);
    }

    @EventListener
    @Async
    public void onUserRemoved(UserRemovedEvent event) {
        log.info("[onUserRemoved] user Removed. event :{}", event);
        String roomId = event.getRoomId();
        UserDataDto user = event.getUser();
        if (roomId == null || user == null)
            throw new IllegalArgumentException("room id/user info should be required");
        gameService.deleteMember(roomId, user);

        //TODO: user login/logout 한곳에서 관리
        messageService.sendLoginInfoMessage(roomId, new LoginInfo(roomId, user.getUserId(), false));
    }

    @FunctionalInterface
    public interface ProcessGame {
        void process(MessageContainer request, String roomId);
    }

    ProcessGame getGameState = (request, roomId) -> {
        GameStateResponse state = gameService.getGameState(roomId);
        log.info("[API]getGameState gameState : {}, room id: {}", state, roomId);
        messageService.sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_STATE, state), request.getSenderId(), roomId);
    };

    ProcessGame startGame = (request, roomId) -> {
        GameInfoResponse gameInfo = gameService.startGame(request, roomId);
        log.info("[API]startGame gameInfo : {}, room id: {}", gameInfo, roomId);
        messageService.sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_STARTED, gameInfo), roomId);
    };

    ProcessGame startRound = (request, roomId) -> {
        RoundResponse roundResponse = gameService.startRound(request, roomId);
        log.info("[API]startRound response : {}, room id: {}", roundResponse, roomId);
        messageService.sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_ROUND_STARTED, roundResponse), roomId);
    };

    ProcessGame selectLiar = (request, roomId) -> {
        gameService.selectLiarAndSendIsLiar(request, roomId);
    };

    ProcessGame openKeyword = (request, roomId) -> {
        gameService.openAndSendKeyword(request, roomId);
        //Notify first user turn after keyword opened
        log.info("[API]requestTurnFinished call from server. [room:{}]", roomId);
        requestTurnFinished(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
    };

    ProcessGame requestTurnFinished = this::requestTurnFinished;

    private void requestTurnFinished(MessageContainer request, String roomId) {
        log.info("[API]requestTurnFinished request : {}", request);
        String senderId = request.getSenderId();
        gameService.updateTurn(request.getUuid(), senderId, roomId);
    }

    ProcessGame voteLiar = this::__voteLiar;

    private void __voteLiar(MessageContainer request, String roomId) {
        gameService.voteLiar(request, roomId);
        gameService.checkVoteResultAndSendMessage(request, roomId);
    }

    ProcessGame openLiar = (request, roomId) -> {
        gameService.openLiar(request, roomId);
    };

    ProcessGame checkKeywordCorrect = this::__checkKeywordCorrect;

    private void __checkKeywordCorrect(MessageContainer request, String roomId) {
        gameService.checkKeywordCorrectAndSendResult(request, roomId);
    }


    ProcessGame openScores = (request, roomId) -> {
        notifyScores(request, roomId);
        notifyRoundEnd(request, roomId);
    };

    private void notifyScores(MessageContainer request, String roomId) {
        ScoreboardResponse body = gameService.notifyScores(request, roomId);
        log.info("[API]notifyScores response : {}", body);
        messageService.sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_SCORES, body), roomId);
    }

    ProcessGame publishRankings = (request, roomId) -> {
        publishRankings(request, roomId);
        notifyGameEnd(roomId);
    };

    private void publishRankings(MessageContainer request, String roomId) {
        RankingsResponse body = gameService.publishRankings(request, roomId);
        log.info("[API]publishRankings response : {}", body);
        messageService.sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_RANKINGS_PUBLISHED, body), roomId);
    }

    ProcessGame getGameCategory = (request, roomId) -> {
        GameCategoryResponse body = gameService.getGameCategory(roomId);
        log.info("[API]getGameCategory response : {}", body);
        messageService.sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_CATEGORY, body), request.getSenderId(), roomId);
    };

    private void notifyRoundEnd(MessageContainer request, String roomId) {
        RoundResponse round = gameService.notifyRoundEnd(roomId);
        log.info("[API]notifyRoundEnd from [room:{}]", roomId);
        messageService.sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_ROUND_END, round), roomId);
    }

    private void notifyGameEnd(String roomId) {
        log.info("[API]notifyGameEnd from [room:{}]", roomId);
        GameInfo gameInfo = gameService.nextGameState(roomId);
        GameStateResponse body = new GameStateResponse(gameInfo.getState());
        messageService.sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_GAME_END, body), roomId);
        gameService.resetGame(roomId);
    }

    private final Map<String, ProcessGame> messageMapper = new HashMap<String, ProcessGame>() {
        {
            put(Global.START_GAME, startGame);
            put(Global.START_ROUND, startRound);
            put(Global.SELECT_LIAR, selectLiar);
            put(Global.OPEN_KEYWORD, openKeyword);
            put(Global.VOTE_LIAR, voteLiar);
            put(Global.OPEN_LIAR, openLiar);
            put(Global.CHECK_KEYWORD_CORRECT, checkKeywordCorrect);
            put(Global.OPEN_SCORES, openScores);
            put(Global.PUBLISH_RANKINGS, publishRankings);
            put(Global.GET_GATE_STATE, getGameState);
            put(Global.REQUEST_TURN_FINISH, requestTurnFinished);
            put(Global.GET_GAME_CATEGORY, getGameCategory);
        }
    };
}