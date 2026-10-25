package ru.practicum.ewm.stats.client;

public class StatsClientException extends RuntimeException {

    public StatsClientException(String message) {
        super(message);
    }

    public StatsClientException(String message, Throwable cause) {
        super(message, cause);
    }
}