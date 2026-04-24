package com.example.cqrs.domain;

import com.example.cqrs.domain.api.command.CompleteEmailChangeCommand;
import com.example.cqrs.domain.api.event.*;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CommandHandlingTest
public class CompleteEmailChangeTest {

    @Test
    public void completesWhenChangingEmail(@Autowired CommandHandlingTestFixture<CompleteEmailChangeCommand> fixture) {
        fixture
                .given(
                        new SignUpInitiatedEvent("alice", "alice@example.com"),
                        new SignUpCompletedEvent("alice", "alice@example.com"),
                        new EmailChangeInitiatedEvent("alice", "alice@example.com", "new@example.com")
                )
                .when(new CompleteEmailChangeCommand("alice"))
                .expectSuccessfulExecution()
                .expectSingleEvent(new EmailChangeCompletedEvent("alice", "alice@example.com"));
    }

    @Test
    public void skipsWhenAlreadyCompleted(@Autowired CommandHandlingTestFixture<CompleteEmailChangeCommand> fixture) {
        fixture
                .given(
                        new SignUpInitiatedEvent("alice", "alice@example.com"),
                        new SignUpCompletedEvent("alice", "alice@example.com")
                )
                .when(new CompleteEmailChangeCommand("alice"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }

    @Test
    public void skipsWhenStillRegistering(@Autowired CommandHandlingTestFixture<CompleteEmailChangeCommand> fixture) {
        fixture
                .given(new SignUpInitiatedEvent("alice", "alice@example.com"))
                .when(new CompleteEmailChangeCommand("alice"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }

    @Test
    public void skipsWhenNotRegistered(@Autowired CommandHandlingTestFixture<CompleteEmailChangeCommand> fixture) {
        fixture
                .given(
                        new SignUpInitiatedEvent("alice", "alice@example.com"),
                        new SignUpRejectedEvent("alice", "alice@example.com")
                )
                .when(new CompleteEmailChangeCommand("alice"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }
}
