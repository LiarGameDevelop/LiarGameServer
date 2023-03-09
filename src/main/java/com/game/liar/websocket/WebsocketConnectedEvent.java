package com.game.liar.websocket;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class WebsocketConnectedEvent extends ApplicationEvent {
    @Getter
    private String roomId;
    @Getter
    private String userId;
    @Getter
    private String sessionId;

    public WebsocketConnectedEvent(Object source, String roomId, String userId, String sessionId) {
        super(source);
        this.roomId = roomId;
        this.userId = userId;
        this.sessionId = sessionId;
    }
}
