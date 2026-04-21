package com.example.cqrs.domain;

import com.opencqrs.framework.command.*;
import com.opencqrs.framework.eventhandler.EventHandling;
import com.example.cqrs.domain.UserAccount.Status;
import com.example.cqrs.domain.api.command.*;
import com.example.cqrs.domain.api.exception.*;
import com.example.cqrs.domain.api.event.*;
import org.springframework.beans.factory.annotation.Autowired;

import static com.example.cqrs.domain.api.Purpose.SIGN_UP;
import static com.example.cqrs.domain.api.Purpose.EMAIL_CHANGE;

@CommandHandlerConfiguration
public class UserAccountHandling {

    @CommandHandling
    public void handle(SignUpCommand command, CommandEventPublisher<UserAccount> publisher) {
        publisher.publish(new SignUpInitiatedEvent(command.username(), command.email()));
    }

    @StateRebuilding
    public UserAccount on(SignUpInitiatedEvent event) {
        return new UserAccount(event.username(), new Status.Registering(event.email()));
    }

    @EventHandling("user")
    public void on(SignUpInitiatedEvent event, @Autowired CommandRouter router) {
        router.send(new ReserveEmailAddressCommand(event.email(), event.username(), SIGN_UP));
    }

    @CommandHandling
    public void handle(UserAccount account, CompleteSignUpCommand command, CommandEventPublisher<UserAccount> publisher) {
        switch (account.status()) {
            case Status.Registering(String email) when email.equalsIgnoreCase(command.email()) ->
                    publisher.publish(new SignUpCompletedEvent(account.username(), email));
            case Status.Registering registering ->
                    throw new IllegalStateException("Completion email '" + command.email()
                            + "' does not match pending registration email '" + registering.email() + "'.");
            case Status.Registered _, Status.ChangingEmail _ -> { }
            case Status.NotRegistered _ ->
                    throw new IllegalStateException("Cannot complete sign-up: account is NotRegistered.");
        }
    }

    @StateRebuilding
    public UserAccount on(UserAccount account, SignUpCompletedEvent event) {
        return new UserAccount(account.username(), new Status.Registered(event.email()));
    }

    @CommandHandling
    public void handle(UserAccount account, RejectSignUpCommand command, CommandEventPublisher<UserAccount> publisher) {
        switch (account.status()) {
            case Status.Registering _ ->
                    publisher.publish(new SignUpRejectedEvent(account.username(), command.email()));
            case Status.NotRegistered _ -> { }
            case Status.Registered _ ->
                    throw new IllegalStateException("Cannot reject sign-up: account is already Registered.");
            case Status.ChangingEmail _ ->
                    throw new IllegalStateException("Cannot reject sign-up: account is changing email.");
        }
    }

    @StateRebuilding
    public UserAccount on(UserAccount account, SignUpRejectedEvent event) {
        return new UserAccount(account.username(), new Status.NotRegistered(event.email()));
    }

    @CommandHandling
    public void handle(UserAccount account, ChangeEmailCommand command, CommandEventPublisher<UserAccount> publisher) {
        switch (account.status()) {
            case Status.Registered(String email) when email.equalsIgnoreCase(command.newEmail()) ->
                    throw new SameEmailException();
            case Status.Registered(String email) ->
                    publisher.publish(new EmailChangeInitiatedEvent(account.username(), email, command.newEmail()));
            case Status.ChangingEmail _ -> throw new EmailChangeInProgressException();
            case Status.Registering _ -> throw new SignUpPendingException();
            case Status.NotRegistered _ -> throw new AccountDisabledException();
        }
    }

    @StateRebuilding
    public UserAccount on(UserAccount account, EmailChangeInitiatedEvent event) {
        return new UserAccount(account.username(), new Status.ChangingEmail(event.oldEmail(), event.newEmail()));
    }

    @EventHandling("user")
    public void on(EmailChangeInitiatedEvent event, @Autowired CommandRouter router) {
        router.send(new ReserveEmailAddressCommand(event.newEmail(), event.username(), EMAIL_CHANGE));
    }

    @CommandHandling
    public void handle(UserAccount account, CompleteEmailChangeCommand command, CommandEventPublisher<UserAccount> publisher) {
        switch (account.status()) {
            case Status.ChangingEmail changing ->
                    publisher.publish(new EmailChangeCompletedEvent(account.username(), changing.email()));
            case Status.Registered _ -> { }
            case Status.Registering _ ->
                    throw new IllegalStateException("Cannot complete email change: sign-up is still pending.");
            case Status.NotRegistered _ ->
                    throw new IllegalStateException("Cannot complete email change: account is NotRegistered.");
        }
    }

    @StateRebuilding
    public UserAccount on(UserAccount account, EmailChangeCompletedEvent event) {
        return switch (account.status()) {
            case Status.ChangingEmail changing ->
                    new UserAccount(account.username(), new Status.Registered(changing.newEmail()));
            case Status.Registering _, Status.Registered _, Status.NotRegistered _ -> account;
        };
    }

    @EventHandling("user")
    public void on(EmailChangeCompletedEvent event, @Autowired CommandRouter router) {
        router.send(new ReleaseEmailAddressCommand(event.oldEmail(), event.username()));
    }

    @CommandHandling
    public void handle(UserAccount account, RevertEmailChangeCommand command, CommandEventPublisher<UserAccount> publisher) {
        switch (account.status()) {
            case Status.ChangingEmail _ ->
                    publisher.publish(new EmailChangeRevertedEvent(account.username()));
            case Status.Registered _ -> { }
            case Status.Registering _ ->
                    throw new IllegalStateException("Cannot revert email change: sign-up is still pending.");
            case Status.NotRegistered _ ->
                    throw new IllegalStateException("Cannot revert email change: account is NotRegistered.");
        }
    }

    @StateRebuilding
    public UserAccount on(UserAccount account, EmailChangeRevertedEvent event) {
        return switch (account.status()) {
            case Status.ChangingEmail changing ->
                    new UserAccount(account.username(), new Status.Registered(changing.email()));
            case Status.Registering _, Status.Registered _, Status.NotRegistered _ -> account;
        };
    }

    @CommandHandling
    public UserAccount handle(UserAccount account, GetUserAccountCommand command) {
        return account;
    }

    @CommandHandling(sourcingMode = SourcingMode.LOCAL)
    public boolean handle(EmailAddress state, ReserveEmailAddressCommand command, CommandEventPublisher<EmailAddress> publisher) {
        final EmailAddressReservedEvent reservedEvent =
                new EmailAddressReservedEvent(command.email(), command.username(), command.purpose());
        return switch (state) {
            case null -> {
                publisher.publish(reservedEvent);
                yield true;
            }
            case EmailAddress.Available _ -> {
                publisher.publish(reservedEvent);
                yield true;
            }
            case EmailAddress.Reserved reserved when reserved.username().equals(command.username()) ->
                    true;
            case EmailAddress.Reserved _ -> {
                publisher.publish(new EmailAddressDeniedEvent(command.email(), command.username(), command.purpose()));
                yield false;
            }
        };
    }

    @StateRebuilding
    public EmailAddress on(EmailAddressReservedEvent event) {
        return new EmailAddress.Reserved(event.email(), event.username());
    }

    @EventHandling("user")
    public void on(EmailAddressReservedEvent event, @Autowired CommandRouter router) {
        switch (event.purpose()) {
            case SIGN_UP -> router.send(new CompleteSignUpCommand(event.username(), event.email()));
            case EMAIL_CHANGE -> router.send(new CompleteEmailChangeCommand(event.username()));
        }
    }

    @EventHandling("user")
    public void on(EmailAddressDeniedEvent event, @Autowired CommandRouter router) {
        switch (event.purpose()) {
            case SIGN_UP -> router.send(new RejectSignUpCommand(event.username(), event.email()));
            case EMAIL_CHANGE -> router.send(new RevertEmailChangeCommand(event.username()));
        }
    }

    @CommandHandling
    public void handle(EmailAddress state, ReleaseEmailAddressCommand command, CommandEventPublisher<EmailAddress> publisher) {
        switch (state) {
            case EmailAddress.Reserved reserved when reserved.username().equals(command.username()) ->
                    publisher.publish(new EmailAddressReleasedEvent(command.email()));
            case EmailAddress.Available _ -> { }
            case EmailAddress.Reserved _ ->
                    throw new IllegalStateException("Cannot release: email is reserved by a different user.");
            case null ->
                    throw new IllegalStateException("Cannot release: email has never been reserved.");
        }
    }

    @StateRebuilding
    public EmailAddress on(EmailAddressReleasedEvent event) {
        return new EmailAddress.Available(event.email());
    }
}
