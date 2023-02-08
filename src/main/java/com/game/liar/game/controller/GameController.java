package com.game.liar.game.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.exception.ErrorResult;
import com.game.liar.exception.JsonDeserializeException;
import com.game.liar.exception.LiarGameException;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.game.domain.GameInfo;
import com.game.liar.game.domain.Global;
import com.game.liar.game.dto.MessageContainer;
import com.game.liar.game.dto.request.KeywordRequest;
import com.game.liar.game.dto.request.LiarDesignateRequest;
import com.game.liar.game.dto.response.*;
import com.game.liar.game.service.GameService;
import com.game.liar.room.dto.UserDataDto;
import com.game.liar.room.event.UserAddedEvent;
import com.game.liar.room.event.UserRemovedEvent;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

import static com.game.liar.game.domain.Global.*;

@RestController
@Slf4j
public class GameController {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public GameController(SimpMessagingTemplate messagingTemplate, GameService gameService) {
        this.messagingTemplate = messagingTemplate;
        this.gameService = gameService;
    }

    private SimpMessagingTemplate messagingTemplate;
    private GameService gameService;
    @Setter
    private long timeout = 20000;

    //TODO : refactoring
    @SneakyThrows
    @MessageExceptionHandler
    @SendToUser(destinations = "/subscribe/errors", broadcast = false)
    public MessageContainer LiarGameExceptionHandler(LiarGameException ex, String requestStr) {
        MessageContainer request;
        try {
            request = objectMapper.readValue(requestStr, MessageContainer.class);
        } catch (JsonProcessingException e) {
            log.info("Json Parsing error : ex from [request:{}]", requestStr);
            return MessageContainer.messageContainerBuilder()
                    .senderId("SERVER")
                    .message(new MessageContainer.Message(null, new ErrorResponse(objectMapper.writeValueAsString(new ErrorResult(ex.getCode(), ex.getMessage())))))
                    .build();
        }
        return MessageContainer.messageContainerBuilder()
                .uuid(request.getUuid())
                .senderId("SERVER")
                .message(new MessageContainer.Message(
                        apiRequestMapper.get(request.getMessage().getMethod()) != null ? apiRequestMapper.get(request.getMessage().getMethod()) : "METHOD_ERROR"
                        , new ErrorResponse(objectMapper.writeValueAsString(new ErrorResult(ex.getCode(), ex.getMessage())))))
                .build();
    }

    @MessageMapping("/private/{roomId}")
    public void handlePrivateMessage(@Payload String requestStr, @DestinationVariable("roomId") String string) throws JsonDeserializeException {
        log.info("[private] message from room id({}) : {}", string, requestStr);
        messageHandler(string, requestStr);
    }

    private void messageHandler(String string, String requestStr) throws JsonDeserializeException {
        if (gameService.checkRoomExist(string)) {
            MessageContainer request = null;
            try {
                request = objectMapper.readValue(requestStr, MessageContainer.class);
            } catch (JsonProcessingException e) {
                throw new JsonDeserializeException("JSON format doest not fit for JAVA object. Please check reference");
            }
            String method = request.getMessage().getMethod();
            messageMapper.get(method).process(request, string);
        } else {
            log.error("mapped room id does not exist, room id : {}", string);
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
        GameInfo gameInfo = gameService.getGame(roomId);
        gameInfo.addUser(user);
    }

    @EventListener
    @Async
    public void onUserRemoved(UserRemovedEvent event) throws JsonProcessingException {
        log.info("[onUserRemoved] user Removed. event :{}", event);
        String roomId = event.getRoomId();
        UserDataDto user = event.getUser();
        if (roomId == null || user == null)
            throw new IllegalArgumentException("room id/user info should be required");
        GameInfo gameInfo = gameService.getGame(roomId);
        gameInfo.deleteUser(user);

        //TODO: user login/logout 한곳에서 관리
        LogoutInfo logoutInfo = new LogoutInfo(roomId, user.getUserId(), false);
        messagingTemplate.convertAndSend(String.format("/subscribe/room.logout/%s", roomId), objectMapper.writeValueAsString(logoutInfo));
    }

    @AllArgsConstructor
    @Getter
    private static class LogoutInfo {
        String roomId;
        String userId;
        boolean login;
    }

    public void addRoom(String string, String ownerId) {
        gameService.addGame(string, ownerId);
    }

    public void removeRoom(String string) {
        gameService.removeGame(string);
    }

    @FunctionalInterface
    public interface ProcessGame {
        void process(MessageContainer request, String string);
    }

    ProcessGame getGameState = (request, roomId) -> {
        GameInfo gameInfo = gameService.getGameState(roomId);
        GameStateResponse body = new GameStateResponse(gameInfo.getState());
        sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_STATE, body), request.getSenderId());
    };

    ProcessGame startGame = (request, roomId) -> {
        GameInfo gameInfo = gameService.startGame(request, roomId);
        GameStateResponse body = new GameStateResponse(gameInfo.getState());
        log.info("[API]startGame response : {}, room id: {}", body, roomId);
        sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_STARTED, body), roomId);
    };

    ProcessGame startRound = (request, roomId) -> {
        GameInfo gameInfo = gameService.startRound(request, roomId);
        RoundResponse body = new RoundResponse(gameInfo.getState(), gameInfo.getRound());
        log.info("[API]startRound response : {}, room id: {}", body, roomId);
        sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_ROUND_STARTED, body), roomId);
    };

    ProcessGame selectLiar = (request, roomId) -> {
        GameInfo gameInfo = gameService.selectLiar(request, roomId);
        for (String userId : gameService.getUserIdListInRoom(roomId)) {
            boolean isLiar = userId.equals(gameInfo.getLiarId());
            LiarResponse body = new LiarResponse(gameInfo.getState(), isLiar);
            log.info("[API]selectLiar response : {} to user : {}", body, userId);
            sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_LIAR_SELECTED, body), userId);
        }
    };

    ProcessGame openKeyword = (request, roomId) -> {
        GameInfo gameInfo = gameService.openKeyword(request, roomId);
        for (String userId : gameService.getUserIdListInRoom(roomId)) {
            boolean isLiar = userId.equals(gameInfo.getLiarId());
            OpenedGameInfo body = OpenedGameInfo.builder()
                    .category(gameInfo.getCurrentRoundCategory())
                    .keyword(isLiar ? "" : gameInfo.getCurrentRoundKeyword())
                    .turnOrder(gameInfo.getTurnOrder())
                    .build();
            log.info("[API]openKeyword response : {}", body);
            sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_KEYWORD_OPENED, body), userId);
        }
        //Notify first user turn after keyword opened
        log.info("[API]__requestTurnFinished call from server. [room:{}]", roomId);
        __requestTurnFinished(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
    };

    ProcessGame requestTurnFinished = this::__requestTurnFinished;

    private void __requestTurnFinished(MessageContainer request, String string) {
        log.info("[API]requestTurnFinished request : {}", request);
        String senderId = request.getSenderId();
        GameInfo gameInfo = gameService.updateTurn(senderId, string);
        String clientId = gameInfo.getCurrentTurnId();
        gameService.cancelTurnTimer(string);

        try {
            if (gameInfo.isLastTurn()) {
                __notifyFindingLiarEnd(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), string);
                return;
            } else {
                TurnResponse body = new TurnResponse(gameInfo.getState(), clientId);
                log.info("[API]requestTurnFinished response : {}", body);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_TURN, body), string);
                __registerTurnTimeoutNotification(request, string, gameInfo);
            }
        } catch (NotAllowedActionException e) {
            //모든 turn이 끝났으므로 다음 state로 바꿔야함.
            log.info("[API]requestTurnFinished response exception: The round is over, change to next state");
        }
        if (gameInfo.isLastTurn()) {
            __notifyFindingLiarEnd(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), string);
        }
    }

    ProcessGame voteLiar = this::__voteLiar;

    private void __voteLiar(MessageContainer request, String roomId) {
        GameInfo gameInfo = gameService.voteLiar(request, roomId);
        if (gameInfo.voteFinished()) {
            gameService.cancelVoteTimer(roomId);
            VoteResult voteResult = gameService.getMostVoted(roomId);
            log.info("[API]voteLiar response : {}", voteResult);
            String uuid = UUID.randomUUID().toString();
            sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_VOTE_RESULT, voteResult), roomId);

            if (voteResult.getMostVoted() != null) {
                if (voteResult.getMostVoted().size() == 1) {
                    gameInfo.nextState();
                    sendPrivateMessage(uuid, new MessageContainer.Message(NOTIFY_LIAR_OPEN_REQUEST, new GameStateResponse(gameInfo.getState())), gameInfo.getOwnerId());
                } else {
                    sendNeedVote(gameInfo, roomId);
                }
            } else {
                sendNeedVote(gameInfo, roomId);
            }
        }
    }

    private void sendNeedVote(GameInfo gameInfo, String string) {
        String uuid = UUID.randomUUID().toString();
        GameStateResponse body = new GameStateResponse(gameInfo.getState());
        gameInfo.resetVoteResult();
        sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_NEW_VOTE_NEEDED, body), string);
    }

    ProcessGame openLiar = (request, roomId) -> {
        OpenLiarResponse body = gameService.openLiar(request, roomId);
        log.info("[API]openLiar response : {}", body);
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_OPENED, body), roomId);

        __notifyLiarAnswerNeeded(roomId);
        __registerLiarAnswerTimeoutNotification(request, roomId, gameService.getGame(roomId));
    };

    ProcessGame checkKeywordCorrect = this::__checkKeywordCorrect;

    private void __checkKeywordCorrect(MessageContainer request, String string) {
        gameService.cancelAnswerTimer(string);
        LiarAnswerResponse body = gameService.checkKeywordCorrect(request, string);
        log.info("[API]checkKeywordCorrect response : {}", body);
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_CORRECT, body), string);
    }


    ProcessGame openScores = (request, roomId) -> {
        ScoreboardResponse body = gameService.notifyScores(request, roomId);
        log.info("[API]notifyScores response : {}", body);
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_SCORES, body), roomId);
        __notifyRoundEnd(request, roomId);
    };

    ProcessGame publishRankings = (request, roomId) -> {
        RankingsResponse body = gameService.publishRankings(request, roomId);
        log.info("[API]publishRankings response : {}", body);
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_RANKINGS_PUBLISHED, body), roomId);
        __notifyGameEnd(roomId);
    };

    ProcessGame getGameCategory = (request, roomId) -> {
        GameCategoryResponse body = gameService.getGameCategory(roomId);
        log.info("[API]getGameCategory response : {}", body);
        sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_CATEGORY, body), request.getSenderId());
    };

    private void __notifyLiarAnswerNeeded(String string) {
        GameInfo gameInfo = gameService.getGame(string);
        String liar = gameInfo.getLiarId();
        log.info("[API]notifyLiarAnswerNeeded from [room:{}]", string);
        sendPrivateMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_NEEDED, null), liar);
    }

    private void __notifyFindingLiarEnd(MessageContainer request, String string) {
        GameInfo gameInfo = gameService.notifyFindingLiarEnd(string);
        log.info("[API]notifyFindingLiarEnd from [room:{}]", string);
        RoundResponse body = new RoundResponse(gameInfo.getState(), gameInfo.getRound());
        sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_FINDING_LIAR_END, body), string);
        __registerVoteTimeoutNotification(request, string, gameInfo);
    }


    private void __notifyRoundEnd(MessageContainer request, String string) {
        GameInfo gameInfo = gameService.notifyRoundEnd(string);
        log.info("[API]notifyRoundEnd from [room:{}]", string);
        RoundResponse body = new RoundResponse(gameInfo.getState(), gameInfo.getRound());
        sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_ROUND_END, body), string);
        gameInfo.resetVoteResult();
    }

    private void __notifyGameEnd(String string) {
        GameInfo gameInfo = gameService.getGame(string);
        log.info("[API]notifyGameEnd from [room:{}]", string);
        gameInfo.nextState();
        GameStateResponse body = new GameStateResponse(gameInfo.getState());
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_GAME_END, body), string);
        gameService.resetGame(string);
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

    private void __registerTurnTimeoutNotification(MessageContainer request, String string, GameInfo gameInfo) {
        log.info("[API]register notifyTurnTimeout from [room:{}]", string);
        gameInfo.setTurnTask(new TimerTask() {
            @Override
            public void run() {
                log.info("[API]notifyTurnTimeout from [room:{}]", string);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_TURN_TIMEOUT, null), string);
                //타임아웃났다고 알리고, 다음 턴의 사람으로 바꿔야함. 다음턴의 사람이 보냈다고 해야함.
                __requestTurnFinished(new MessageContainer(gameInfo.getCurrentTurnId(), null, UUID.randomUUID().toString()), string);
            }
        });
        gameInfo.scheduleTurnTimer(timeout);
    }

    private void __registerVoteTimeoutNotification(MessageContainer request, String roomId, GameInfo gameInfo) {
        log.info("[API]register notifyVoteTimeout from [room:{}]", roomId);
        gameInfo.setVoteTask(new TimerTask() {
            @Override
            public void run() {
                log.info("[API]notifyVoteTimeout from [room:{}]", roomId);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_VOTE_TIMEOUT, null), roomId);
                //타임아웃났다고 알리고, 투표완료시켜야함
                List<String> notVoteUserList = gameService.getNotVoteUserList(roomId);
                log.info("[API]notifyVoteTimeout notVote user :{}", notVoteUserList);
                for (String userId : notVoteUserList) {
                    __voteLiar(new MessageContainer(userId,
                            new MessageContainer.Message(VOTE_LIAR,
                                    new LiarDesignateRequest("")),
                            UUID.randomUUID().toString()), roomId);
                }
            }
        });
        gameInfo.scheduleVoteTimer(timeout);
    }

    private void __registerLiarAnswerTimeoutNotification(MessageContainer request, String string, GameInfo gameInfo) {
        log.info("[API]register notifyAnswerTimeout from [room:{}]", string);
        gameInfo.setAnswerTask(new TimerTask() {
            @Override
            public void run() {
                log.info("[API]notifyLiarAnswerTimeout from [room:{}]", string);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_TIMEOUT, null), string);
                //타임아웃났다고 알리고, checkKeywordCorrect 요청
                __checkKeywordCorrect(new MessageContainer(gameInfo.getLiarId(),
                                new MessageContainer.Message(CHECK_KEYWORD_CORRECT,
                                        new KeywordRequest("")),
                                UUID.randomUUID().toString()),
                        string);
            }
        });
        gameInfo.scheduleAnswerTimer(timeout);
    }

    //TODO: introduce message service
    public void sendPrivateMessage(String uuid, MessageContainer.Message message, String receiver) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();
        log.info("Send private message. message: {}, [receiver:{}]", response, receiver);
        messagingTemplate.convertAndSend(String.format("/subscribe/private/%s", receiver), response);
    }

    public void sendPublicMessage(String uuid, MessageContainer.Message message, String string) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();
        log.info("Send public message. message: {}, [room:{}]", response, string);
        messagingTemplate.convertAndSend(String.format("/subscribe/public/%s", string), response);
    }
}
