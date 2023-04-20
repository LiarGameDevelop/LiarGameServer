package com.game.liar.game.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

public class TimerInfoThreadPoolTaskScheduler extends ThreadPoolTaskScheduler {
    Map<String, ScheduledFuture<?>> timerInfo = new ConcurrentHashMap<>();
    public ScheduledFuture<?> schedule(Runnable task, Instant instant, TimerInfo info) {
        ScheduledFuture<?> scheduledFuture = super.schedule(task, instant);
        timerInfo.put(info.getRoomId(), scheduledFuture);
        return scheduledFuture;
    }

    public void cancel(TimerInfo timer) {
        ScheduledFuture<?> scheduledFuture = timerInfo.get(timer.getRoomId());
        if (scheduledFuture != null)
            scheduledFuture.cancel(true);
    }

    @AllArgsConstructor
    public static class TimerInfo {
        @Getter
        private String roomId;
    }
}
