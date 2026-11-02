package com.pharmasync.service;

/** A rule violation that should be shown to the user (invalid input, blocked operation, etc.). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
