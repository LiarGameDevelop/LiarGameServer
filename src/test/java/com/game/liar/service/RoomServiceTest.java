package com.game.liar.service;

import com.game.liar.dto.Room;
import com.game.liar.dto.request.RoomIdRequest;
import com.game.liar.dto.request.RoomInfoRequest;
import com.game.liar.dto.response.RoomInfoResponseDto;
import com.game.liar.exception.NotExistException;
import com.game.liar.repository.RoomRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoomServiceTest {
    private RoomRepository roomRepository = new RoomRepository();
    private RoomService roomService = new RoomService(roomRepository);

    @BeforeEach
    private void clear(){
        roomRepository.clearRooms();
    }

    @Test
    public void 방만들기_성공() throws Exception {
        //Given
        RoomInfoRequest roomInfo = new RoomInfoRequest();
        roomInfo.setRoomName("room1");
        roomInfo.setMaxPersonCount(5);
        roomInfo.setSenderId(UUID.randomUUID().toString());

        //When
        RoomInfoResponseDto result = roomService.create(roomInfo);
        //Then
        Assertions.assertThat(result).isNotNull();
        assertThat(result.getRoomName()).isEqualTo(roomInfo.getRoomName());
        assertThat(result.getOwnerId()).isEqualTo(roomInfo.getSenderId());
    }

    @Test
    public void 방만들기_Error_sender정보없음() throws Exception {
        //Given
        RoomInfoRequest roomInfo = new RoomInfoRequest();
        roomInfo.setRoomName("room12");
        roomInfo.setMaxPersonCount(5);

        assertThrows(NotExistException.class, ()->{roomService.create(roomInfo);});
        assertThat(roomRepository.getRoomCount()).isEqualTo(0);
    }

    @Test
    public void 방만들기_Error_roomname정보없음() throws Exception {
        //Given
        RoomInfoRequest roomInfo = new RoomInfoRequest();
        roomInfo.setMaxPersonCount(5);
        roomInfo.setSenderId(UUID.randomUUID().toString());

        assertThrows(NotExistException.class, ()->{roomService.create(roomInfo);});
        assertThat(roomRepository.getRoomCount()).isEqualTo(0);
    }

    @Test
    public void 방찾기_성공() throws Exception {
        //Given
        RoomInfoRequest roomInfo = new RoomInfoRequest();
        roomInfo.setMaxPersonCount(5);
        roomInfo.setRoomName("room12");
        roomInfo.setSenderId(UUID.randomUUID().toString());
        RoomInfoResponseDto room = roomService.create(roomInfo);

        RoomIdRequest idRequest = new RoomIdRequest(room.getRoomId());

        RoomInfoResponseDto result = roomService.getRoom(idRequest);

        assertThat(room.getRoomName()).isEqualTo(result.getRoomName());
        assertThat(room.getMaxPersonCount()).isEqualTo(result.getMaxPersonCount());
        assertThat(result.getOwnerId()).isNotBlank();
    }

    @Test
    public void 방찾기_Error() throws Exception {
        //Given
        RoomInfoRequest roomInfo = new RoomInfoRequest();
        roomInfo.setMaxPersonCount(5);
        roomInfo.setRoomName("room12");
        roomInfo.setSenderId(UUID.randomUUID().toString());
        Room room = roomRepository.create(roomInfo);

        RoomIdRequest idRequest = new RoomIdRequest("error");

        assertThrows(NotExistException.class, ()->{ roomService.getRoom(idRequest);});
    }
}