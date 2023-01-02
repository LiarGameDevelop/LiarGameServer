package com.game.liar.service;

import com.game.liar.domain.User;
import com.game.liar.domain.Room;
import com.game.liar.dto.request.RoomIdUserIdRequest;
import com.game.liar.dto.request.RoomIdRequest;
import com.game.liar.dto.request.RoomIdUserNameRequest;
import com.game.liar.dto.request.RoomInfoRequest;
import com.game.liar.dto.response.RoomInfoResponseDto;
import com.game.liar.exception.MaxCountException;
import com.game.liar.exception.NotExistException;
import com.game.liar.repository.RoomRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RoomService {
    private RoomRepository repository;
    private final Integer MAX_COUNT_NUMBER = 16;

    public RoomService(RoomRepository repository) {
        this.repository = repository;
    }

    public RoomInfoResponseDto create(RoomInfoRequest request) throws MaxCountException, NotExistException {
        if (request.getMaxPersonCount() == null) {
            log.info("the number of room max count does not exist");
            request.setMaxPersonCount(MAX_COUNT_NUMBER);
        }

        if (request.getOwnerName() == null) {
            throw new NotExistException("Room name is required");
        }
        Room room = repository.create(request);
        return new RoomInfoResponseDto(room);
    }

    public RoomInfoResponseDto getRoom(RoomIdRequest request) {
        try {
            Room room = repository.getRoom(request);
            return new RoomInfoResponseDto(room);
        } catch (NullPointerException e) {
            throw new NotExistException("Request Room name does not exist");
        }
    }

    public List<String> getUsers(RoomIdRequest request) {
        List<User> users = repository.getUsersInRoom(request);
        return users.stream().map(User::getUserId).collect(Collectors.toList());
    }

    public RoomInfoResponseDto deleteRoom(RoomIdUserIdRequest request) {
        try {
            Room room = repository.deleteRoom(request);
            return new RoomInfoResponseDto(room);
        } catch (NullPointerException e) {
            throw new NotExistException("Request Room name does not exist");
        }
    }

    public RoomInfoResponseDto addRoomMember(RoomIdUserNameRequest request) throws MaxCountException {
        Room room = repository.addRoomMember(request);
        return new RoomInfoResponseDto(room);
    }

    public RoomInfoResponseDto leaveRoomMember(RoomIdUserIdRequest request) {
        Room room = repository.leaveRoomMember(request);
        return new RoomInfoResponseDto(room);
    }
}
