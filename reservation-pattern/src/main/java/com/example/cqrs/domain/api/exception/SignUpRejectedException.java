package com.example.cqrs.domain.api.exception;

import org.springframework.http.HttpStatus;

public sealed abstract class SignUpRejectedException extends RuntimeException
        permits UsernameAlreadyTakenException {

    protected SignUpRejectedException(String reason) {
        super(reason);
    }

    public abstract HttpStatus status();
}
