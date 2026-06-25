package com.example.demo.service.exception;

public class IllegalTaskStateException extends RuntimeException {
    public IllegalTaskStateException(String message) {
        super(message);
    }
}
