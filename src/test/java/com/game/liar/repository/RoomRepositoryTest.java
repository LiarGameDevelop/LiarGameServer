package com.game.liar.repository;

import com.game.liar.dto.Room;
import com.game.liar.dto.request.RoomIdRequest;
import com.game.liar.dto.request.RoomInfoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


class RoomRepositoryTest {
    private RoomRepository roomRepository = new RoomRepository();
    @BeforeEach
    void clear() {
        roomRepository.clearRooms();
    }
    @Test
    public void 방만들기_성공() throws Exception{
        //Given
        RoomInfoRequest info = new RoomInfoRequest();
        info.setRoomName("room1");
        info.setMaxPersonCount(5);
        info.setSenderId(UUID.randomUUID().toString());

        //When
        Room result = roomRepository.create(info);
        //Then
        assertThat(roomRepository.getRoomCount()).isEqualTo(1);
        assertThat(result.getMemberList().size()).isEqualTo(1);
    }
    
    @Test
    public void 방정보얻기 () throws Exception{
        //Given
        RoomInfoRequest info = new RoomInfoRequest();
        info.setRoomName("room1");
        info.setMaxPersonCount(5);
        info.setSenderId(UUID.randomUUID().toString());
        Room room = roomRepository.create(info);

        RoomIdRequest idRequest = new RoomIdRequest(room.getRoomId(),room.getOwnerId());
        
        //When
        Room result = roomRepository.getRoom(idRequest);
        
        //Then
        assertThat(room).isEqualTo(result);
        assertThat(roomRepository.getRoomCount()).isEqualTo(1);
    }

    @Test
    public void 방삭제 () throws Exception{
        //Given
        RoomInfoRequest info = new RoomInfoRequest();
        info.setRoomName("room1");
        info.setMaxPersonCount(5);
        info.setSenderId(UUID.randomUUID().toString());
        Room room = roomRepository.create(info);

        RoomIdRequest idRequest = new RoomIdRequest(room.getRoomId(),room.getOwnerId());

        //When
        roomRepository.deleteRoom(idRequest);

        //Then
        assertThat(roomRepository.getRoomCount()).isEqualTo(0);
    }
}
