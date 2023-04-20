package com.game.liar.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.exception.*;
import com.game.liar.game.domain.GameInfo;
import com.game.liar.game.domain.GameState;
import com.game.liar.game.domain.Global;
import com.game.liar.game.dto.LiarDesignateDto;
import com.game.liar.game.dto.MessageContainer;
import com.game.liar.game.dto.request.GameSettingsRequest;
import com.game.liar.game.dto.request.KeywordRequest;
import com.game.liar.game.dto.response.*;
import com.game.liar.game.repository.GameInfoRepository;
import com.game.liar.messagequeue.TimeoutManager;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.dto.RoomIdRequest;
import com.game.liar.room.dto.UserDataDto;
import com.game.liar.room.service.RoomService;
import com.game.liar.user.domain.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.game.liar.game.domain.Global.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameService {
    private final RoomService roomService;
    private final GameSubjectService gameSubjectService;
    private final GameInfoRepository gameInfoRepository;
    private final MessageService messageService;
    //private final TimerInfoThreadPoolTaskScheduler taskScheduler;
    private final TaskScheduler taskScheduler;
    private final TimeoutManager timeoutManager;
    private final long timeout = 20000;

    @Transactional
    public boolean checkRoomExist(RoomId roomId) {
        return gameInfoRepository.findById(roomId).isPresent();
    }

    @Transactional
    public GameStateResponse getGameState(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));

        if (gameInfo == null) {
            throw new NotExistException(String.format("There is no room. Room ID :%s", roomId));
        }

        return new GameStateResponse(gameInfo.getState());
    }

    @Transactional
    public boolean isGameStarted(RoomId roomId) {
        return !getGame(roomId).getState().equals(GameState.BEFORE_START);
    }

    @Transactional
    public GameInfo getGame(RoomId roomId) {
        return gameInfoRepository.findById(roomId).orElseThrow(() -> new NotExistException(String.format("There is no game in the room [%s]", roomId)));
    }

    @Transactional
    public GameInfo getGameForUpdate(RoomId roomId) {
        return gameInfoRepository.findByRoomId(roomId).orElseThrow(() -> new NotExistException(String.format("There is no game in the room [%s]", roomId)));
    }

    @Transactional
    public GameInfoResponse startGame(MessageContainer request, String roomId) throws NotExistException, NotAllowedActionException, StateNotAllowedExpcetion {
        String senderID = request.getSenderId();
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        if (gameInfo == null) {
            throw new NotExistException(String.format("There is no room. Room ID :%s", roomId));
        }
        if (!gameInfo.getOwnerId().getUserId().equals(senderID)) {
            throw new NotAllowedActionException("You are not owner of this room");
        }
        if (gameInfo.getState() != GameState.BEFORE_START)
            throw new StateNotAllowedExpcetion("Game is not initialized");

        log.info("request.getMessage().getBody(): {}", request.getMessage().getBody());
        GameSettingsRequest settings = (GameSettingsRequest) request.getMessage().getBody();
        if (settings == null || settings.getRound() == null || settings.getTurn() == null || settings.getCategory() == null) {
            throw new RequiredParameterMissingException("Game round, turn, and category field are required");
        }
        if (settings.getRound() <= 0 || settings.getRound() >= 6) {
            throw new NotAllowedActionException("Round can be 1 to 5");
        }
        if (settings.getTurn() <= 0 || settings.getTurn() >= 4) {
            throw new NotAllowedActionException("Turn can be 1 to 3");
        }
        if (settings.getCategory().isEmpty()) {
            throw new NotAllowedActionException("Category should contain more than 1");
        }
        log.info("request OK, settings : {}", settings);
        gameInfo.setGameSettings(settings.toEntity());
        gameInfo.nextState();
        initializeGameInfo(gameInfo);
        return new GameInfoResponse(
                gameInfo.getState(),
                gameInfo.getRoomId(),
                gameInfo.getOwnerId(),
                gameInfo.getCurrentTurn(),
                gameInfo.getCurrentRound(),
                new GameSettingsResponse(
                        gameInfo.getGameSettings().getRound(),
                        gameInfo.getGameSettings().getTurn(),
                        gameInfo.getGameSettings().getCategory()
                ));
    }

    @Transactional
    public GameInfo addGame(String roomId, String roomOwnerId) {
        if (gameInfoRepository.findById(RoomId.of(roomId)).isPresent()) {
            log.error("The game manager already exists");
            throw new AlreadyExistException("The game manager already exists");
        }

        GameInfo gameInfo = new GameInfo(RoomId.of(roomId), UserId.of(roomOwnerId));
        save(gameInfo);
        log.debug("[addGame] game created");
        return gameInfo;
    }

    private void save(GameInfo gameInfo) {
        gameInfoRepository.save(gameInfo);
    }

    @Transactional
    public void removeGame(RoomId roomId) {
        if (!gameInfoRepository.findById(roomId).isPresent()) {
            log.error("The game manager does not exists");
            return;
        }
        gameInfoRepository.deleteById(roomId);
        log.debug("game manager destroyed");
    }

    public void clearGame() {
        gameInfoRepository.deleteAll();
    }

    @Transactional
    public RoundResponse startRound(MessageContainer request, String roomId) {
        String senderID = request.getSenderId();
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        if (!gameInfo.getOwnerId().getUserId().equals(senderID)) {
            throw new NotAllowedActionException("You are not owner of this room");
        }
        if (gameInfo.getState() != GameState.BEFORE_ROUND)
            throw new StateNotAllowedExpcetion("Current State is not BEFORE_ROUND");
        if (gameInfo.getGameSettings().getRound() < gameInfo.getCurrentRound() + 1) {
            throw new NotAllowedActionException("Round is over.");
        }
        gameInfo.nextRound();
        gameInfo.nextState();

        return new RoundResponse(gameInfo.getState(), gameInfo.getCurrentRound());
    }

    @Transactional
    public GameStateResponse selectLiarAndSendIsLiar(MessageContainer request, String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        selectLiar(request, roomId, gameInfo);
        sendIsLiar(request, roomId, gameInfo);
        return new GameStateResponse(gameInfo.getState());
    }

    private void selectLiar(MessageContainer request, String roomId, GameInfo gameInfo) {
        String senderID = request.getSenderId();
        if (!gameInfo.getOwnerId().getUserId().equals(senderID))
            throw new NotAllowedActionException("You are not owner of this room");
        if (gameInfo.getState() != GameState.SELECT_LIAR)
            throw new StateNotAllowedExpcetion("Current State is not SELECT_LIAR");

        String liarId = __selectLiar(getUserIdListInRoom(roomId), roomId);
        gameInfo.selectLiar(liarId);
        gameInfo.nextState();
    }

    private void sendIsLiar(MessageContainer request, String roomId, GameInfo gameInfo) {
        for (String userId : getUserIdListInRoom(roomId)) {
            boolean isLiar = userId.equals(gameInfo.getLiarId());
            LiarResponse body = new LiarResponse(gameInfo.getState(), isLiar);
            log.debug("[sendIsLiar]selectLiar response : {} to user : {}", body, userId);
            messageService.sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_LIAR_SELECTED, body), userId, roomId);
        }
    }

    private String __selectLiar(List<String> usersInRoom, String roomId) {
        Random random = new Random();
        random.setSeed(System.currentTimeMillis() + UUID.fromString(roomId).hashCode());
        int index = random.nextInt(usersInRoom.size());

        return usersInRoom.get(index);
    }

    @Transactional
    public LiarDesignateDto getLiarResponse(RoomId roomId) {
        GameInfo gameInfo = getGame(roomId);
        return new LiarDesignateDto(gameInfo.getLiarId());
    }

    @Transactional
    public TurnOrderResponse openAndSendKeyword(MessageContainer request, String roomId) {
        openKeyword(request, roomId);
        sendKeyword(request, roomId);
        return new TurnOrderResponse(getGame(RoomId.of(roomId)).getTurnOrder());
    }

    private GameInfo openKeyword(MessageContainer request, String roomId) {
        String senderID = request.getSenderId();
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        if (!gameInfo.getOwnerId().getUserId().equals(senderID))
            throw new NotAllowedActionException("You are not owner of this room");
        if (gameInfo.getState() != GameState.OPEN_KEYWORD)
            throw new StateNotAllowedExpcetion(String.format("Current State is not OPEN_KEYWORD. state:%s", gameInfo.getState()));
        selectCategory(gameInfo);
        selectKeyword(gameInfo);
        createTurnOrder(gameInfo);

        gameInfo.nextState();
        return gameInfo;
    }

    private void sendKeyword(MessageContainer request, String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        for (String userId : getUserIdListInRoom(roomId)) {
            boolean isLiar = userId.equals(gameInfo.getLiarId());
            OpenedGameInfo body = OpenedGameInfo.builder()
                    .category(gameInfo.getCurrentRoundCategory())
                    .keyword(isLiar ? "" : gameInfo.getCurrentRoundKeyword())
                    .turnOrder(gameInfo.getTurnOrder())
                    .build();
            log.info("[API]openKeyword response : {}", body);
            messageService.sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_KEYWORD_OPENED, body), userId, roomId);
        }
    }

    private void selectCategory(GameInfo gameInfo) {
        List<String> valuesList = new ArrayList<>(gameInfo.getGameSettings().getSelectedByRoomOwnerCategory().keySet());
        Collections.shuffle(valuesList);
        gameInfo.initializeRoundCategory(valuesList.get(0));
        log.info("current round category : {}", gameInfo.getCurrentRoundCategory());
    }

    private void selectKeyword(GameInfo gameInfo) {
        ArrayList<String> valuesList = gameInfo.getGameSettings().getSelectedByRoomOwnerCategory().get(gameInfo.getCurrentRoundCategory());
        Collections.shuffle(valuesList);
        gameInfo.initializeRoundKeyword(valuesList.get(0));
        log.info("current round keyword : {}", gameInfo.getCurrentRoundKeyword());
    }

    private void createTurnOrder(GameInfo gameInfo) {
        List<String> valuesList = new ArrayList<>(getUserIdListInRoom(gameInfo.getRoomId().getId()));
        log.info("[createTurnOrder] valueslist {}", valuesList);
        Collections.shuffle(valuesList);
        gameInfo.initializeRoundTurnOrder(valuesList);
        log.info("[createTurnOrder] current round turn order : {}", gameInfo.getTurnOrder());
    }

    @Transactional
    public String updateTurn(String requestUUID, String senderId, String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        log.info("[updateTurn] game info : {}", gameInfo);
        if (gameInfo.getState() != GameState.IN_PROGRESS)
            throw new StateNotAllowedExpcetion(String.format("Current State is not IN_PROGRESS. state:%s", gameInfo.getState()));
        if (gameInfo.isTurnOver()) {
            //log.info("turn cannot be larger than total turn. turn:{}", gameInfo.getTurn());
            throw new NotAllowedActionException(String.format("turn cannot be larger than total turn. turn:%d", gameInfo.getCurrentTurn()));
        }
        if (gameInfo.getCurrentTurn() < 0) {
            if (!senderId.equals(Global.SERVER_ID))
                throw new NotAllowedActionException("It's not your turn");
        } else {
            if (!gameInfo.getCurrentTurnId().equals(senderId))
                throw new NotAllowedActionException("It's not your turn");
        }
        gameInfo.nextTurn();
        cancelTurnTimer(roomId);

        String currentTurnId = getCurrentTurnUser(roomId);
        checkLastTurnAndSendYourTurnMessage(requestUUID, roomId, gameInfo, currentTurnId);
        return currentTurnId;
    }

    private void checkLastTurnAndSendYourTurnMessage(String requestUUID, String roomId, GameInfo gameInfo, String currentTurnId) {
        try {
            if (gameInfo.isLastTurn()) {
                notifyFindingLiarEnd(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
                return;
            } else {
                CurrentTurnResponse body = new CurrentTurnResponse(gameInfo.getState(), currentTurnId);
                log.info("[API]requestTurnFinished response : {}", body);
                messageService.sendPublicMessage(requestUUID, new MessageContainer.Message(NOTIFY_TURN, body), roomId);
                registerTurnTimeoutNotification(requestUUID, roomId);
            }
        } catch (NotAllowedActionException e) {
            //모든 turn이 끝났으므로 다음 state로 바꿔야함.
            log.info("[API]requestTurnFinished response exception: The round is over, change to next state");
        }
        if (gameInfo.isLastTurn()) {
            notifyFindingLiarEnd(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
        }
    }

    @Transactional
    public GameInfo notifyFindingLiarEnd(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        if (gameInfo.getState() != GameState.IN_PROGRESS)
            throw new StateNotAllowedExpcetion(String.format("Current State is not IN_PROGRESS. state:%s", gameInfo.getState()));
        gameInfo.resetTurn();
        gameInfo.nextState();
        return gameInfo;
    }

    public List<String> getUserIdListInRoom(String roomId) {
        return roomService.getUsersId(new RoomIdRequest(roomId));
    }

    @Transactional
    public GameInfo nextGameState(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.nextState();
        return gameInfo;
    }

    @Transactional
    public void addMember(String roomId, UserDataDto gameUser) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        if (gameInfo != null) {
            log.info("[addMember] room id : {} added user: {}", roomId, gameUser);
            gameInfo.addUser(gameUser);
        } else {
            log.error("room id does not exist");
        }
    }

    @Transactional
    public void deleteMember(String roomId, UserDataDto gameUser) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        if (gameInfo != null) {
            log.info("[deleteMember] room id : {} removed user: {}", roomId, gameUser);
            gameInfo.deleteUser(gameUser);
        } else {
            log.error("room id does not exist");
        }
    }

    @Transactional
    public GameInfo voteLiar(MessageContainer request, String roomId) {
        GameInfo gameInfo = getGameForUpdate(RoomId.of(roomId));
        String senderId = request.getSenderId();
        if (gameInfo.getState() != GameState.VOTE_LIAR) {
            throw new StateNotAllowedExpcetion(String.format("Current State is not VOTE_LIAR. state:%s", gameInfo.getState()));
        }
        if (gameInfo.checkVoteCompleted(senderId)) {
            throw new NotAllowedActionException("Vote finished");
        }
        LiarDesignateDto liarDesignate = (LiarDesignateDto) request.getMessage().getBody();
        if (liarDesignate.getLiar().equals("")) {
            log.debug("vote timeout from room id : {}, GameInfo: {}", roomId, gameInfo);
        } else if (!isUserInTheRoom(liarDesignate.getLiar(), roomId)) {
            throw new NotExistException("Designated user does not exist");
        }
        gameInfo.addVoteResult(senderId, liarDesignate.getLiar());
        return gameInfo;
    }

    @Transactional
    public void checkVoteResultAndSendMessage(MessageContainer request, String roomId) {
        if (isVoteFinished(roomId)) {
            cancelVoteTimer(roomId);
            timeoutManager.cancel(roomId);
            VoteResult voteResult = getMostVoted(roomId);
            log.info("[API]voteLiar response : {}", voteResult);
            String uuid = UUID.randomUUID().toString();
            messageService.sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_VOTE_RESULT, voteResult), roomId);

            if (voteResult.getMostVoted() != null) {
                if (voteResult.getMostVoted().size() == 1) {
                    GameInfo gameInfo = nextGameState(roomId);
                    messageService.sendPrivateMessage(
                            uuid
                            , new MessageContainer.Message(NOTIFY_LIAR_OPEN_REQUEST, new GameStateResponse(gameInfo.getState()))
                            , gameInfo.getOwnerId().getUserId()
                            , roomId);
                } else {
                    sendNeedVote(roomId);
                }
            } else {
                sendNeedVote(roomId);
            }
        }
    }

    @Transactional(readOnly = true)
    public VoteResult getMostVoted(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        List<Map.Entry<String, Long>> mostVoted = gameInfo.getMostVotedUserIdAndCount();
        return VoteResult.builder()
                .voteResult(gameInfo.getVoteResult())
                .mostVoted(mostVoted)
                .build();
    }

    @Transactional
    public OpenLiarResponse openLiar(MessageContainer request, String roomId) {
        OpenLiarResponse body = __openLiar(request, roomId);
        log.info("[API]openLiar response : {}", body);
        messageService.sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_OPENED, body), roomId);

        notifyLiarAnswerNeeded(roomId);
        registerLiarAnswerTimeoutNotification(request.getUuid(), roomId);
        return body;
    }

    private void notifyLiarAnswerNeeded(String roomId) {
        LiarDesignateDto gameInfo = getLiarResponse(RoomId.of(roomId));
        String liar = gameInfo.getLiar();
        log.info("[API]notifyLiarAnswerNeeded from [room:{}] to [liar:{}]", roomId, liar);
        messageService.sendPrivateMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_NEEDED, null), liar, roomId);
    }

    private OpenLiarResponse __openLiar(MessageContainer request, String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        String senderId = request.getSenderId();
        if (gameInfo.getState() != GameState.OPEN_LIAR) {
            throw new StateNotAllowedExpcetion(String.format("Current State is not OPEN_LIAR. state:%s", gameInfo.getState()));
        }
        if (!senderId.equals(gameInfo.getOwnerId().getUserId())) {
            throw new NotAllowedActionException("Only room owner can open liar");
        }
        gameInfo.nextState();
        boolean isAnswer = gameInfo.isUsersMatchLiar();
        return new OpenLiarResponse(gameInfo.getLiarId(), isAnswer, gameInfo.getState());
    }

    @Transactional
    public void checkKeywordCorrectAndSendResult(MessageContainer request, String roomId) {
        LiarAnswerResponse body = checkKeywordCorrect(request, roomId);
        log.info("[API]checkKeywordCorrect response : {}", body);
        messageService.sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_CORRECT, body), roomId);
    }

    private LiarAnswerResponse checkKeywordCorrect(MessageContainer request, String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        String senderId = request.getSenderId();
        if (gameInfo.getState() != GameState.LIAR_ANSWER) {
            throw new StateNotAllowedExpcetion(String.format("Current State is not LIAR_ANSWER. state:%s", gameInfo.getState()));
        }
        if (!senderId.equals(gameInfo.getLiarId())) {
            throw new NotAllowedActionException("Only liar can request to check keyword is right");
        }
        KeywordRequest keywordRequest = (KeywordRequest) request.getMessage().getBody();
        gameInfo.nextState();
        gameInfo.setLiarAnswer(gameInfo.getCurrentRoundKeyword().equals(keywordRequest.getKeyword()));
        cancelAnswerTimer(roomId);
        return new LiarAnswerResponse(gameInfo.getState(), gameInfo.isLiarAnswer(), gameInfo.getCurrentRoundKeyword());
    }

    @Transactional
    public ScoreboardResponse notifyScores(MessageContainer request, String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        String senderId = request.getSenderId();
        if (gameInfo.getState() != GameState.PUBLISH_SCORE) {
            throw new StateNotAllowedExpcetion(String.format("Current State is not PUBLISH_SCORE. state:%s", gameInfo.getState()));
        }
        if (!senderId.equals(gameInfo.getOwnerId().getUserId())) {
            throw new NotAllowedActionException("Only Room owner can query user scores");
        }
        gameInfo.updateScoreBoard();
        return new ScoreboardResponse(gameInfo.getScoreboard());
    }

    @Transactional
    public RoundResponse notifyRoundEnd(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        if (gameInfo.getState() != GameState.PUBLISH_SCORE)
            throw new StateNotAllowedExpcetion(String.format("Current State is not PUBLISH_SCORE. state:%s", gameInfo.getState()));
        if (gameInfo.getCurrentRound().intValue() == gameInfo.getGameSettings().getRound().intValue())
            gameInfo.nextState();
        else
            gameInfo.nextLoop();
        gameInfo.resetVoteResult();
        return new RoundResponse(gameInfo.getState(), gameInfo.getCurrentRound());
    }

    @Transactional
    public void resetLiarInfo(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.resetLiarInfo();
    }

    @Transactional
    public RankingsResponse publishRankings(MessageContainer request, String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        String senderId = request.getSenderId();
        if (gameInfo.getState() != GameState.PUBLISH_RANKINGS)
            throw new StateNotAllowedExpcetion(String.format("Current State is not PUBLISH_RANKINGS. state:%s", gameInfo.getState()));
        if (!senderId.equals(gameInfo.getOwnerId().getUserId())) {
            throw new NotAllowedActionException("Only Room owner can query publish rankings");
        }
        //{"rankings":[{"id":"id1","score":12},{"id":"id2","score":11}]}
        return new RankingsResponse(gameInfo.getScoreboard().entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .map(entry -> new RankingsResponse.RankingInfo(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
    }

    @Transactional
    public List<String> getNotVoteUserList(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        List<String> notVoteUserList = new ArrayList<>();
        log.info("gameInfo.getUserList : {}", getUserIdListInRoom(roomId));
        log.info("gameInfo.getVoteResult : {}", gameInfo.getVoteResult());
        for (String userId : getUserIdListInRoom(roomId)) {
            if (gameInfo.getVoteResult().get(userId) == null)
                notVoteUserList.add(userId);
        }
        return notVoteUserList;
    }

    @Transactional(readOnly = true)
    public GameCategoryResponse getGameCategory(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));

        if (gameInfo == null) {
            throw new NotExistException(String.format("There is no room. Room ID :%s", roomId));
        }
        return new GameCategoryResponse(gameSubjectService.getAllCategory());
    }

    @Transactional
    public void initializeGameInfo(GameInfo gameInfo) {
        gameSubjectService.loadInitialCategory();
        gameInfo.initialize(gameSubjectService.getAllSubject(), gameSubjectService.getAllCategory());
    }

    @Transactional
    public void cancelTurnTimer(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.cancelTurnTimer();
        timeoutManager.cancel(roomId);
    }

    @Transactional
    public void cancelVoteTimer(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.cancelVoteTimer();
        timeoutManager.cancel(roomId);
    }

    @Transactional
    public void cancelAnswerTimer(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.cancelAnswerTimer();
        timeoutManager.cancel(roomId);
    }

    private void startTurnTimer(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.startTurnTimer();
    }

    private void startVoteTimer(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.startVoteTimer();
    }

    private void startAnswerTimer(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.startAnswerTimer();
    }

    @Transactional
    public void resetGame(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.resetTurn();
        gameInfo.resetLiarInfo();
        gameInfo.resetScoreBoard();
        gameInfo.resetVoteResult();
    }

    private boolean isUserInTheRoom(String userId, String roomId) {
        return getUserIdListInRoom(roomId).contains(userId);
    }

    @Transactional(readOnly = true)
    public boolean isVoteFinished(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        return gameInfo.voteFinished();
    }

    @Transactional(readOnly = true)
    public String getCurrentTurnUser(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        return gameInfo.getCurrentTurnId();
    }

    @Transactional
    public GameStateResponse resetVoteResult(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.resetVoteResult();
        return new GameStateResponse(gameInfo.getState());
    }

    private void sendNeedVote(String roomId) {
        String uuid = UUID.randomUUID().toString();
        GameStateResponse response = resetVoteResult(roomId);
        messageService.sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_NEW_VOTE_NEEDED, response), roomId);
        registerVoteTimeoutNotification(uuid, roomId);
    }

    protected void registerTurnTimeoutNotification(String requestUUID, String roomId) {
        log.info("[API]register notifyTurnTimeout from [room:{}]", roomId);
        timeoutManager.timerStart(requestUUID, roomId, TimeoutManager.TimeoutData.TimerType.TURN);
        startTurnTimer(roomId);
    }

    private void registerVoteTimeoutNotification(String requestUUID, String roomId) {
        log.info("[API]register notifyVoteTimeout from [room:{}]", roomId);
        timeoutManager.timerStart(requestUUID, roomId, TimeoutManager.TimeoutData.TimerType.VOTE);
        startVoteTimer(roomId);
    }

    private void registerLiarAnswerTimeoutNotification(String requestUUID, String roomId) {
        log.info("[API]register notifyAnswerTimeout from [room:{}]", roomId);
        startAnswerTimer(roomId);
        timeoutManager.timerStart(requestUUID, roomId, TimeoutManager.TimeoutData.TimerType.ANSWER);
    }

    @Transactional
    public void onTimeout(byte[] bytes) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        TimeoutManager.TimeoutData message = objectMapper.readValue(new String(bytes), TimeoutManager.TimeoutData.class);
        String uuid = message.getUuid();
        String roomId = message.getRoomId();
        TimeoutManager.TimeoutData.TimerType timerType = message.getTimerType();

        try {
            switch (timerType) {
                case VOTE: {
                    GameInfo gameInfo = getGame(RoomId.of(roomId));
                    log.info("[API]timer triggered notifyVoteTimeout from [room:{}][timer:{}][game:{}]", roomId, this, gameInfo);
                    if (!gameInfo.isVoteTimerRunning() || gameInfo.getState() != GameState.VOTE_LIAR) return;
                    messageService.sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_VOTE_TIMEOUT, null), roomId);

                    List<String> notVoteUserList = getNotVoteUserList(roomId);
                    log.info("[API]notifyVoteTimeout notVote user :{}", notVoteUserList);
                    for (String userId : notVoteUserList) {
                        voteLiar(new MessageContainer(
                                userId,
                                new MessageContainer.Message(VOTE_LIAR, new LiarDesignateDto("")),
                                UUID.randomUUID().toString()
                        ), roomId);
                        checkVoteResultAndSendMessage(new MessageContainer(
                                userId,
                                new MessageContainer.Message(VOTE_LIAR, new LiarDesignateDto("")),
                                UUID.randomUUID().toString()
                        ), roomId);
                    }
                    break;
                }
                case TURN: {
                    GameInfo gameInfo = getGame(RoomId.of(roomId));
                    log.info("[API]timer run notifyTurnTimeout from [room:{}][timer:{}][game:{}]", roomId, this, gameInfo);
                    if (!gameInfo.isTurnTimerRunning() || gameInfo.getState() != GameState.IN_PROGRESS) {
                        return;
                    }
                    log.info("[API]notifyTurnTimeout from [room:{}]", roomId);
                    messageService.sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_TURN_TIMEOUT, null), roomId);

                    String senderId = gameInfo.getCurrentTurnId();
                    String currentTurnId = updateTurn(uuid, senderId, roomId);
                    checkLastTurnAndSendYourTurnMessage(uuid, roomId, gameInfo, currentTurnId);
                    break;
                }
                case ANSWER: {
                    GameInfo gameInfo = getGame(RoomId.of(roomId));
                    log.info("[API]timer triggered notifyAnswerTimeout from [room:{}][timer:{}][game:{}]", roomId, this, gameInfo);
                    if (!gameInfo.isAnswerTimerRunning() || gameInfo.getState() != GameState.LIAR_ANSWER) return;
                    messageService.sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_LIAR_ANSWER_TIMEOUT, null), roomId);
                    MessageContainer request = new MessageContainer(
                            gameInfo.getLiarId(),
                            new MessageContainer.Message(CHECK_KEYWORD_CORRECT, new KeywordRequest("")),
                            uuid);

                    LiarAnswerResponse response = checkKeywordCorrect(request, roomId);
                    log.info("[API]checkKeywordCorrect response : {}", response);
                    messageService.sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_CORRECT, response), roomId);
                    break;
                }
                default: {
                    log.error("There is no other timer");
                    break;
                }
            }
        } catch (NotExistException e) {
            log.error("game room error");
        }
    }

    private void notifyFindingLiarEnd(MessageContainer request, String roomId) {
        GameInfo gameInfo = notifyFindingLiarEnd(roomId);
        log.info("[API]notifyFindingLiarEnd from [room:{}]", roomId);

        RoundResponse body = new RoundResponse(gameInfo.getState(), gameInfo.getCurrentRound());
        messageService.sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_FINDING_LIAR_END, body), roomId);
        registerVoteTimeoutNotification(request.getUuid(), roomId);
    }
}
