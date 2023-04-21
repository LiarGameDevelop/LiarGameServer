package com.game.liar.messagequeue;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

@ToString
public class TimeoutEvent extends ApplicationEvent {
    @Getter
    private final TimeoutManager.TimeoutData data;

    public TimeoutEvent(Object source, TimeoutManager.TimeoutData data) {
        super(source);
        this.data = data;
    }
}