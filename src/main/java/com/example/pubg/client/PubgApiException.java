package com.example.pubg.client;

public class PubgApiException extends RuntimeException {
    public PubgApiException(String message) {
        super(message);
    }

    public PubgApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
