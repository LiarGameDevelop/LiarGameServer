package com.game.liar.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.domain.Global;
import com.game.liar.domain.User;
import com.game.liar.dto.MessageContainer;
import com.game.liar.dto.request.KeywordRequest;
import com.game.liar.dto.request.LiarDesignateRequest;
import com.game.liar.dto.response.*;
import com.game.liar.exception.ErrorResult;
import com.game.liar.exception.JsonDeserializeException;
import com.game.liar.exception.LiarGameException;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.domain.GameInfo;
import com.game.liar.service.GameService;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

import static com.game.liar.domain.Global.*;

@RestController
@Slf4j
public class GameController {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SimpMessagingTemplate messagingTemplate;
    private GameService gameService;
    @Setter
    private long timeout = 20000;

    public GameController(SimpMessagingTemplate messagingTemplate, GameService gameService) {
        this.messagingTemplate = messagingTemplate;
        this.gameService = gameService;
    }

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
    public void handlePrivateMessage(@Payload String requestStr, @DestinationVariable("roomId") String roomId) throws JsonDeserializeException {
        log.info("[private] message from room id({}) : {}", roomId, requestStr);
        messageHandler(roomId, requestStr);
    }

    private void messageHandler(String roomId, String requestStr) throws JsonDeserializeException {
        if (gameService.checkRoomExist(roomId)) {
            MessageContainer request = null;
            try {
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

    public void addRoom(String roomId, String ownerId) {
        gameService.addGame(roomId, ownerId);
    }

    public void removeRoom(String roomId) {
        gameService.removeGame(roomId);
    }

    public void addMember(String roomId, User userList) {
        gameService.addMember(roomId, userList);
    }

    public void deleteMember(String roomId, User userList) {
        gameService.deleteMember(roomId, userList);
    }

    @FunctionalInterface
    public interface ProcessGame {
        void process(MessageContainer request, String roomId);
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

    private void __requestTurnFinished(MessageContainer request, String roomId) {
        log.info("[API]requestTurnFinished request : {}", request);
        String senderId = request.getSenderId();
        GameInfo gameInfo = gameService.updateTurn(senderId, roomId);
        String clientId = gameInfo.getCurrentTurnId();
        gameService.cancelTurnTimer(roomId);

        try {
            if (gameInfo.isLastTurn()) {
                __notifyFindingLiarEnd(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
                return;
            } else {
                TurnResponse body = new TurnResponse(gameInfo.getState(), clientId);
                log.info("[API]requestTurnFinished response : {}", body);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_TURN, body), roomId);
                __registerTurnTimeoutNotification(request, roomId, gameInfo);
            }
        } catch (NotAllowedActionException e) {
            //?????? turn??? ??????????????? ?????? state??? ????????????.
            log.info("[API]requestTurnFinished response exception: The round is over, change to next state");
        }
        if (gameInfo.isLastTurn()) {
            __notifyFindingLiarEnd(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
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
                    sendHostMessage(uuid, new MessageContainer.Message(NOTIFY_LIAR_OPEN_REQUEST, new GameStateResponse(gameInfo.getState())), roomId);
                } else {
                    sendNeedVote(gameInfo, roomId);
                }
            } else {
                sendNeedVote(gameInfo, roomId);
            }
        }
    }

    private void sendNeedVote(GameInfo gameInfo, String roomId) {
        String uuid = UUID.randomUUID().toString();
        GameStateResponse body = new GameStateResponse(gameInfo.getState());
        gameInfo.resetVoteResult();
        sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_NEW_VOTE_NEEDED, body), roomId);
    }

    ProcessGame openLiar = (request, roomId) -> {
        OpenLiarResponse body = gameService.openLiar(request, roomId);
        log.info("[API]openLiar response : {}", body);
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_OPENED, body), roomId);

        __notifyLiarAnswerNeeded(roomId);
        __registerLiarAnswerTimeoutNotification(request, roomId, gameService.getGame(roomId));
    };

    ProcessGame checkKeywordCorrect = this::__checkKeywordCorrect;

    private void __checkKeywordCorrect(MessageContainer request, String roomId) {
        gameService.cancelAnswerTimer(roomId);
        LiarAnswerResponse body = gameService.checkKeywordCorrect(request, roomId);
        log.info("[API]checkKeywordCorrect response : {}", body);
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_CORRECT, body), roomId);
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

    private void __notifyLiarAnswerNeeded(String roomId) {
        GameInfo gameInfo = gameService.getGame(roomId);
        String liar = gameInfo.getLiarId();
        log.info("[API]notifyLiarAnswerNeeded from [room:{}]", roomId);
        sendPrivateMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_NEEDED, null), liar);
    }

    private void __notifyFindingLiarEnd(MessageContainer request, String roomId) {
        GameInfo gameInfo = gameService.notifyFindingLiarEnd(roomId);
        log.info("[API]notifyFindingLiarEnd from [room:{}]", roomId);
        RoundResponse body = new RoundResponse(gameInfo.getState(), gameInfo.getRound());
        sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_FINDING_LIAR_END, body), roomId);
        __registerVoteTimeoutNotification(request, roomId, gameInfo);
    }


    private void __notifyRoundEnd(MessageContainer request, String roomId) {
        GameInfo gameInfo = gameService.notifyRoundEnd(roomId);
        log.info("[API]notifyRoundEnd from [room:{}]", roomId);
        RoundResponse body = new RoundResponse(gameInfo.getState(), gameInfo.getRound());
        sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_ROUND_END, body), roomId);
        gameInfo.resetVoteResult();
    }

    private void __notifyGameEnd(String roomId) {
        GameInfo gameInfo = gameService.getGame(roomId);
        log.info("[API]notifyGameEnd from [room:{}]", roomId);
        gameInfo.nextState();
        GameStateResponse body = new GameStateResponse(gameInfo.getState());
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_GAME_END, body), roomId);
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

    private void __registerTurnTimeoutNotification(MessageContainer request, String roomId, GameInfo gameInfo) {
        log.info("[API]register notifyTurnTimeout from [room:{}]", roomId);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (gameInfo.isTurnTimerStop()) {
                    log.info("[API]notifyTurnTimeout STOP. from [room:{}]", roomId);
                    return;
                }
                log.info("[API]notifyTurnTimeout from [room:{}]", roomId);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_TURN_TIMEOUT, null), roomId);
                //????????????????????? ?????????, ?????? ?????? ???????????? ????????????. ???????????? ????????? ???????????? ?????????.
                __requestTurnFinished(new MessageContainer(gameInfo.getCurrentTurnId(), null, UUID.randomUUID().toString()), roomId);
            }
        };
        gameInfo.scheduleTurnTimer(task, timeout);
    }

    private void __registerVoteTimeoutNotification(MessageContainer request, String roomId, GameInfo gameInfo) {
        log.info("[API]register notifyVoteTimeout from [room:{}]", roomId);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (gameInfo.isVoteTimerStop()) {
                    log.info("[API]notifyVoteTimeout STOP. from [room:{}]", roomId);
                    return;
                }
                log.info("[API]notifyVoteTimeout from [room:{}]", roomId);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_VOTE_TIMEOUT, null), roomId);
                //????????????????????? ?????????, ????????????????????????
                List<String> notVoteUserList = gameService.getNotVoteUserList(roomId);
                log.info("[API]notifyVoteTimeout notVote user :{}", notVoteUserList);
                for (String userId : notVoteUserList) {
                    __voteLiar(new MessageContainer(userId,
                            new MessageContainer.Message(VOTE_LIAR,
                                    new LiarDesignateRequest("")),
                            UUID.randomUUID().toString()), roomId);
                }
            }
        };
        gameInfo.scheduleVoteTimer(task, timeout);
    }

    private void __registerLiarAnswerTimeoutNotification(MessageContainer request, String roomId, GameInfo gameInfo) {
        log.info("[API]register notifyAnswerTimeout from [room:{}]", roomId);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (gameInfo.isAnswerTimerStop()) {
                    log.info("[API]notifyLiarAnswerTimeout STOP. from [room:{}]", roomId);
                    return;
                }
                log.info("[API]notifyLiarAnswerTimeout from [room:{}]", roomId);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_TIMEOUT, null), roomId);
                //????????????????????? ?????????, checkKeywordCorrect ??????
                __checkKeywordCorrect(new MessageContainer(gameInfo.getLiarId(),
                                new MessageContainer.Message(CHECK_KEYWORD_CORRECT,
                                        new KeywordRequest("")),
                                UUID.randomUUID().toString()),
                        roomId);
            }
        };
        gameInfo.scheduleAnswerTimer(task, timeout);
    }

    public void sendHostMessage(String uuid, MessageContainer.Message message, String roomId) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();
        log.info("sendHostMessage. message: {}, [room:{}]", response, roomId);
        messagingTemplate.convertAndSend(String.format("/subscribe/private/%s", roomId), response);
    }

    public void sendPrivateMessage(String uuid, MessageContainer.Message message, String receiver) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();
        log.info("Send private message. message: {}, [receiver:{}]", response, receiver);
        messagingTemplate.convertAndSend(String.format("/subscribe/private/%s", receiver), response);
    }

    public void sendPublicMessage(String uuid, MessageContainer.Message message, String roomId) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();
        log.info("Send public message. message: {}, [room:{}]", response, roomId);
        messagingTemplate.convertAndSend(String.format("/subscribe/public/%s", roomId), response);
    }
}
