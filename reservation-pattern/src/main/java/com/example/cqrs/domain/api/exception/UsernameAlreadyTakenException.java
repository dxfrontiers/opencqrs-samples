package com.example.cqrs.domain.api.exception;

public final class UsernameAlreadyTakenException extends SignUpRejectedException {
    public UsernameAlreadyTakenException(String username) {
        super("Username '" + username + "' is already taken.");
    }
}
