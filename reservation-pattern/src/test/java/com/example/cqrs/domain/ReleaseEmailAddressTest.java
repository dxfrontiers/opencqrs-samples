package com.example.cqrs.domain;

import com.example.cqrs.domain.api.Purpose;
import com.example.cqrs.domain.api.command.ReleaseEmailAddressCommand;
import com.example.cqrs.domain.api.event.EmailAddressReleasedEvent;
import com.example.cqrs.domain.api.event.EmailAddressReservedEvent;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CommandHandlingTest
public class ReleaseEmailAddressTest {

    @Test
    public void releasesOwnReservation(@Autowired CommandHandlingTestFixture<ReleaseEmailAddressCommand> fixture) {
        fixture
                .given(new EmailAddressReservedEvent("alice@example.com", "alice", Purpose.SIGN_UP))
                .when(new ReleaseEmailAddressCommand("alice@example.com", "alice"))
                .expectSuccessfulExecution()
                .expectSingleEvent(new EmailAddressReleasedEvent("alice@example.com"));
    }

    @Test
    public void skipsAlreadyReleased(@Autowired CommandHandlingTestFixture<ReleaseEmailAddressCommand> fixture) {
        fixture
                .given(
                        new EmailAddressReservedEvent("alice@example.com", "alice", Purpose.SIGN_UP),
                        new EmailAddressReleasedEvent("alice@example.com")
                )
                .when(new ReleaseEmailAddressCommand("alice@example.com", "alice"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }

    @Test
    public void rejectsDifferentUser(@Autowired CommandHandlingTestFixture<ReleaseEmailAddressCommand> fixture) {
        fixture
                .given(new EmailAddressReservedEvent("alice@example.com", "alice", Purpose.SIGN_UP))
                .when(new ReleaseEmailAddressCommand("alice@example.com", "bob"))
                .expectException(IllegalStateException.class);
    }
}
