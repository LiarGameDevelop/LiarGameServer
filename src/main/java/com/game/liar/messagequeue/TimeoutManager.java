package com.game.liar.messagequeue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.game.liar.game.config.TimerInfoThreadPoolTaskScheduler;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.TimerTask;

@Service
@Slf4j
public class TimeoutManager {
    private final TimerInfoThreadPoolTaskScheduler taskScheduler;
    @Setter(value = AccessLevel.PUBLIC) //for test
    private static Integer timeout = 20000;
    private final TimeoutService timeoutService;

    public TimeoutManager(TimerInfoThreadPoolTaskScheduler taskScheduler, TimeoutService timeoutService) {
        this.taskScheduler = taskScheduler;
        this.timeoutService = timeoutService;
    }

    public void timerStart(String uuid, String roomId, TimeoutData.TimerType timerType) {
        taskScheduler.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    timeoutService.onTimeout(new TimeoutData(uuid, roomId, timerType));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        }, Instant.now().plusMillis(timeout), new TimerInfoThreadPoolTaskScheduler.TimerInfo(roomId));
    }

    public void cancel(String roomId) {
        taskScheduler.cancel(new TimerInfoThreadPoolTaskScheduler.TimerInfo(roomId));
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Setter
    public static class TimeoutData {
        String uuid;
        String roomId;
        TimerType timerType;

        public enum TimerType {
            ANSWER,
            VOTE,
            TURN
        }
    }
}
