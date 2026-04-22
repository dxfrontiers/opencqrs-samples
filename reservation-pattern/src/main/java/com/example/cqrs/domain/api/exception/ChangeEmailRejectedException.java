package com.example.cqrs.domain.api.exception;

public sealed abstract class ChangeEmailRejectedException extends RuntimeException
        permits SameEmailException,
                SignUpPendingException,
                EmailChangeInProgressException,
                AccountDisabledException {

    protected ChangeEmailRejectedException(String reason) {
        super(reason);
    }
}
