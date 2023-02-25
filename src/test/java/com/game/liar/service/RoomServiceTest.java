package com.game.liar.service;

import com.game.liar.exception.MaxCountException;
import com.game.liar.exception.NotExistException;
import com.game.liar.game.domain.RoomSettings;
import com.game.liar.room.domain.*;
import com.game.liar.room.dto.*;
import com.game.liar.room.repository.RoomRepository;
import com.game.liar.room.service.RoomService;
import com.game.liar.security.JwtService;
import com.game.liar.security.dto.TokenDto;
import com.game.liar.user.repository.UserRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Disabled
class RoomServiceTest {
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private RoomService roomService;

    @Test
    public void 방만들기_성공() throws Exception {
        //Given
        String roomId= UUID.randomUUID().toString();
        when(roomRepository.nextRoomId()).thenReturn(new RoomId(roomId));
        List<GameUser> fakeUsers = new ArrayList<>();
        fakeUsers.add(new GameUser(UserId.of("ownerId"),RoomId.of(roomId),"ownerName","password2", Authority.ROLE_USER));
        when(userRepository.findByRoomId(any())).thenReturn(fakeUsers);
        RoomInfoRequest request = createRoomPrecondition();

        //When
        EnterRoomResponse result = createRoom(request);
        //Then
        assertThat(result).isNotNull();
        assertThat(result.getRoom().getSettings().getMaxCount()).isEqualTo(request.getMaxPersonCount());
        assertThat(result.getUser().getUsername()).isEqualTo(request.getOwnerName());
        assertThat(result.getRoom().getOwnerId()).isEqualTo(result.getUser().getUserId());
        assertThat(result.getUserList()).isEqualTo(fakeUsers.stream().map(UserDataDto::toDto).collect(Collectors.toList()));

        verify(roomRepository, times(1)).save(any());
        verify(userRepository, times(1)).createUser(any(), any(), any(), any());
        verify(userRepository, times(1)).findByRoomId(any());
    }

    private EnterRoomResponse createRoom(RoomInfoRequest request) {
        EnterRoomResponse result = roomService.create(request);
        return result;
    }

    private RoomInfoRequest createRoomPrecondition() {
        RoomInfoRequest request = new RoomInfoRequest(5, "ownerName", "password");
        Room room = createFakeRoom(request);
        when(roomRepository.save(any())).thenReturn(room);
        when(userRepository.createUser(eq("ownerName"), eq("password"), any(), eq(passwordEncoder)))
                .thenReturn(new GameUser(new UserId("ownerId"), room.getId(), "ownerName", "password2", null));

        when(jwtService.getJwtToken(any(), any())).thenReturn(new TokenDto("ROLE_USER", "access", "refresh", 10L));
        return request;
    }

    private Room createFakeRoom(RoomInfoRequest request) {
        Room room = new Room(new RoomId(request.getOwnerName())
                , new RoomSettings(request.getMaxPersonCount())
                , new UserId("ownerId"));
        return room;
    }

    @Test
    public void 방만들기_Error_roomname정보없음() throws Exception {
        //Given
        RoomInfoRequest request = new RoomInfoRequest();
        request.setMaxPersonCount(5);

        assertThrows(NotExistException.class, () -> {
            roomService.create(request);
        });
    }

    @Test
    public void 방만들기_Error_비밀번호정보없음() throws Exception {
        //Given
        RoomInfoRequest request = new RoomInfoRequest();
        request.setMaxPersonCount(5);

        assertThrows(NotExistException.class, () -> {
            roomService.create(request);
        });
    }


    @Test
    public void 방찾기_성공() throws Exception {
        //Given
        RoomInfoRequest makeRoomReq = new RoomInfoRequest(5, "ownerName", "password");
        when(roomRepository.findById(any())).thenReturn(Optional.of(createFakeRoom(makeRoomReq)));
        when(userRepository.findByRoomId(any()))
                .thenReturn(Collections.singletonList(new GameUser(new UserId("ownerId"), RoomId.of("ownerName"), "ownerName", "password2", null)));

        //When
        RoomIdRequest request = new RoomIdRequest(makeRoomReq.getOwnerName());
        RoomInfoResponse result = roomService.getRoom(request);

        //Then
        assertThat(result.getRoom().getRoomId()).isEqualTo(request.getRoomId());
        assertThat(result.getRoom().getOwnerId()).isEqualTo("ownerId");
        assertThat(result.getUserList().size()).isEqualTo(1);
    }

    @Test
    public void 방찾기_Error() throws Exception {
        //Given
        when(roomRepository.findById(any())).thenThrow(NotExistException.class);

        //When
        assertThrows(NotExistException.class, () -> roomService.getRoom(new RoomIdRequest("NoRoom")));
    }

    @Test
    public void 방입장_성공() throws Exception {
        //Given
        RoomInfoRequest createRoomRequest = new RoomInfoRequest(5, "ownerName", "password");
        when(roomRepository.findById(any())).thenReturn(Optional.of(createFakeRoom(createRoomRequest)));
        GameUser fakeUser = new GameUser(
                UserId.of("guest" + UUID.randomUUID()),
                RoomId.of("room"),
                "guest",
                "password2",
                null);
        List<GameUser> fakeUsers = Collections.singletonList(fakeUser);
        when(userRepository.findByRoomId(any())).thenReturn(fakeUsers);
        when(userRepository.createUser(eq("guest"), eq("password"), any(), eq(passwordEncoder)))
                .thenReturn(new GameUser(
                        UserId.of("guest" + UUID.randomUUID()),
                        RoomId.of("room"),
                        "guest",
                        "password2",
                        null));

        //When
        RoomIdUserNameRequest guest = new RoomIdUserNameRequest("room", "guest", "password");
        EnterRoomResponse result = roomService.addRoomMember(guest);

        //Then
        System.out.println("result :" + result + ", guest" + guest);
        assertThat(result.getUser().getUsername()).isEqualTo(guest.getUsername());
        assertThat(result.getUser().getPassword()).isEqualTo(guest.getPassword());
        assertThat(result.getUserList()).isEqualTo(fakeUsers.stream().map(UserDataDto::toDto).collect(Collectors.toList()));

        verify(roomRepository,times(1)).findById(any());
        verify(userRepository,times(2)).findByRoomId(any());

    }

    @Test
    public void 방입장_인원초과_Error() throws Exception {
        //Given
        RoomInfoRequest createRoomRequest = new RoomInfoRequest(5, "ownerName", "password");
        when(roomRepository.findById(any())).thenReturn(Optional.of(createFakeRoom(createRoomRequest)));
        GameUser fakeUser = new GameUser(
                UserId.of("guest" + UUID.randomUUID()),
                RoomId.of("room"),
                "guest",
                "password2",
                null);
        List<GameUser> users = Arrays.asList(fakeUser, fakeUser, fakeUser, fakeUser, fakeUser, fakeUser);
        when(userRepository.findByRoomId(any())).thenReturn(users);

        //When
        RoomIdUserNameRequest guest = new RoomIdUserNameRequest("room", "guest", "password");

        //Then
        assertThrows(MaxCountException.class, () -> roomService.addRoomMember(guest));
    }
}