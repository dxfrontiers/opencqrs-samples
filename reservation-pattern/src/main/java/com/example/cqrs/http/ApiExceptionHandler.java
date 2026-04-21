package com.example.cqrs.http;

import com.example.cqrs.domain.api.command.SignUpCommand;
import com.example.cqrs.domain.api.exception.ChangeEmailRejectedException;
import com.example.cqrs.domain.api.exception.SignUpRejectedException;
import com.example.cqrs.domain.api.exception.UsernameAlreadyTakenException;
import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ChangeEmailRejectedException.class)
    public ProblemDetail changeEmailRejected(ChangeEmailRejectedException e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }

    @ExceptionHandler(SignUpRejectedException.class)
    public ProblemDetail signUpRejected(SignUpRejectedException e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }

    @ExceptionHandler(CommandSubjectAlreadyExistsException.class)
    public ProblemDetail alreadyExists(CommandSubjectAlreadyExistsException e) {
        if (e.getCommand() instanceof SignUpCommand signUp) {
            UsernameAlreadyTakenException rejected = new UsernameAlreadyTakenException(signUp.username());
            return ProblemDetail.forStatusAndDetail(rejected.status(), rejected.getMessage());
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(CommandSubjectDoesNotExistException.class)
    public ProblemDetail notFound(CommandSubjectDoesNotExistException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
