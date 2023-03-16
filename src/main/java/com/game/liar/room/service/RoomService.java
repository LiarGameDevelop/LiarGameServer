package com.game.liar.room.service;

import com.game.liar.exception.MaxCountException;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.exception.NotExistException;
import com.game.liar.game.domain.RoomSettings;
import com.game.liar.room.domain.GameUser;
import com.game.liar.room.domain.Room;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.domain.UserId;
import com.game.liar.room.dto.*;
import com.game.liar.room.event.UserAddedEvent;
import com.game.liar.room.event.UserRemovedEvent;
import com.game.liar.room.repository.RoomRepository;
import com.game.liar.security.JwtService;
import com.game.liar.security.dto.TokenDto;
import com.game.liar.security.util.SecurityUtil;
import com.game.liar.user.repository.UserRepository;
import com.game.liar.websocket.WebsocketConnectedEvent;
import com.game.liar.websocket.WebsocketDisconnectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomService {
    private static final Integer MAX_COUNT_NUMBER = 6;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    @Transactional
    public EnterRoomResponse create(RoomInfoRequest request) {
        if (request.getMaxPersonCount() == null) {
            log.info("the number of room max count does not exist");
            request.setMaxPersonCount(MAX_COUNT_NUMBER);
        }
        if (request.getOwnerName() == null) {
            throw new NotExistException("Room name is required");
        }
        if (request.getPassword() == null) {
            throw new NotExistException("Password is required");
        }

        RoomId roomId = generateRoomId();
        GameUser user = userRepository.createUser(request.getOwnerName(), request.getPassword(), roomId, encoder);
        UserId ownerId = user.getUserId();

        RoomSettings roomSettings = RoomSettings.of(request.getMaxPersonCount());
        Room room = new Room(roomId, roomSettings, ownerId);
        roomRepository.save(room);

        UserDto userDto = UserDto.toDto(user);
        userDto.setPassword(request.getPassword());
        TokenDto token = jwtService.getJwtToken(userDto, room);

        List<UserDataDto> users = getUserList(roomId);

        return new EnterRoomResponse(new RoomDto(room), userDto, users, token);
    }

    private RoomId generateRoomId() {
        RoomId roomId = roomRepository.nextRoomId();
        while (roomRepository.findById(roomId).isPresent()) {
            log.debug("room id{} does exist. Generate new room id", roomId.getId());
            roomId = roomRepository.nextRoomId();
        }
        return roomId;
    }

    public RoomInfoResponse getRoom(RoomIdRequest request) {
        RoomId roomId = RoomId.of(request.getRoomId());
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new NotExistException("Request Room name does not exist"));
        List<UserDataDto> users = getUserList(roomId);
        return new RoomInfoResponse(new RoomDto(room), users);
    }

    private List<UserDataDto> getUserList(RoomId roomId) {
        return userRepository.findByRoomId(roomId).stream().map(UserDataDto::toDto).collect(Collectors.toList());
    }

    public List<String> getUsersId(RoomIdRequest request) {
        return userRepository.findByRoomId(RoomId.of(request.getRoomId())).stream()
                .map(UserDataDto::toDto).map(UserDataDto::getUserId).collect(Collectors.toList());
    }

    @Transactional
    public void deleteRoom(RoomIdRequest request) {
        RoomId roomId = RoomId.of(request.getRoomId());
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new NotExistException("Request Room name does not exist"));
        if (room.getOwnerId().getUserId().equals(SecurityUtil.getCurrentUserId()))
            roomRepository.delete(room);
        else
            throw new NotAllowedActionException("You are not owner of the room");
    }

    @Transactional
    public EnterRoomResponse addRoomMember(RoomIdUserNameRequest request) {
        RoomId roomId = RoomId.of(request.getRoomId());
        String username = request.getUsername();
        String password = request.getPassword();

        Room room = roomRepository.findById(roomId).orElseThrow(() -> {
            log.error("request room id : {}", roomId.getId());
            return new NotExistException("No room");
        });
        if (userRepository.findByRoomId(roomId).size() + 1 > room.getSettings().getMaxCount()) {
            throw new MaxCountException("No left seat in the room");
        }
        GameUser user = userRepository.createUser(username, password, roomId, encoder);

        UserDto userDto = UserDto.toDto(user);
        userDto.setPassword(request.getPassword());
        TokenDto token = jwtService.getJwtToken(userDto, room);

        List<UserDataDto> users = getUserList(roomId);

        log.info("room : {}, user : {}", user, room);

        return new EnterRoomResponse(new RoomDto(room), userDto, users, token);
    }

    @Transactional
    public RoomInfoResponse leaveRoomMember(RoomIdUserIdRequest request) {
        log.info("[leaveRoomMember] request :{}", request);
        RoomId roomId = RoomId.of(request.getRoomId());
        UserId userId = UserId.of(request.getUserId());

        GameUser user = userRepository.findByUserIdAndRoomId(userId, roomId).orElseThrow(() -> new NotExistException("No user in the room"));
        userRepository.delete(user);

        Room room = roomRepository.findById(roomId).orElseThrow(() -> new NotExistException("No room exists"));
        List<UserDataDto> users = userRepository.findByRoomId(roomId).stream().map(UserDataDto::toDto).collect(Collectors.toList());
        return new RoomInfoResponse(new RoomDto(room), users);
    }

    @Component
    @RequiredArgsConstructor
    public static class WebsocketConnectionEventListener {
        private final RoomService roomService;
        private final UserRepository userRepository;
        private final ApplicationEventPublisher publisher;

        @EventListener
        @Async
        @Transactional
        public void connected(WebsocketConnectedEvent event) {
            String roomId = event.getRoomId();
            String userId = event.getUserId();
            String sessionId = event.getSessionId();
            if (roomId == null || userId == null || sessionId == null)
                throw new IllegalArgumentException("room id/user id/session id should be required");

            GameUser user = userRepository.findByUserIdAndRoomId(UserId.of(userId), RoomId.of(roomId))
                    .orElseThrow(() -> new NotExistException(String.format("There is no session id :%s", sessionId)));
            log.info("User found and save the user{} session id {}", userId, sessionId);
            user.saveSession(sessionId);
            publisher.publishEvent(new UserAddedEvent(this, roomId, UserDataDto.toDto(user)));
        }

        @EventListener
        @Async
        public void disconnected(WebsocketDisconnectedEvent event) {
            String sessionId = event.getSessionId();
            if (sessionId == null) {
                throw new IllegalArgumentException("Session id should be required");
            }
            log.info("disconnected session id :{}", sessionId);
            Optional<GameUser> userOpt = userRepository.findBySessionId(sessionId);
            if (!userOpt.isPresent()) {
                log.debug("There is no session id {}", sessionId);
                return;
            }

            GameUser user = userOpt.get();
            String roomId = user.getRoomId().getId();
            String userId = user.getUserId().getUserId();
            String username = user.getUsername();
            UserDataDto userDto = new UserDataDto(username, userId);

            roomService.leaveRoomMember(new RoomIdUserIdRequest(roomId, userId));
            publisher.publishEvent(new UserRemovedEvent(this, roomId, userDto));
        }
    }

}
