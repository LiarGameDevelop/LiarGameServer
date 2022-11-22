package com.game.liar.service;

import com.game.liar.dto.Room;
import com.game.liar.dto.request.RoomIdRequest;
import com.game.liar.dto.request.RoomInfoRequest;
import com.game.liar.dto.response.RoomInfoResponseDto;
import com.game.liar.exception.MaxCountException;
import com.game.liar.exception.NotExistException;
import com.game.liar.repository.RoomRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RoomService {
    private RoomRepository repository;
    private final Integer MAX_COUNT_NUMBER = 16;

    public RoomService(RoomRepository repository) {
        this.repository = repository;
    }

    public RoomInfoResponseDto create(RoomInfoRequest request) throws MaxCountException, NotExistException {
        //sender id체크
        if (request.getSenderId() == null) {
            log.info("sender info not exist, so make new info");
            throw new NotExistException("Sender info is required");
        }

        if (request.getMaxPersonCount() == null) {
            log.info("the number of room max count does not exist");
            request.setMaxPersonCount(MAX_COUNT_NUMBER);
        }

        if (request.getRoomName() == null) {
            throw new NotExistException("Room name is required");
        }
        //room id 체크
        Room room = repository.create(request);
        return new RoomInfoResponseDto(room);
    }

    public RoomInfoResponseDto getRoom(RoomIdRequest request) {
        try{
            Room room = repository.getRoom(request);
            return new RoomInfoResponseDto(room);
        }
        catch (NullPointerException e){
            throw new NotExistException("Request Room name does not exist");
        }
    }

    public RoomInfoResponseDto deleteRoom(RoomIdRequest request){
        try{
            Room room = repository.deleteRoom(request);
            return new RoomInfoResponseDto(room);
        }
        catch (NullPointerException e){
            throw new NotExistException("Request Room name does not exist");
        }
    }

    public RoomInfoResponseDto addRoom(RoomIdRequest request) throws MaxCountException {
        Room room = repository.addRoomMember(request);
        return new RoomInfoResponseDto(room);
    }
}
