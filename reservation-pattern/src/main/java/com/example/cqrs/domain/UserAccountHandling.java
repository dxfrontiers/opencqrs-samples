package com.example.cqrs.domain;

import com.opencqrs.framework.command.*;
import com.example.cqrs.domain.UserAccount.Status;
import com.example.cqrs.domain.api.command.*;
import com.example.cqrs.domain.api.event.*;
import org.springframework.beans.factory.annotation.Autowired;

import static com.example.cqrs.domain.api.Purpose.SIGN_UP;
import static com.example.cqrs.domain.api.Purpose.EMAIL_CHANGE;

@CommandHandlerConfiguration
public class UserAccountHandling {

    @CommandHandling
    public boolean handle(SignUpCommand command,
                          CommandEventPublisher<UserAccount> publisher,
                          @Autowired CommandRouter router) {

        publisher.publish(new SignUpInitiatedEvent(command.username(), command.email()));

        boolean reserved = router.send(
                new ReserveEmailAddressCommand(command.email(), command.username(), SIGN_UP));

        if (reserved) {
            publisher.publish(new SignUpCompletedEvent(command.username(), command.email()));
            return true;
        } else {
            publisher.publish(new SignUpRejectedEvent(command.username(), command.email()));
            return false;
        }
    }

    @StateRebuilding
    public UserAccount on(SignUpInitiatedEvent event) {
        return new UserAccount(event.username(), new Status.Registering(event.email()));
    }

    @StateRebuilding
    public UserAccount on(UserAccount account, SignUpCompletedEvent event) {
        return new UserAccount(account.username(), new Status.Registered(event.email()));
    }

    @StateRebuilding
    public UserAccount on(UserAccount account, SignUpRejectedEvent event) {
        return new UserAccount(account.username(), new Status.NotRegistered(event.email()));
    }

    @CommandHandling
    public boolean handle(UserAccount account, ChangeEmailCommand command,
                          CommandEventPublisher<UserAccount> publisher,
                          @Autowired CommandRouter router) {

        return switch (account.status()) {
            case Status.Registered(String email) when email.equalsIgnoreCase(command.newEmail()) -> {
                throw new IllegalArgumentException("New email is the same as the current one.");
            }
            case Status.Registered(String email) -> {
                publisher.publish(new EmailChangeInitiatedEvent(account.username(), email, command.newEmail()));

                boolean reserved = router.send(new ReserveEmailAddressCommand(command.newEmail(), account.username(), EMAIL_CHANGE));

                if (reserved) {
                    publisher.publish(new EmailChangeCompletedEvent(account.username(), email));
                    router.send(new ReleaseEmailAddressCommand(email, account.username()));
                    yield true;
                } else {
                    publisher.publish(new EmailChangeRevertedEvent(account.username()));
                    yield false;
                }
            }
            case Status.ChangingEmail _ -> throw new IllegalStateException("Another email change is already in progress.");
            case Status.Registering _ -> throw new IllegalStateException("Cannot change email: sign-up is still pending.");
            case Status.NotRegistered _ -> throw new IllegalStateException("Cannot change email: account is NotRegistered.");
        };
    }

    @StateRebuilding
    public UserAccount on(UserAccount account, EmailChangeInitiatedEvent event) {
        return new UserAccount(account.username(), new Status.ChangingEmail(event.oldEmail(), event.newEmail()));
    }

    @StateRebuilding
    public UserAccount on(UserAccount account, EmailChangeCompletedEvent event) {
        return switch (account.status()) {
            case Status.ChangingEmail changing ->
                    new UserAccount(account.username(), new Status.Registered(changing.newEmail()));
            case Status.Registering _, Status.Registered _, Status.NotRegistered _ -> account;
        };
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
        final EmailAddressReservedEvent reservedEvent = new EmailAddressReservedEvent(command.email(), command.username(), command.purpose());
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

    @CommandHandling
    public void handle(EmailAddress state, ReleaseEmailAddressCommand command, CommandEventPublisher<EmailAddress> publisher) {
        switch (state) {
            case EmailAddress.Reserved reserved when reserved.username().equals(command.username()) ->
                    publisher.publish(new EmailAddressReleasedEvent(command.email()));
            case EmailAddress.Available _ -> { /* replay — already released */ }
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
