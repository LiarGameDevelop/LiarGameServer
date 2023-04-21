package com.game.liar.messagequeue;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimeoutService {
    private final ApplicationEventPublisher publisher;

    public void onTimeout(TimeoutManager.TimeoutData data) throws JsonProcessingException {
        publisher.publishEvent(new TimeoutEvent(this, data));
    }
}
