package com.example.cqrs.domain;

import com.example.cqrs.domain.api.command.ChangeEmailCommand;
import com.example.cqrs.domain.api.event.EmailChangeInitiatedEvent;
import com.example.cqrs.domain.api.event.SignUpCompletedEvent;
import com.example.cqrs.domain.api.event.SignUpInitiatedEvent;
import com.example.cqrs.domain.api.exception.EmailChangeInProgressException;
import com.example.cqrs.domain.api.exception.SameEmailException;
import com.example.cqrs.domain.api.exception.SignUpPendingException;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CommandHandlingTest
public class ChangeEmailTest {

    @Test
    public void publishesInitiatedWhenRegistered(@Autowired CommandHandlingTestFixture<ChangeEmailCommand> fixture) {
        fixture
                .given(
                        new SignUpInitiatedEvent("alice", "alice@example.com"),
                        new SignUpCompletedEvent("alice", "alice@example.com")
                )
                .when(new ChangeEmailCommand("alice", "new@example.com"))
                .expectSuccessfulExecution()
                .expectSingleEvent(new EmailChangeInitiatedEvent("alice", "alice@example.com", "new@example.com"));
    }

    @Test
    public void rejectsSameEmail(@Autowired CommandHandlingTestFixture<ChangeEmailCommand> fixture) {
        fixture
                .given(
                        new SignUpInitiatedEvent("alice", "alice@example.com"),
                        new SignUpCompletedEvent("alice", "alice@example.com")
                )
                .when(new ChangeEmailCommand("alice", "alice@example.com"))
                .expectException(SameEmailException.class);
    }

    @Test
    public void rejectsDuringSignUp(@Autowired CommandHandlingTestFixture<ChangeEmailCommand> fixture) {
        fixture
                .given(new SignUpInitiatedEvent("alice", "alice@example.com"))
                .when(new ChangeEmailCommand("alice", "new@example.com"))
                .expectException(SignUpPendingException.class);
    }

    @Test
    public void rejectsWhileAnotherChangeInProgress(@Autowired CommandHandlingTestFixture<ChangeEmailCommand> fixture) {
        fixture
                .given(
                        new SignUpInitiatedEvent("alice", "alice@example.com"),
                        new SignUpCompletedEvent("alice", "alice@example.com"),
                        new EmailChangeInitiatedEvent("alice", "alice@example.com", "pending@example.com")
                )
                .when(new ChangeEmailCommand("alice", "other@example.com"))
                .expectException(EmailChangeInProgressException.class);
    }
}
