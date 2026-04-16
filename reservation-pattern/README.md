# Reservation Pattern: Cross-Aggregate Consistency through Idempotency

-----

**NOTE**

This tutorial assumes you have completed the official [OpenCQRS-tutorial](https://docs.opencqrs.com/tutorials/).

-----

In an event-sourced system, each aggregate owns exactly one event stream.
A single aggregate can only validate its own state — it cannot enforce constraints across streams.
When a value like an email address must be unique system-wide, explicit coordination is needed.

The **Reservation Pattern** solves this with a dedicated auxiliary aggregate that acts as a lock.
No central saga is required. Instead, **idempotent preconditions in the command handlers** plus synchronous cross-aggregate orchestration via `router.send` guarantee safe coordination between the two aggregates.

## Why Command → Event → Command?

A natural question when looking at this implementation: why not just modify both aggregates in a single handler? Why the indirection through commands and events?

The answer is a hard constraint of event sourcing: **a command handler can only modify exactly one aggregate atomically.** Each aggregate owns its own event stream, and events are appended to that stream as a single unit. There are no distributed transactions across streams.

Cross-aggregate coordination therefore needs two distinct primitives:

- **`publisher.publish(event)`** — appends an event to the event stream of the **current** aggregate. The event is queued and committed atomically when the handler returns. `publish` never crosses aggregate boundaries — every event published from a handler ends up on the handler's own subject.

- **`router.send(otherCommand)`** — dispatches a **separate child command** to whichever aggregate owns `otherCommand.getSubject()`. The child runs in its own transaction, loads its own state via `@StateRebuilding`, calls its own handler, commits its own events, and returns its result.

In the previous version of this sample, `router.send` was called from `@EventHandling` methods that the `EventHandlingProcessor` invoked asynchronously after each event was committed. The REST controller returned `202 Accepted` and the client had to poll for the outcome.

This version moves the orchestration into the command handler itself: `publisher.publish` queues events locally, `router.send` reaches across to another aggregate in its own transaction and returns a typed result (here: `boolean`), and all locally queued events commit atomically on the parent subject when the handler returns. The **Command → Event → Command → Event** chain still exists — every intermediate state is a first-class aggregate state visible in the event stream — but the "who dispatches the next command" responsibility now sits inside the handler rather than in the `EventHandlingProcessor`. The result: the controller can read the handler's boolean return value and respond synchronously with `201 Created` or `422 Unprocessable Content`.

## The Two Aggregates

The [`UserAccount`](src/main/java/com/example/cqrs/domain/UserAccount.java) represents the identity and lifecycle of a user. It is addressed via `/user-accounts/{username}` and its uniqueness is guaranteed by `SubjectCondition.PRISTINE` on the [`SignUpCommand`](src/main/java/com/example/cqrs/domain/api/command/SignUpCommand.java). Its states — `Registering`, `Registered`, `ChangingEmail`, `NotRegistered` — are captured in a sealed [`Status`](src/main/java/com/example/cqrs/domain/UserAccount.java) interface and are shown as green nodes in the workflow diagrams below.

The [`EmailAddress`](src/main/java/com/example/cqrs/domain/EmailAddress.java) aggregate exists solely as a reservation mechanism for email uniqueness. It is addressed via `/email-addresses/{SHA256(email)}` and has no business purpose of its own. Its states — `Reserved` and `Available` — are shown as green nodes in the diagrams; the initial "no events yet" case is `null` and handled explicitly in the reservation handler. A [`Purpose`](src/main/java/com/example/cqrs/domain/api/Purpose.java) enum (`SIGN_UP` / `EMAIL_CHANGE`) is stored in the reservation and denial events as audit metadata.

## Consistency Problems this Implementation Solves

A naive implementation of cross-aggregate email uniqueness runs into three problems, which all share the same cause: the `EmailAddress` aggregate gives no signal back when a reservation fails.

If a sign-up fails because the email is already taken, the `UserAccount` stays in **Registering** forever. No event signals the failure, no command transitions the account out of this state. This implementation lets the [`ReserveEmailAddressCommand` handler](src/main/java/com/example/cqrs/domain/UserAccountHandling.java) publish an `EmailAddressDeniedEvent` when the email belongs to another user and return `false`. The calling `SignUpCommand` handler reacts to that boolean by publishing `SignUpRejectedEvent`, transitioning the account to **NotRegistered**.

The same problem occurs with email changes: the `ChangeEmailCommand` handler guards against a change already in progress, but if the reservation of the new address fails, the account would be stuck in **ChangingEmail**. The same `boolean` mechanism therefore causes the handler to publish `EmailChangeRevertedEvent` and restore the account to **Registered** with the original email.

Both issues are amplified by a race condition: when two users register with the same email simultaneously, only one reservation succeeds. The reservation handler distinguishes three cases. If the email has not yet been reserved, it is reserved. If it is already reserved by the same user (replay), the command returns `true` idempotently. If it belongs to a different user, an `EmailAddressDeniedEvent` is published and `false` is returned.

## Race Conditions and Replay Behavior

When a command arrives, the `CommandRouter` reads all events for the target subject, rebuilds state via `@StateRebuilding`, and invokes the handler. When the handler publishes an event, the framework writes it to the EventSourcingDB with a precondition: the last event on the subject must still be the same as when it was read. If another process wrote in between, the write fails with a `ConcurrencyException`. The framework then retries with fresh state.

**Two sign-ups with the same username:** Both target `/user-accounts/alice` with `SubjectCondition.PRISTINE`. One wins. The other fails with `CommandSubjectAlreadyExistsException`, which the framework maps to `409 Conflict`.

**Two sign-ups with the same email, different usernames:** Both handlers publish their `SignUpInitiatedEvent` (queued) and both call `router.send(ReserveEmailAddressCommand)` targeting the same `/email-addresses/{hash}` subject. Optimistic locking on the email subject serializes them — one reservation wins, the other retries with fresh state, sees `Reserved` by a different user, publishes `EmailAddressDeniedEvent`, and returns `false`. The losing handler then publishes `SignUpRejectedEvent`. Both `UserAccount` subjects end up with two events each (Initiated + Completed or Initiated + Rejected), committed atomically per subject.

**Two parallel email changes for the same user:** Both target `/user-accounts/alice`. Optimistic locking serializes them — one transitions the account to `ChangingEmail`, the second retries, matches the `case Status.ChangingEmail` branch, and throws `IllegalStateException`.

**Replay after restart:** Every event in the store is replayed through `@StateRebuilding`. No `@EventHandling` fires because the sample uses pure synchronous orchestration. The aggregate states are rebuilt deterministically. The intermediate `Registering` and `ChangingEmail` states are visible to anyone reading the event stream at historical positions between consecutive events; they are not visible to a live GET request because consecutive events in one handler commit atomically.

**JVM crash between `router.send(Reserve)` commit and `UserAccount` batch commit:** The `EmailAddress` reservation is durable but the `UserAccount` has no events yet. A client retry of the same `SignUpCommand` passes `PRISTINE` (no `UserAccount` events), the handler runs, calls `router.send(Reserve)`, the reservation handler matches "Reserved by same user" and idempotently returns `true` without publishing a duplicate event, the handler then publishes `SignUpInitiatedEvent` + `SignUpCompletedEvent` and both commit atomically. The workflow self-heals.

## Idempotency through Preconditions

Every [command handler](src/main/java/com/example/cqrs/domain/UserAccountHandling.java) checks the aggregate state before publishing. The sealed `Status` interface makes these checks type-safe via **exhaustive switch statements** — every branch is covered explicitly, no `default`, no `instanceof`. Each branch decides between three outcomes: publish an event, skip silently, or throw an exception. The choice follows one rule:

**Idempotent skip** — a command is silently skipped when the target state has already been reached by an earlier execution. This guarantees consistency on event replay and on multiple command dispatches for the same logical transition.

> Example: `ReserveEmailAddressCommand` arrives for an email already reserved by the same user. This is the expected state after a crash-recovery retry — the reservation must not publish a duplicate event, it simply returns `true`.

**Throw an exception** — a command is rejected when the current state is logically impossible for this command. Replay cannot produce this state; it signals a programming or data-integrity bug that must not be silently swallowed.

> Example: `ChangeEmailCommand` arrives on an account that is `Registering`. You cannot change an email before the sign-up itself is finished — this state is unreachable via any normal or replayed flow, so the handler throws `IllegalStateException`.

The [`ReserveEmailAddressCommand` handler](src/main/java/com/example/cqrs/domain/UserAccountHandling.java) uses `SourcingMode.LOCAL` and checks ownership before deciding between idempotent skip, denial (publishing `EmailAddressDeniedEvent` and returning `false`), or a new reservation (publishing `EmailAddressReservedEvent` and returning `true`).

## Workflows

Each workflow follows the **Command → Event → Command → Event …** chain described above. All transitions happen synchronously within a single handler invocation. States are color-coded by aggregate: **green for positive states**, **red for negative states** (`NotRegistered`).

### Sign-Up

![Sign-Up Workflow](diagrams/signup.svg)

### Change-Email

![Change-Email Workflow](diagrams/change-email.svg)

## Running the App

To run the app, ensure you have [Docker](https://www.docker.com/) installed on your system as well as being logged into the [GitHub Container Registry](https://docs.github.com/de/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authentifizieren-bei-der-container-registry).

Then run:

```bash
docker-compose up
```

This command will start:

- An instance of EventSourcingDB.
- An instance of the app itself.

To interact with the app, we provide the [`test-api.sh`](test-api.sh) script that exercises the full sign-up and change-email flows against the running instance.
