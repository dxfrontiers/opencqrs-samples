package com.example.cqrs.domain;

import com.example.cqrs.domain.api.command.*;
import com.example.cqrs.domain.api.event.*;
import com.example.cqrs.domain.api.exception.*;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import com.opencqrs.framework.command.CommandRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@CommandHandlingTest
class UserAccountHandlingTest {

    // ─────────────────────────────────────────────────────────────────────
    // Tests using CommandHandlingTestFixture: every @CommandHandling method
    // is exercised in given–when–then style on its own aggregate stream.
    // ─────────────────────────────────────────────────────────────────────

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

        // The handler returns a boolean and no longer publishes
        // EmailAddressReservedEvent / EmailAddressDeniedEvent (commit 56137d7
        // "remove unnecessary event publishing") — these tests therefore only
        // assert the boolean result. The Reserved/Available state in `given`
        // still works because @StateRebuilding for those historical events
        // remains in place.

        @Test
        void reservesNewEmail(@Autowired CommandHandlingTestFixture<ReserveEmailAddressCommand> fixture) {
            fixture
                    .givenNothing()
                    .when(new ReserveEmailAddressCommand("alice@example.com", "alice"))
                    .expectSuccessfulExecution()
                    .expectResult(true)
                    .expectNoEvents();
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
                    .expectNoEvents();
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

    // ─────────────────────────────────────────────────────────────────────
    // TODO: @EventHandling unit tests (mocked CommandRouter).
    //
    // The methods below — annotated with @EventHandling("user") in
    // UserAccountHandling — run on the asynchronous EventHandlingProcessor and
    // dispatch follow-up commands via an injected CommandRouter. They are
    // therefore *not* command handlers and cannot be exercised through
    // CommandHandlingTestFixture, which only knows how to:
    //   1. seed an aggregate's event stream (given),
    //   2. invoke a single @CommandHandling method (when), and
    //   3. assert result/published events on the same aggregate.
    //
    // Cross-aggregate orchestration via router.send(...) lives outside the
    // fixture's scope — there is no real CommandRouter in the test slice and
    // no second aggregate to dispatch into. We therefore test these methods
    // as plain Java unit tests with a mocked CommandRouter, asserting the
    // exact follow-up command(s) dispatched for each branch. Boundary value
    // analysis on the boolean returned by ReserveEmailAddressCommand gives us
    // the two cases per initiator (granted=true / denied=false); the
    // EmailChangeCompletedEvent handler is unconditional and only needs one
    // case.
    // ─────────────────────────────────────────────────────────────────────
    @Nested
    class EventHandlingUnitTests {

        private UserAccountHandling sut;
        private CommandRouter router;

        @BeforeEach
        void setUp() {
            sut = new UserAccountHandling();
            router = mock(CommandRouter.class);
        }

        @Nested
        class OnSignUpInitiated {

            @Test
            void dispatchesCompleteSignUp_whenReservationGranted() {
                doReturn(true).when(router).send(any(ReserveEmailAddressCommand.class));

                sut.on(new SignUpInitiatedEvent("alice", "alice@example.com"), router);

                InOrder inOrder = inOrder(router);
                inOrder.verify(router).send(new ReserveEmailAddressCommand("alice@example.com", "alice"));
                inOrder.verify(router).send(new CompleteSignUpCommand("alice", "alice@example.com"));
                inOrder.verifyNoMoreInteractions();
            }

            @Test
            void dispatchesRejectSignUp_whenReservationDenied() {
                doReturn(false).when(router).send(any(ReserveEmailAddressCommand.class));

                sut.on(new SignUpInitiatedEvent("alice", "alice@example.com"), router);

                InOrder inOrder = inOrder(router);
                inOrder.verify(router).send(new ReserveEmailAddressCommand("alice@example.com", "alice"));
                inOrder.verify(router).send(new RejectSignUpCommand("alice", "alice@example.com"));
                inOrder.verifyNoMoreInteractions();
            }
        }

        @Nested
        class OnEmailChangeInitiated {

            @Test
            void dispatchesCompleteEmailChange_whenReservationGranted() {
                doReturn(true).when(router).send(any(ReserveEmailAddressCommand.class));

                sut.on(new EmailChangeInitiatedEvent("alice", "old@example.com", "new@example.com"), router);

                InOrder inOrder = inOrder(router);
                inOrder.verify(router).send(new ReserveEmailAddressCommand("new@example.com", "alice"));
                inOrder.verify(router).send(new CompleteEmailChangeCommand("alice"));
                inOrder.verifyNoMoreInteractions();
            }

            @Test
            void dispatchesRevertEmailChange_whenReservationDenied() {
                doReturn(false).when(router).send(any(ReserveEmailAddressCommand.class));

                sut.on(new EmailChangeInitiatedEvent("alice", "old@example.com", "new@example.com"), router);

                InOrder inOrder = inOrder(router);
                inOrder.verify(router).send(new ReserveEmailAddressCommand("new@example.com", "alice"));
                inOrder.verify(router).send(new RevertEmailChangeCommand("alice"));
                inOrder.verifyNoMoreInteractions();
            }
        }

        @Nested
        class OnEmailChangeCompleted {

            @Test
            void dispatchesReleaseEmailAddressForOldEmail() {
                sut.on(new EmailChangeCompletedEvent("alice", "old@example.com"), router);

                verify(router).send(new ReleaseEmailAddressCommand("old@example.com", "alice"));
                verifyNoMoreInteractions(router);
            }
        }
    }
}
