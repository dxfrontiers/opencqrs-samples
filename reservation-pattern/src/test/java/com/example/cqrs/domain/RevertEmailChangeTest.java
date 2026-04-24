package com.example.cqrs.domain;

import com.example.cqrs.domain.api.command.RevertEmailChangeCommand;
import com.example.cqrs.domain.api.event.*;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CommandHandlingTest
public class RevertEmailChangeTest {

    @Test
    public void revertsWhenChangingEmail(@Autowired CommandHandlingTestFixture<RevertEmailChangeCommand> fixture) {
        fixture
                .given(
                        new SignUpInitiatedEvent("alice", "alice@example.com"),
                        new SignUpCompletedEvent("alice", "alice@example.com"),
                        new EmailChangeInitiatedEvent("alice", "alice@example.com", "new@example.com")
                )
                .when(new RevertEmailChangeCommand("alice"))
                .expectSuccessfulExecution()
                .expectSingleEvent(new EmailChangeRevertedEvent("alice"));
    }

    @Test
    public void skipsWhenAlreadyReverted(@Autowired CommandHandlingTestFixture<RevertEmailChangeCommand> fixture) {
        fixture
                .given(
                        new SignUpInitiatedEvent("alice", "alice@example.com"),
                        new SignUpCompletedEvent("alice", "alice@example.com")
                )
                .when(new RevertEmailChangeCommand("alice"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }

    @Test
    public void skipsWhenStillRegistering(@Autowired CommandHandlingTestFixture<RevertEmailChangeCommand> fixture) {
        fixture
                .given(new SignUpInitiatedEvent("alice", "alice@example.com"))
                .when(new RevertEmailChangeCommand("alice"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }

    @Test
    public void skipsWhenNotRegistered(@Autowired CommandHandlingTestFixture<RevertEmailChangeCommand> fixture) {
        fixture
                .given(
                        new SignUpInitiatedEvent("alice", "alice@example.com"),
                        new SignUpRejectedEvent("alice", "alice@example.com")
                )
                .when(new RevertEmailChangeCommand("alice"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }
}
