package com.example.cqrs.domain;

import com.example.cqrs.domain.api.command.*;
import com.example.cqrs.domain.api.event.*;
import com.example.cqrs.domain.api.exception.*;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CommandHandlingTest
class UserAccountHandlingTest {

    @Nested
    class CompleteSignUpCommandHandling {

        @Test
        void completesWhenRegistering(@Autowired CommandHandlingTestFixture<CompleteSignUpCommand> fixture) {
            fixture
                    .given(new SignUpInitiatedEvent("alice", "alice@example.com"))
                    .when(new CompleteSignUpCommand("alice", "alice@example.com"))
                    .expectSuccessfulExecution()
                    .expectSingleEvent(new SignUpCompletedEvent("alice", "alice@example.com"));
        }

        @Test
        void skipsWhenRegisteringForDifferentEmail(@Autowired CommandHandlingTestFixture<CompleteSignUpCommand> fixture) {
            fixture
                    .given(new SignUpInitiatedEvent("alice", "alice@example.com"))
                    .when(new CompleteSignUpCommand("alice", "other@example.com"))
                    .expectSuccessfulExecution()
                    .expectNoEvents();
        }

        @Test
        void skipsWhenAlreadyRegistered(@Autowired CommandHandlingTestFixture<CompleteSignUpCommand> fixture) {
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
        void skipsWhenChangingEmail(@Autowired CommandHandlingTestFixture<CompleteSignUpCommand> fixture) {
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
        void skipsWhenNotRegistered(@Autowired CommandHandlingTestFixture<CompleteSignUpCommand> fixture) {
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

    @Nested
    class RejectSignUpCommandHandling {

        @Test
        void rejectsWhenRegistering(@Autowired CommandHandlingTestFixture<RejectSignUpCommand> fixture) {
            fixture
                    .given(new SignUpInitiatedEvent("alice", "alice@example.com"))
                    .when(new RejectSignUpCommand("alice", "alice@example.com"))
                    .expectSuccessfulExecution()
                    .expectSingleEvent(new SignUpRejectedEvent("alice", "alice@example.com"));
        }

        @Test
        void skipsWhenAlreadyRejected(@Autowired CommandHandlingTestFixture<RejectSignUpCommand> fixture) {
            fixture
                    .given(
                            new SignUpInitiatedEvent("alice", "alice@example.com"),
                            new SignUpRejectedEvent("alice", "alice@example.com")
                    )
                    .when(new RejectSignUpCommand("alice", "alice@example.com"))
                    .expectSuccessfulExecution()
                    .expectNoEvents();
        }

        @Test
        void skipsWhenAlreadyRegistered(@Autowired CommandHandlingTestFixture<RejectSignUpCommand> fixture) {
            fixture
                    .given(
                            new SignUpInitiatedEvent("alice", "alice@example.com"),
                            new SignUpCompletedEvent("alice", "alice@example.com")
                    )
                    .when(new RejectSignUpCommand("alice", "alice@example.com"))
                    .expectSuccessfulExecution()
                    .expectNoEvents();
        }

        @Test
        void skipsWhenChangingEmail(@Autowired CommandHandlingTestFixture<RejectSignUpCommand> fixture) {
            fixture
                    .given(
                            new SignUpInitiatedEvent("alice", "alice@example.com"),
                            new SignUpCompletedEvent("alice", "alice@example.com"),
                            new EmailChangeInitiatedEvent("alice", "alice@example.com", "new@example.com")
                    )
                    .when(new RejectSignUpCommand("alice", "alice@example.com"))
                    .expectSuccessfulExecution()
                    .expectNoEvents();
        }
    }

    @Nested
    class ChangeEmailCommandHandling {

        @Test
        void publishesInitiatedWhenRegistered(@Autowired CommandHandlingTestFixture<ChangeEmailCommand> fixture) {
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
        void rejectsSameEmail(@Autowired CommandHandlingTestFixture<ChangeEmailCommand> fixture) {
            fixture
                    .given(
                            new SignUpInitiatedEvent("alice", "alice@example.com"),
                            new SignUpCompletedEvent("alice", "alice@example.com")
                    )
                    .when(new ChangeEmailCommand("alice", "alice@example.com"))
                    .expectException(SameEmailException.class);
        }

        @Test
        void rejectsDuringSignUp(@Autowired CommandHandlingTestFixture<ChangeEmailCommand> fixture) {
            fixture
                    .given(new SignUpInitiatedEvent("alice", "alice@example.com"))
                    .when(new ChangeEmailCommand("alice", "new@example.com"))
                    .expectException(SignUpPendingException.class);
        }

        @Test
        void rejectsWhileAnotherChangeInProgress(@Autowired CommandHandlingTestFixture<ChangeEmailCommand> fixture) {
            fixture
                    .given(
                            new SignUpInitiatedEvent("alice", "alice@example.com"),
                            new SignUpCompletedEvent("alice", "alice@example.com"),
                            new EmailChangeInitiatedEvent("alice", "alice@example.com", "pending@example.com")
                    )
                    .when(new ChangeEmailCommand("alice", "other@example.com"))
                    .expectException(EmailChangeInProgressException.class);
        }

        @Test
        void rejectsWhenAccountDisabled(@Autowired CommandHandlingTestFixture<ChangeEmailCommand> fixture) {
            fixture
                    .given(
                            new SignUpInitiatedEvent("alice", "alice@example.com"),
                            new SignUpRejectedEvent("alice", "alice@example.com")
                    )
                    .when(new ChangeEmailCommand("alice", "new@example.com"))
                    .expectException(AccountDisabledException.class);
        }
    }

    @Nested
    class CompleteEmailChangeCommandHandling {

        @Test
        void completesWhenChangingEmail(@Autowired CommandHandlingTestFixture<CompleteEmailChangeCommand> fixture) {
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
        void skipsWhenAlreadyCompleted(@Autowired CommandHandlingTestFixture<CompleteEmailChangeCommand> fixture) {
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
        void skipsWhenStillRegistering(@Autowired CommandHandlingTestFixture<CompleteEmailChangeCommand> fixture) {
            fixture
                    .given(new SignUpInitiatedEvent("alice", "alice@example.com"))
                    .when(new CompleteEmailChangeCommand("alice"))
                    .expectSuccessfulExecution()
                    .expectNoEvents();
        }

        @Test
        void skipsWhenNotRegistered(@Autowired CommandHandlingTestFixture<CompleteEmailChangeCommand> fixture) {
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

    @Nested
    class RevertEmailChangeCommandHandling {

        @Test
        void revertsWhenChangingEmail(@Autowired CommandHandlingTestFixture<RevertEmailChangeCommand> fixture) {
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
        void skipsWhenAlreadyReverted(@Autowired CommandHandlingTestFixture<RevertEmailChangeCommand> fixture) {
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
        void skipsWhenStillRegistering(@Autowired CommandHandlingTestFixture<RevertEmailChangeCommand> fixture) {
            fixture
                    .given(new SignUpInitiatedEvent("alice", "alice@example.com"))
                    .when(new RevertEmailChangeCommand("alice"))
                    .expectSuccessfulExecution()
                    .expectNoEvents();
        }

        @Test
        void skipsWhenNotRegistered(@Autowired CommandHandlingTestFixture<RevertEmailChangeCommand> fixture) {
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

    @Nested
    class ReserveEmailAddressCommandHandling {

        @Test
        void reservesNewEmail(@Autowired CommandHandlingTestFixture<ReserveEmailAddressCommand> fixture) {
            fixture
                    .givenNothing()
                    .when(new ReserveEmailAddressCommand("alice@example.com", "alice"))
                    .expectSuccessfulExecution()
                    .expectResult(true)
                    .expectSingleEvent(new EmailAddressReservedEvent("alice@example.com", "alice"));
        }

        @Test
        void reservesAvailableEmail(@Autowired CommandHandlingTestFixture<ReserveEmailAddressCommand> fixture) {
            fixture
                    .given(
                            new EmailAddressReservedEvent("alice@example.com", "alice"),
                            new EmailAddressReleasedEvent("alice@example.com")
                    )
                    .when(new ReserveEmailAddressCommand("alice@example.com", "bob"))
                    .expectSuccessfulExecution()
                    .expectResult(true)
                    .expectSingleEvent(new EmailAddressReservedEvent("alice@example.com", "bob"));
        }

        @Test
        void skipsIdempotentReservationBySameUser(@Autowired CommandHandlingTestFixture<ReserveEmailAddressCommand> fixture) {
            fixture
                    .given(new EmailAddressReservedEvent("alice@example.com", "alice"))
                    .when(new ReserveEmailAddressCommand("alice@example.com", "alice"))
                    .expectSuccessfulExecution()
                    .expectResult(true)
                    .expectNoEvents();
        }

        @Test
        void deniesDifferentUser(@Autowired CommandHandlingTestFixture<ReserveEmailAddressCommand> fixture) {
            fixture
                    .given(new EmailAddressReservedEvent("alice@example.com", "alice"))
                    .when(new ReserveEmailAddressCommand("alice@example.com", "bob"))
                    .expectSuccessfulExecution()
                    .expectResult(false)
                    .expectNoEvents();
        }
    }

    @Nested
    class ReleaseEmailAddressCommandHandling {

        @Test
        void releasesOwnReservation(@Autowired CommandHandlingTestFixture<ReleaseEmailAddressCommand> fixture) {
            fixture
                    .given(new EmailAddressReservedEvent("alice@example.com", "alice"))
                    .when(new ReleaseEmailAddressCommand("alice@example.com", "alice"))
                    .expectSuccessfulExecution()
                    .expectSingleEvent(new EmailAddressReleasedEvent("alice@example.com"));
        }

        @Test
        void skipsAlreadyReleased(@Autowired CommandHandlingTestFixture<ReleaseEmailAddressCommand> fixture) {
            fixture
                    .given(
                            new EmailAddressReservedEvent("alice@example.com", "alice"),
                            new EmailAddressReleasedEvent("alice@example.com")
                    )
                    .when(new ReleaseEmailAddressCommand("alice@example.com", "alice"))
                    .expectSuccessfulExecution()
                    .expectNoEvents();
        }

        @Test
        void skipsForeignReservation(@Autowired CommandHandlingTestFixture<ReleaseEmailAddressCommand> fixture) {
            fixture
                    .given(new EmailAddressReservedEvent("alice@example.com", "alice"))
                    .when(new ReleaseEmailAddressCommand("alice@example.com", "bob"))
                    .expectSuccessfulExecution()
                    .expectNoEvents();
        }
    }
}
