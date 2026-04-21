package com.example.cqrs.domain.api.exception;

import org.springframework.http.HttpStatus;

public final class SignUpPendingException extends ChangeEmailRejectedException {
    public SignUpPendingException() {
        super("Cannot change email: sign-up is still pending.");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
