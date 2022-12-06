package com.game.liar.repository;

import com.game.liar.domain.Member;
import com.game.liar.domain.Room;
import com.game.liar.domain.request.RoomIdAndUserNameRequest;
import com.game.liar.domain.request.RoomIdRequest;
import com.game.liar.domain.request.RoomInfoRequest;
import com.game.liar.exception.AlreadyExistException;
import com.game.liar.exception.MaxCountException;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.exception.NotExistException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Slf4j
public class RoomRepository {
    private final Map<String, Room> roomMap = new ConcurrentHashMap<>();
    private List<String> users = Collections.synchronizedList(new ArrayList<>());

    public int getRoomCount() {
        return roomMap.size();
    }

    public Room create(RoomInfoRequest request) throws MaxCountException, AlreadyExistException {
        Room room = makeRoom(request);
        addRoom(room);
        return room;
    }

    private Room makeRoom(RoomInfoRequest request) throws MaxCountException {
        String roomId = makeUniqueRoomId();
        String ownerId = makeUniqueUserId();
        Room room = new Room();
        room.setRoomId(roomId);
        room.setOwnerName(request.getOwnerName());
        room.setOwnerId(ownerId);
        room.setMaxCount(request.getMaxPersonCount());

        room.addMember(new Member(request.getOwnerName(),ownerId));
        users.add(ownerId);
        log.info("Created Room : {}",room);
        return room;
    }

    private String makeUniqueRoomId() {
        String id;
        while (true) {
            id = UUID.randomUUID().toString();
            if (roomMap.containsKey(id)) continue;
            break;
        }
        return id;
    }

    private String makeUniqueUserId() {
        String id;
        while (true) {
            id = UUID.randomUUID().toString();
            if (users.contains(id)) continue;
            break;
        }
        return id;
    }

    private void addRoom(Room room) {
        roomMap.put(room.getRoomId(), room);
    }

    public void clearRooms() {
        roomMap.clear();
    }

    public Room getRoom(RoomIdRequest request) {
        return roomMap.getOrDefault(request.getRoomId(), null);
    }

    public List<Member> getUsersInRoom(RoomIdRequest request) {
        Room room = roomMap.get(request.getRoomId());
        if (room == null) {
            throw new NotExistException("No member in the room");
        }
        return room.getMemberList();
    }

    public Room getRoom(String roomId) {
        return roomMap.getOrDefault(roomId, null);
    }

    public Room addRoomMember(RoomIdAndUserNameRequest request) throws MaxCountException {
        Room room = getRoom(request.getRoomId());
        String userId=makeUniqueUserId();
        if (room == null)
            throw new NotExistException("Requested Room does not exist");
        room.addMember(new Member(request.getUsername(),userId));
        return room;
    }

    public Room leaveRoomMember(RoomIdAndUserNameRequest request) {
        Room room = getRoom(request.getRoomId());
        if (room == null)
            throw new NotExistException("Requested Room does not exist");
        room.leaveMember(request.getUsername());
        return room;
    }

    public Room deleteRoom(RoomIdAndUserNameRequest request) {
        if (roomMap.containsKey(request.getRoomId())) {
            String roomOwnerId = roomMap.get(request.getRoomId()).getOwnerId();
            if (request.getUsername().equals(roomOwnerId)) {
                return roomMap.remove(request.getRoomId());
            }
            throw new NotAllowedActionException("Room owner can delete room");
        }
        throw new NullPointerException();
    }
}
