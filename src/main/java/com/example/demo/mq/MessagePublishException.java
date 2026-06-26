package com.example.demo.mq;

public class MessagePublishException extends RuntimeException {
    public MessagePublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
