package com.example.taskmanagement.exception;

import org.springframework.http.HttpStatus;

public class BadGatewayException extends ApiException {
    public BadGatewayException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}
