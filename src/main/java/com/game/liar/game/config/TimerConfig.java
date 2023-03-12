package com.game.liar.game.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class TimerConfig {
    @Bean
    public TimerInfoThreadPoolTaskScheduler taskScheduler(){
        TimerInfoThreadPoolTaskScheduler scheduler = new TimerInfoThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("Timer Scheduler Thread-");
        scheduler.initialize();
        return scheduler;
    }
}
