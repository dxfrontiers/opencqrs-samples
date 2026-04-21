package com.example.cqrs.domain.api.exception;

import org.springframework.http.HttpStatus;

public sealed abstract class ChangeEmailRejectedException extends RuntimeException
        permits SameEmailException,
                SignUpPendingException,
                EmailChangeInProgressException,
                AccountDisabledException {

    protected ChangeEmailRejectedException(String reason) {
        super(reason);
    }

    public abstract HttpStatus status();
}
