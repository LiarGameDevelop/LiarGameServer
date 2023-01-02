package com.game.liar.repository;

import com.game.liar.domain.Room;
import com.game.liar.dto.request.RoomIdUserIdRequest;
import com.game.liar.dto.request.RoomIdRequest;
import com.game.liar.dto.request.RoomInfoRequest;
import com.game.liar.exception.NotAllowedActionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        info.setOwnerName("room1");
        info.setMaxPersonCount(5);

        //When
        Room result = roomRepository.create(info);
        //Then
        assertThat(roomRepository.getRoomCount()).isEqualTo(1);
        assertThat(result.getUserList().size()).isEqualTo(1);
    }

    @Test
    public void 방정보얻기_성공() throws Exception{
        //Given
        RoomInfoRequest info = new RoomInfoRequest();
        info.setOwnerName("room1");
        info.setMaxPersonCount(5);
        Room room = roomRepository.create(info);

        RoomIdRequest idRequest = new RoomIdRequest(room.getRoomId());

        //When
        Room result = roomRepository.getRoom(idRequest);

        //Then
        assertThat(room).isEqualTo(result);
        assertThat(roomRepository.getRoomCount()).isEqualTo(1);
    }

    @Test
    public void 방삭제_성공() throws Exception{
        //Given
        RoomInfoRequest info = new RoomInfoRequest();
        info.setOwnerName("room1");
        info.setMaxPersonCount(5);
        Room room = roomRepository.create(info);

        RoomIdUserIdRequest idRequest = new RoomIdUserIdRequest(room.getRoomId(),room.getOwnerId());

        //When
        roomRepository.deleteRoom(idRequest);

        //Then
        assertThat(roomRepository.getRoomCount()).isEqualTo(0);
    }

    @Test
    public void 방삭제_Error_존재하지않는방번호() throws Exception{
        //Given
        RoomInfoRequest info = new RoomInfoRequest();
        info.setOwnerName("room1");
        info.setMaxPersonCount(5);
        Room room = roomRepository.create(info);

        RoomIdUserIdRequest idRequest = new RoomIdUserIdRequest("1234",room.getOwnerId());

        //When
        assertThrows(NullPointerException.class, ()->{roomRepository.deleteRoom(idRequest);});
        //Then
        assertThat(roomRepository.getRoomCount()).isEqualTo(1);
    }

    @Test
    public void 방삭제_Error_owner매치에러() throws Exception{
        //Given
        RoomInfoRequest info = new RoomInfoRequest();
        info.setOwnerName("room1");
        info.setMaxPersonCount(5);
        Room room = roomRepository.create(info);

        RoomIdUserIdRequest idRequest = new RoomIdUserIdRequest(room.getRoomId(),"1234");

        //When
        assertThrows(NotAllowedActionException.class, ()->{roomRepository.deleteRoom(idRequest);});
        //Then
        assertThat(roomRepository.getRoomCount()).isEqualTo(1);
    }

    @Test
    public void 방입장_성공() throws Exception{
        //Given
        RoomInfoRequest info = new RoomInfoRequest();
        info.setOwnerName("room1");
        info.setMaxPersonCount(5);
        Room room = roomRepository.create(info);

        RoomIdUserIdRequest idRequest = new RoomIdUserIdRequest(room.getRoomId(),"1234");

        //When
        Room result = roomRepository.addRoomMember(idRequest);
        //Then
        System.out.println(room);
        System.out.println(result);
        assertThat(room).isEqualTo(result);
    }
}
