package com.example.taskmanagement.exception;

public class InvalidBusinessDataException extends RuntimeException {
    public InvalidBusinessDataException(String message) {
        super(message);
    }
}
