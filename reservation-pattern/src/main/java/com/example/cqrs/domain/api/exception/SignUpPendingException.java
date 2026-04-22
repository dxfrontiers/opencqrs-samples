package com.example.cqrs.domain.api.exception;

public final class SignUpPendingException extends ChangeEmailRejectedException {
    public SignUpPendingException() {
        super("Cannot change email: sign-up is still pending.");
    }
}
