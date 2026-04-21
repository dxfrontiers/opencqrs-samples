package com.example.cqrs.domain.api.exception;

import org.springframework.http.HttpStatus;

public final class EmailChangeInProgressException extends ChangeEmailRejectedException {
    public EmailChangeInProgressException() {
        super("Another email change is already in progress.");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}