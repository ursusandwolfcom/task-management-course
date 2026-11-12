package com.example.taskmanagement.exception;

public class AttachmentLimitExceededException extends RuntimeException {
    public AttachmentLimitExceededException(String message) {
        super(message);
    }
}
