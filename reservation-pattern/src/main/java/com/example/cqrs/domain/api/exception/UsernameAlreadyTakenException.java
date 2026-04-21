package com.example.cqrs.domain.api.exception;

import org.springframework.http.HttpStatus;

public final class UsernameAlreadyTakenException extends SignUpRejectedException {
    public UsernameAlreadyTakenException(String username) {
        super("Username '" + username + "' is already taken.");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
