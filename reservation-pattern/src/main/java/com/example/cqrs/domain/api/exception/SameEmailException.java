package com.example.cqrs.domain.api.exception;

import org.springframework.http.HttpStatus;

public final class SameEmailException extends ChangeEmailRejectedException {
    public SameEmailException() {
        super("New email is the same as the current one.");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
