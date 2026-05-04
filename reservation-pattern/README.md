# Achieving Cross-Aggregate Consistency: The Reservation Pattern

-----

**NOTE**

This tutorial assumes you have completed the official [OpenCQRS tutorial](https://docs.opencqrs.com/tutorials/).

-----

In an event-sourced system, each aggregate owns exactly one event stream and can only validate its own state — a constraint like "an email address must be unique system-wide" requires explicit coordination between two aggregates.

The **Reservation Pattern** solves this with a dedicated **Index-Aggregate** acting as a lock, plus **asynchronous orchestration** via `@EventHandling`-driven child commands. No central saga; instead, **idempotent command handlers** guarantee safe coordination on retry, replay, and failure.

## The Two Aggregates

The [`UserAccount`](src/main/java/com/example/cqrs/domain/UserAccount.java) models user identity. Addressed via `/user-accounts/{username}`, uniqueness is enforced by [`SubjectCondition.PRISTINE`](https://docs.opencqrs.com/reference/extension_points/command_handler/) on [`SignUpCommand`](src/main/java/com/example/cqrs/domain/api/command/SignUpCommand.java) — the framework checks the subject's stream is empty before invoking the handler, so a duplicate username is rejected synchronously by `commandRouter.send(...)`. Sealed [`Status`](src/main/java/com/example/cqrs/domain/UserAccount.java): `Registering`, `Registered`, `ChangingEmail`, `NotRegistered`.

The [`EmailAddress`](src/main/java/com/example/cqrs/domain/EmailAddress.java) aggregate is a pure **Index-Aggregate** for email-address uniqueness: a reservation mechanism with two states, `Reserved` and `Available`. Addressed via `/email-addresses/{SHA256(email)}`, the raw email is SHA-256-hashed because ESDB subjects only accept path segments matching a restricted character set (see [`ReserveEmailAddressCommand`](src/main/java/com/example/cqrs/domain/api/command/ReserveEmailAddressCommand.java) and the [EventSourcingDB subject rules](https://docs.eventsourcingdb.io/fundamentals/subjects/)) — `@` and `.` in email addresses would otherwise be rejected. A dedicated `Subject` value type is planned so this encoding no longer has to live inside every command. The [`ReserveEmailAddressCommand`](src/main/java/com/example/cqrs/domain/api/command/ReserveEmailAddressCommand.java) handler returns a `boolean` — `true` if the address was reserved (or the caller already owned it), `false` if it is held by another user. The calling `@EventHandling` method already knows whether it's orchestrating a sign-up or an email change, so it can pick the right follow-up command itself; no `Purpose` marker has to be carried around.

## Why asynchronous Command → Event → Command?

A command handler can atomically modify only one aggregate — events published via `publisher.publish(...)` are queued and committed as one batch on that handler's subject ([CommandRouter reference](https://docs.opencqrs.com/reference/core_components/command_router/)). There are no cross-subject transactions in EventSourcingDB.

Cross-aggregate orchestration therefore uses two primitives:

- **`publisher.publish(event)`** — queues an event on the **current** subject. Committed atomically when the handler returns.
- **`router.send(otherCommand)`** — dispatches a separate command to the aggregate owning `otherCommand.getSubject()`. Runs in its own transaction and returns the handler's result to the caller.

Here, `router.send` is invoked from **`@EventHandling("user")` methods** in [`UserAccountHandling`](src/main/java/com/example/cqrs/domain/UserAccountHandling.java) (sign-up orchestrator: [`UserAccountHandling.java#L24-L31`](src/main/java/com/example/cqrs/domain/UserAccountHandling.java#L24-L31)). These run in the asynchronous `EventHandlingProcessor` ([reference](https://docs.opencqrs.com/reference/core_components/event_handling_processor/)) after the triggering event has been committed. The processor has per-group progress tracking and retry policies. The same handler then inspects the boolean returned by the reservation and dispatches the success-or-fail follow-up command directly, keeping the orchestration logic in one place.

The reservation itself lives on the index aggregate ([`UserAccountHandling.java#L152-L167`](src/main/java/com/example/cqrs/domain/UserAccountHandling.java#L152-L167)) and is exhaustive over the four possible incoming states. Only `null` and `Available` publish; the two `Reserved` arms are the idempotency / denial pivot:

```java
case EmailAddress.Reserved r when r.username().equals(command.username()) -> true;   // already owned by caller, no event
case EmailAddress.Reserved _                                              -> false;  // held by someone else, no event
```

`SourcingMode.LOCAL` on that handler keeps it scoped to events on the `/email-addresses/{hash}` subject only ([SourcingMode reference](https://docs.opencqrs.com/reference/extension_points/command_handler/)).

Consequence for the HTTP layer: the terminal outcome is established several async steps after the initial command, so the [`UserController`](src/main/java/com/example/cqrs/http/UserController.java) returns **`202 Accepted`** and the client polls `GET /api/user-accounts/{username}` for the final state. Synchronous rejections — duplicate username, same-email, change on a disabled account, and similar — surface as exceptions from `commandRouter.send(...)` and are mapped to the appropriate HTTP status code by [`ApiExceptionHandler`](src/main/java/com/example/cqrs/http/ApiExceptionHandler.java). Failures that arise later inside `@EventHandling` stay on the processor thread and are covered by the retry policy, never the client response.

## Workflows

The lifecycle of both aggregates is captured in the Mermaid sequence diagram below. Each participant is a swim-lane: `Client`, the two aggregates (`UserAccount`, `EmailAddress`), [`UserAccountHandling`](src/main/java/com/example/cqrs/domain/UserAccountHandling.java) (the class hosting the `@EventHandling("user")` methods that the framework's `EventHandlingProcessor` invokes), and `ESDB` on the far right as the durable event store. The boolean returned by `ReserveEmailAddressCommand` drives the branch directly inside the `@EventHandling` method.

Self-arrows on each aggregate lifeline depict the `@StateRebuilding` step — the aggregate is the lifeline, and every event causes a state change immediately at `publisher.publish(...)`, not only on the next command. The corresponding **`append <Event>` arrow to ESDB** makes the persistence boundary explicit, and the dashed return from ESDB to `UserAccountHandling` shows the asynchronous subscription that drives the cross-aggregate orchestration.

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant UA as UserAccount
    participant EHP as UserAccountHandling
    participant EA as EmailAddress
    participant ESDB

    rect rgb(240,248,255)
    Note over Client,ESDB: Sign-Up workflow
    Client->>+UA: SignUpCommand
    UA->>UA: SignUpInitiatedEvent<br/>∅ → Registering(email)
    UA->>ESDB: append SignUpInitiatedEvent
    UA-->>-Client: 202 Accepted
    ESDB-->>EHP: SignUpInitiatedEvent (subscribed)
    EHP->>+EA: ReserveEmailAddressCommand(email, user)
    alt email Available (or null)
        EA->>EA: EmailAddressReservedEvent<br/>∅/Available → Reserved(email, user)
        EA->>ESDB: append EmailAddressReservedEvent
        EA-->>-EHP: true
        EHP->>+UA: CompleteSignUpCommand
        UA->>UA: SignUpCompletedEvent<br/>Registering → Registered(email)
        UA->>ESDB: append SignUpCompletedEvent
        UA-->>-EHP: ✓
    else email already Reserved by another user
        Note over EA,ESDB: stays Reserved — no event, nothing appended
        EA-->>EHP: false
        EHP->>+UA: RejectSignUpCommand
        UA->>UA: SignUpRejectedEvent<br/>Registering → NotRegistered(email)
        UA->>ESDB: append SignUpRejectedEvent
        UA-->>-EHP: ✓
    end
    end

    rect rgb(245,255,240)
    Note over Client,ESDB: Change-Email workflow (starts from Registered)
    Client->>+UA: ChangeEmailCommand(newEmail)
    UA->>UA: EmailChangeInitiatedEvent<br/>Registered(old) → ChangingEmail(old, new)
    UA->>ESDB: append EmailChangeInitiatedEvent
    UA-->>-Client: 202 Accepted
    ESDB-->>EHP: EmailChangeInitiatedEvent (subscribed)
    EHP->>+EA: ReserveEmailAddressCommand(new, user)
    alt new email Available
        EA->>EA: EmailAddressReservedEvent<br/>∅/Available → Reserved(new, user)
        EA->>ESDB: append EmailAddressReservedEvent
        EA-->>-EHP: true
        EHP->>+UA: CompleteEmailChangeCommand
        UA->>UA: EmailChangeCompletedEvent<br/>ChangingEmail → Registered(new)
        UA->>ESDB: append EmailChangeCompletedEvent
        UA-->>-EHP: ✓
        EHP->>EA: ReleaseEmailAddressCommand(old, user)
        EA->>EA: EmailAddressReleasedEvent<br/>Reserved(old) → Available(old)
        EA->>ESDB: append EmailAddressReleasedEvent
    else new email already Reserved
        EA-->>EHP: false
        EHP->>+UA: RevertEmailChangeCommand
        UA->>UA: EmailChangeRevertedEvent<br/>ChangingEmail → Registered(old)
        UA->>ESDB: append EmailChangeRevertedEvent
        UA-->>-EHP: ✓
    end
    end
```

### Sign-Up Workflow

The numbers below refer to the autonumbered steps in the diagram above.

**Synchronous part (steps 1–4).** The client `POST`s `SignUpCommand` (1). The duplicate-username guard sits *outside* the handler: [`SubjectCondition.PRISTINE`](src/main/java/com/example/cqrs/domain/api/command/SignUpCommand.java) on the command — the framework rejects the call before invoking the handler if the `/user-accounts/{username}` stream isn't empty. That's why a collision surfaces synchronously as an HTTP error and the handler stays trivial: it just publishes `SignUpInitiatedEvent`, which `@StateRebuilding` applies to take `UserAccount` from `∅` to `Registering(email)` (2). The event is appended to ESDB (3); [`UserController`](src/main/java/com/example/cqrs/http/UserController.java#L26-L30) returns `202 Accepted` (4) because the terminal outcome — completed or rejected — only materialises after the async leg.

**Async orchestration (steps 5–13 happy path).** ESDB pushes the appended event to [`UserAccountHandling#on(SignUpInitiatedEvent, …)`](src/main/java/com/example/cqrs/domain/UserAccountHandling.java#L24-L31) via subscription (5). The orchestrator fires `ReserveEmailAddressCommand` against the index aggregate (6). The reservation handler ([`UserAccountHandling.java#L152-L167`](src/main/java/com/example/cqrs/domain/UserAccountHandling.java#L152-L167)) publishes `EmailAddressReservedEvent` only on the `null`/`Available` arms; the `Reserved`-by-same-user arm returns `true` without publishing (idempotent re-delivery), and the `Reserved`-by-someone-else arm returns `false`. On `true` (9) the orchestrator dispatches `CompleteSignUpCommand` (10), which moves `UserAccount` to `Registered(email)` (11–13). Keeping orchestrator, reservation, and follow-up commands in the **same class** is the central design choice — the boolean stays a method-local variable instead of being smuggled through a Saga or carried as a `Purpose` field on events.

**Denial branch (steps 14–18).** When the email is already reserved by another user, the handler returns `false` **without publishing anything** — that's why the diagram shows no `EA→EA` self-arrow and no `EA→ESDB` append in this branch. The orchestrator dispatches `RejectSignUpCommand` (15), and `UserAccount` settles in the explicit `NotRegistered(email)` terminal state (16–18) instead of an absence-of-state — that's what makes the rejected sign-up replayable and observable through `GET /api/user-accounts/{username}`.

**Idempotency invariant.** Every command handler in [`UserAccountHandling`](src/main/java/com/example/cqrs/domain/UserAccountHandling.java) is an exhaustive `switch` over `Status` (or `EmailAddress`), with a no-op branch for every off-flow state. A re-fired `CompleteSignUpCommand` against an already-`Registered` account hits a no-op arm and produces no event — that's the load-bearing property that lets at-least-once redelivery and event replay run safely.

### Change-Email Workflow

Starts from `Registered` — the terminal state of the successful sign-up branch. The pattern reuses the **same** `EmailAddress` index aggregate and the **same** orchestrator class.

**Synchronous gate (steps 19–22).** Unlike sign-up, the change-email guard cannot be expressed via `SubjectCondition` — uniqueness against a *new* email is exactly what the index aggregate is for. Instead, the [`ChangeEmailCommand` handler](src/main/java/com/example/cqrs/domain/UserAccountHandling.java#L71-L82) enumerates every `Status` and throws a typed domain exception (`SameEmailException`, `EmailChangeInProgressException`, `SignUpPendingException`, `AccountDisabledException`) for any non-`Registered` arm; [`ApiExceptionHandler`](src/main/java/com/example/cqrs/http/ApiExceptionHandler.java) maps each one to the appropriate HTTP status before ESDB ever sees the request. On the happy arm, `UserAccount` transitions to `ChangingEmail(old, new)` (20) — and the design choice that pays off later is **carrying both addresses** in this status: it is what makes the revert branch and the old-address release possible without rereading prior events.

**Happy path (steps 23–34).** The async leg mirrors sign-up: ESDB delivers the event (23), the orchestrator reserves the **new** address (24–26) using the same reservation handler. On `true` (27), `CompleteEmailChangeCommand` (28) takes `UserAccount` to `Registered(new)` (29–31). A *separate* [`@EventHandling` on `EmailChangeCompletedEvent`](src/main/java/com/example/cqrs/domain/UserAccountHandling.java#L121-L124) then frees the **old** address with `ReleaseEmailAddressCommand` (32–34). The release handler ([`UserAccountHandling.java#L174-L184`](src/main/java/com/example/cqrs/domain/UserAccountHandling.java#L174-L184)) only publishes when the caller still owns the reservation:

```java
case EmailAddress.Reserved r when r.username().equals(command.username()) ->
        publisher.publish(new EmailAddressReleasedEvent(command.email()));
```

The two other arms (`Available` and `Reserved`-by-someone-else) are silent no-ops — re-delivery is harmless.

**Revert branch (steps 35–39).** On `false` (35) — the new email is held by another user — `RevertEmailChangeCommand` (36) takes `UserAccount` back to `Registered(old)` (37–39). The old address stays reserved by this user, so no compensating release is needed; the only ESDB write in this branch is the revert event itself (38).

## Running the App

To run the app, ensure you have [Docker](https://www.docker.com/) installed on your system as well as being logged into the [GitHub Container Registry](https://docs.github.com/de/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authentifizieren-bei-der-container-registry).

Then run:

```bash
docker-compose up
```

This command will start:

- An instance of EventSourcingDB.
- An instance of the app itself.

To interact with the app, we provide a [collection](clients) of requests for the [Bruno](https://www.usebruno.com/) API client.
