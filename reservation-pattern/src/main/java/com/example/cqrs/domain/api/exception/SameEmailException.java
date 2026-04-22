package com.example.cqrs.domain.api.exception;

public final class SameEmailException extends ChangeEmailRejectedException {
    public SameEmailException() {
        super("New email is the same as the current one.");
    }
}
