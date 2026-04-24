package com.example.cqrs.domain;

import com.example.cqrs.domain.api.command.CompleteSignUpCommand;
import com.example.cqrs.domain.api.event.*;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CommandHandlingTest
public class CompleteSignUpTest {

    @Test
    public void completesWhenRegistering(@Autowired CommandHandlingTestFixture<CompleteSignUpCommand> fixture) {
        fixture
                .given(new SignUpInitiatedEvent("alice", "alice@example.com"))
                .when(new CompleteSignUpCommand("alice", "alice@example.com"))
                .expectSuccessfulExecution()
                .expectSingleEvent(new SignUpCompletedEvent("alice", "alice@example.com"));
    }

    @Test
    public void skipsWhenRegisteringForDifferentEmail(@Autowired CommandHandlingTestFixture<CompleteSignUpCommand> fixture) {
        fixture
                .given(new SignUpInitiatedEvent("alice", "alice@example.com"))
                .when(new CompleteSignUpCommand("alice", "other@example.com"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }

    @Test
    public void skipsWhenAlreadyRegistered(@Autowired CommandHandlingTestFixture<CompleteSignUpCommand> fixture) {
        fixture
                .given(
                        new SignUpInitiatedEvent("alice", "alice@example.com"),
                        new SignUpCompletedEvent("alice", "alice@example.com")
                )
                .when(new CompleteSignUpCommand("alice", "alice@example.com"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }

    @Test
    public void skipsWhenChangingEmail(@Autowired CommandHandlingTestFixture<CompleteSignUpCommand> fixture) {
        fixture
                .given(
                        new SignUpInitiatedEvent("alice", "alice@example.com"),
                        new SignUpCompletedEvent("alice", "alice@example.com"),
                        new EmailChangeInitiatedEvent("alice", "alice@example.com", "new@example.com")
                )
                .when(new CompleteSignUpCommand("alice", "alice@example.com"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }

    @Test
    public void skipsWhenNotRegistered(@Autowired CommandHandlingTestFixture<CompleteSignUpCommand> fixture) {
        fixture
                .given(
                        new SignUpInitiatedEvent("alice", "alice@example.com"),
                        new SignUpRejectedEvent("alice", "alice@example.com")
                )
                .when(new CompleteSignUpCommand("alice", "alice@example.com"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }
}
