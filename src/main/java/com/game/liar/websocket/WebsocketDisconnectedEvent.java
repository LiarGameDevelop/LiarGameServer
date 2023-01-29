package com.game.liar.websocket;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class WebsocketDisconnectedEvent extends ApplicationEvent {
    @Getter
    private String sessionId;

    public WebsocketDisconnectedEvent(Object source, String sessionId) {
        super(source);
        this.sessionId = sessionId;
    }
}
