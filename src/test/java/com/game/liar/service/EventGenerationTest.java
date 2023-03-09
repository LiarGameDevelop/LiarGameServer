package com.game.liar.service;

import com.game.liar.room.service.RoomService;
import com.game.liar.websocket.InboundInterceptor;
import com.game.liar.websocket.WebsocketConnectedEvent;
import com.game.liar.websocket.WebsocketDisconnectedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
public class EventGenerationTest {
    @Autowired
    private ApplicationEventPublisher publisher;
    @Mock
    private InboundInterceptor inboundInterceptor;
    @MockBean
    private RoomService.WebsocketConnectionEventListener listener;

    @Test
    @DisplayName("웹소켓연결시 이벤트를 날린다")
    public void websocektConnectedTest () throws Exception{
        //Given
        publisher.publishEvent(new WebsocketConnectedEvent(inboundInterceptor,"roomId","userId","sessionId"));

        //When
        //Then
        verify(listener).connected(any());
    }

    @Test
    @DisplayName("웹소켓 연결해제시 이벤트를 날린다")
    public void websocektDisconnectedTest () throws Exception{
        //Given
        publisher.publishEvent(new WebsocketDisconnectedEvent(inboundInterceptor,"sessionId"));

        //When
        //Then
        verify(listener).disconnected(any());
    }
}
