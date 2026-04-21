package com.example.cqrs.domain.api.exception;

import org.springframework.http.HttpStatus;

public final class AccountDisabledException extends ChangeEmailRejectedException {
    public AccountDisabledException() {
        super("Cannot change email: account is NotRegistered.");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
