package com.game.liar.game.service;

import com.game.liar.exception.*;
import com.game.liar.game.domain.GameInfo;
import com.game.liar.game.domain.GameState;
import com.game.liar.game.domain.Global;
import com.game.liar.game.dto.MessageContainer;
import com.game.liar.game.dto.request.GameSettingsRequest;
import com.game.liar.game.dto.request.KeywordRequest;
import com.game.liar.game.dto.request.LiarDesignateRequest;
import com.game.liar.game.dto.response.*;
import com.game.liar.game.repository.GameInfoRepository;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.dto.RoomIdRequest;
import com.game.liar.room.dto.UserDataDto;
import com.game.liar.room.service.RoomService;
import com.game.liar.user.domain.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameService {
    private final RoomService roomService;
    private final GameSubjectService gameSubjectService;
    private final GameInfoRepository gameInfoRepository;

    public boolean checkRoomExist(RoomId roomId) {
        return gameInfoRepository.findById(roomId).isPresent();
    }

    public GameInfo getGameState(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));

        if (gameInfo == null) {
            throw new NotExistException(String.format("There is no room. Room ID :%s", roomId));
        }

        return gameInfo;
    }

    public boolean isGameStarted(RoomId roomId){
        return !getGame(roomId).getState().equals(GameState.BEFORE_START);
    }

    public GameInfo getGame(RoomId roomId) {
        return gameInfoRepository.findById(roomId).orElseThrow(() -> new NotExistException(String.format("There is no game in the room [%s]",roomId)));
    }

    public GameInfo getGameForUpdate(RoomId roomId) {
        return gameInfoRepository.findByRoomId(roomId).orElseThrow(() -> new NotExistException(String.format("There is no game in the room [%s]",roomId)));
    }

    @Transactional
    public GameInfo startGame(MessageContainer request, String roomId) throws NotExistException, NotAllowedActionException, StateNotAllowedExpcetion {
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
        return gameInfo;
    }

    public GameInfo addGame(String roomId, String roomOwnerId) {
        if (gameInfoRepository.findById(RoomId.of(roomId)).isPresent()) {
            log.error("The game manager already exists");
            throw new AlreadyExistException("The game manager already exists");
        }

        GameInfo gameInfo = new GameInfo(RoomId.of(roomId), UserId.of(roomOwnerId));
        saveData(gameInfo);
        log.debug("game manager created");
        return gameInfo;
    }

    private void saveData(GameInfo gameInfo) {
        gameInfoRepository.save(gameInfo);
    }

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
    public GameInfo startRound(MessageContainer request, String roomId) {
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

        return gameInfo;
    }

    @Transactional
    public GameInfo selectLiar(MessageContainer request, String roomId) {
        String senderID = request.getSenderId();
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        if (!gameInfo.getOwnerId().getUserId().equals(senderID))
            throw new NotAllowedActionException("You are not owner of this room");
        if (gameInfo.getState() != GameState.SELECT_LIAR)
            throw new StateNotAllowedExpcetion("Current State is not SELECT_LIAR");

        String liarId = __selectLiar(getUserIdListInRoom(roomId), roomId);
        gameInfo.selectLiar(liarId);
        gameInfo.nextState();

        return gameInfo;
    }

    private String __selectLiar(List<String> usersInRoom, String roomId) {
        Random random = new Random();
        random.setSeed(System.currentTimeMillis() + UUID.fromString(roomId).hashCode());
        int index = random.nextInt(usersInRoom.size());

        return usersInRoom.get(index);
    }

    @Transactional
    public GameInfo openKeyword(MessageContainer request, String roomId) {
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
        log.info("[createTurnOrder] valueslist {}",valuesList);
        Collections.shuffle(valuesList);
        gameInfo.initializeRoundTurnOrder(valuesList);
        log.info("[createTurnOrder] current round turn order : {}", gameInfo.getTurnOrder());
    }

    @Transactional
    public GameInfo updateTurn(String senderId, String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        log.info("[updateTurn] gameinfo : {}",gameInfo);
        if (gameInfo.getState() != GameState.IN_PROGRESS)
            throw new StateNotAllowedExpcetion(String.format("Current State is not IN_PROGRESS. state:%s", gameInfo.getState()));
        if (gameInfo.getCurrentTurn() >= gameInfo.getGameSettings().getTurn() * gameInfo.getTurnOrder().size()) {
            //log.info("turn cannot be larger than total turn. turn:{}", gameInfo.getTurn());
            throw new NotAllowedActionException(String.format("turn cannot be larger than total turn. turn:%d", gameInfo.getCurrentTurn()));
        }
        if (gameInfo.getCurrentTurn() < 0) {
            if (!senderId.equals(Global.SERVER_ID))
                throw new RuntimeException("It's not your turn");
        } else {
            if (!gameInfo.getCurrentTurnId().equals(senderId))
                throw new RuntimeException("It's not your turn");
        }
        gameInfo.nextTurn();
        return gameInfo;
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
        LiarDesignateRequest liarDesignate = (LiarDesignateRequest) request.getMessage().getBody();
        if (liarDesignate.getLiar().equals("")) {
            log.debug("vote timeout from room id : {}, GameInfo: {}", roomId, gameInfo);
        } else if (!isUserInTheRoom(liarDesignate.getLiar(), roomId)) {
            throw new NotExistException("Designated user does not exist");
        }
        gameInfo.addVoteResult(senderId, liarDesignate.getLiar());
        return gameInfo;
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
    public LiarAnswerResponse checkKeywordCorrect(MessageContainer request, String roomId) {
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
    public GameInfo notifyRoundEnd(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        if (gameInfo.getState() != GameState.PUBLISH_SCORE)
            throw new StateNotAllowedExpcetion(String.format("Current State is not PUBLISH_SCORE. state:%s", gameInfo.getState()));
        if (gameInfo.getCurrentRound().intValue() == gameInfo.getGameSettings().getRound().intValue())
            gameInfo.nextState();
        else
            gameInfo.nextLoop();
        return gameInfo;
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
    }

    @Transactional
    public void cancelVoteTimer(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.cancelVoteTimer();
    }

    @Transactional
    public void cancelAnswerTimer(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.cancelAnswerTimer();
    }

    @Transactional
    public void startTurnTimer(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.startTurnTimer();
    }

    @Transactional
    public void startVoteTimer(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.startVoteTimer();
    }

    @Transactional
    public void startAnswerTimer(String roomId) {
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
    public boolean isVoteFinished(String roomId){
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        return gameInfo.voteFinished();
    }

    @Transactional(readOnly = true)
    public String getCurrentTurnUser(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        return gameInfo.getCurrentTurnId();
    }

    @Transactional(readOnly = true)
    public boolean isLastTurn(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        return gameInfo.isLastTurn();
    }

    @Transactional
    public GameInfo resetVoteResult(String roomId) {
        GameInfo gameInfo = getGame(RoomId.of(roomId));
        gameInfo.resetVoteResult();
        return gameInfo;
    }
}
