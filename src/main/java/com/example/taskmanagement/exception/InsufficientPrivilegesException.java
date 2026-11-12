package com.example.taskmanagement.exception;

public class InsufficientPrivilegesException extends RuntimeException {
    public InsufficientPrivilegesException(String message) {
        super(message);
    }
}
