package com.example.cqrs.domain.api.exception;

public sealed abstract class SignUpRejectedException extends RuntimeException
        permits UsernameAlreadyTakenException {

    protected SignUpRejectedException(String reason) {
        super(reason);
    }
}
