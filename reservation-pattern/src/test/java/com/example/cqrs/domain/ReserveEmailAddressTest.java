package com.example.cqrs.domain;

import com.example.cqrs.domain.api.Purpose;
import com.example.cqrs.domain.api.command.ReserveEmailAddressCommand;
import com.example.cqrs.domain.api.event.EmailAddressDeniedEvent;
import com.example.cqrs.domain.api.event.EmailAddressReleasedEvent;
import com.example.cqrs.domain.api.event.EmailAddressReservedEvent;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CommandHandlingTest
public class ReserveEmailAddressTest {

    @Test
    public void reservesNewEmail(@Autowired CommandHandlingTestFixture<ReserveEmailAddressCommand> fixture) {
        fixture
                .givenNothing()
                .when(new ReserveEmailAddressCommand("alice@example.com", "alice", Purpose.SIGN_UP))
                .expectSuccessfulExecution()
                .expectSingleEvent(new EmailAddressReservedEvent("alice@example.com", "alice", Purpose.SIGN_UP));
    }

    @Test
    public void reservesAvailableEmail(@Autowired CommandHandlingTestFixture<ReserveEmailAddressCommand> fixture) {
        fixture
                .given(
                        new EmailAddressReservedEvent("alice@example.com", "alice", Purpose.SIGN_UP),
                        new EmailAddressReleasedEvent("alice@example.com")
                )
                .when(new ReserveEmailAddressCommand("alice@example.com", "bob", Purpose.SIGN_UP))
                .expectSuccessfulExecution()
                .expectSingleEvent(new EmailAddressReservedEvent("alice@example.com", "bob", Purpose.SIGN_UP));
    }

    @Test
    public void skipsIdempotentReservationBySameUser(@Autowired CommandHandlingTestFixture<ReserveEmailAddressCommand> fixture) {
        fixture
                .given(new EmailAddressReservedEvent("alice@example.com", "alice", Purpose.SIGN_UP))
                .when(new ReserveEmailAddressCommand("alice@example.com", "alice", Purpose.SIGN_UP))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }

    @Test
    public void deniesDifferentUser(@Autowired CommandHandlingTestFixture<ReserveEmailAddressCommand> fixture) {
        fixture
                .given(new EmailAddressReservedEvent("alice@example.com", "alice", Purpose.SIGN_UP))
                .when(new ReserveEmailAddressCommand("alice@example.com", "bob", Purpose.SIGN_UP))
                .expectSuccessfulExecution()
                .expectSingleEvent(new EmailAddressDeniedEvent("alice@example.com", "bob", Purpose.SIGN_UP));
    }
}
