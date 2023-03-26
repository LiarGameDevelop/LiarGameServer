package com.game.liar.service;

import com.game.liar.game.domain.RoomSettings;
import com.game.liar.room.domain.Authority;
import com.game.liar.room.domain.GameUser;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.domain.UserId;
import com.game.liar.room.dto.RoomDto;
import com.game.liar.room.dto.RoomInfoResponse;
import com.game.liar.room.dto.UserDataDto;
import com.game.liar.room.event.UserAddedEvent;
import com.game.liar.room.event.UserRemovedEvent;
import com.game.liar.room.service.RoomService;
import com.game.liar.user.repository.UserRepository;
import com.game.liar.websocket.InboundInterceptor;
import com.game.liar.websocket.WebsocketConnectedEvent;
import com.game.liar.websocket.WebsocketDisconnectedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserEventListenerTest {
    @Mock
    private ApplicationEventPublisher publisher;
    @InjectMocks
    private InboundInterceptor inboundInterceptor;
    @InjectMocks
    private RoomService.WebsocketConnectionEventListener listener;
    @Mock
    private RoomService roomService;
    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("connect 이벤트가 들어오면 userAdd event를 발생시킨다")
    public void connectionAddEvent() throws Exception {
        //Given
        when(userRepository.findByUserIdAndRoomId(any(), any()))
                .thenReturn(Optional.of(new GameUser(UserId.of("userId"), RoomId.of("roomId"), "username", "password", Authority.ROLE_USER)));
        doNothing().when(publisher).publishEvent(isA(UserAddedEvent.class));

        //When
        listener.connected(new WebsocketConnectedEvent(inboundInterceptor, "roomId", "userId", "sessionId"));

        //Then
        verify(publisher, times(1)).publishEvent(isA(UserAddedEvent.class));
        verify(userRepository, times(1)).findByUserIdAndRoomId(any(), any());
    }

    @Test
    @DisplayName("disconnect 이벤트가 들어오면 userRemoved event를 발생시킨다")
    public void disconnectionAddEvent() throws Exception {
        //Given
        when(userRepository.findBySessionId(any()))
                .thenReturn(Optional.of(new GameUser(UserId.of("userId"), RoomId.of("roomId"), "username", "password", Authority.ROLE_USER)));
        doNothing().when(publisher).publishEvent(isA(UserRemovedEvent.class));
        RoomInfoResponse roomInfoResponse=new RoomInfoResponse(new RoomDto("roomId","userId",new RoomSettings(3)), Arrays.asList(
                new UserDataDto("username","userId")));
        when(roomService.getRoom(any())).thenReturn(roomInfoResponse);

        //When
        listener.disconnected(new WebsocketDisconnectedEvent(inboundInterceptor, "sessionId"));

        //Then
        verify(publisher, times(1)).publishEvent(isA(UserRemovedEvent.class));
        verify(roomService, times(1)).leaveRoomMember(any());
        verify(userRepository, times(1)).findBySessionId(any());
    }
}
