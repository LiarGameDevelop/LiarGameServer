package com.game.liar.messagequeue;

import com.game.liar.game.config.TimerInfoThreadPoolTaskScheduler;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.TimerTask;

import static com.game.liar.messagequeue.TimeoutService.topicExchangeName;

@Service
@Slf4j
public class TimeoutManager {
    private final RabbitTemplate rabbitTemplate;
    private final TimerInfoThreadPoolTaskScheduler taskScheduler;
    @Setter(value = AccessLevel.PUBLIC) //for test
    private static Integer timeout = 20000;

    public TimeoutManager(RabbitTemplate rabbitTemplate, TimerInfoThreadPoolTaskScheduler taskScheduler) {
        this.rabbitTemplate = rabbitTemplate;
        this.taskScheduler = taskScheduler;
    }

    public void timerStart(String uuid, String roomId, TimeoutData.TimerType timerType) {
        taskScheduler.schedule(new TimerTask() {
            @Override
            public void run() {
                rabbitTemplate.convertAndSend(topicExchangeName, roomId, new TimeoutData(uuid, roomId, timerType));
            }
        }, Instant.now().plusMillis(timeout), new TimerInfoThreadPoolTaskScheduler.TimerInfo(roomId));
    }

    public void cancel(String roomId) {
        log.info("JBJB cancel thread roomID: {}", roomId);
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
