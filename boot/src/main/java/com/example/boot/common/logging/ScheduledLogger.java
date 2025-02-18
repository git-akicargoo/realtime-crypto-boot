package com.example.boot.common.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledLogger {
    private final Map<String, Queue<LogEntry>> logQueues = new ConcurrentHashMap<>();

    private static class LogEntry {
        final Logger logger;
        final String message;
        final Object[] args;
        final String messagePattern;

        LogEntry(Logger logger, String message, Object... args) {
            this.logger = logger;
            this.message = message;
            this.args = args;
            this.messagePattern = message.replaceAll("\\{\\}", ".*?");
        }

        String getFormattedMessage() {
            return MessageFormatter.arrayFormat(message, args).getMessage();
        }
    }
    
    public void scheduleLog(Logger log, String message, Object... args) {
        String key = log.getName();
        logQueues.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>())
                .offer(new LogEntry(log, message, args));
    }

    @Scheduled(fixedRateString = "${logging.scheduled.default-interval:10000}")
    public void flushLogs() {
        logQueues.forEach((loggerName, queue) -> {
            if (!queue.isEmpty()) {
                StringBuilder sb = new StringBuilder("\n=== Aggregated Logs ===\n");
                Queue<LogEntry> entries = new ConcurrentLinkedQueue<>(queue);
                
                // 스케줄이 돌 때마다 큐를 비움
                queue.clear();

                // 중복 제거를 위한 Map (패턴별로 마지막 메시지만 유지)
                Map<String, LogEntry> uniquePatterns = new LinkedHashMap<>();
                entries.forEach(entry -> uniquePatterns.put(entry.messagePattern, entry));

                // 대표 메시지만 출력
                uniquePatterns.values().forEach(entry -> 
                    sb.append(String.format("- %s\n", entry.getFormattedMessage()))
                );

                if (!entries.isEmpty()) {
                    entries.peek().logger.info(sb.toString());
                }
            }
        });
    }
} 