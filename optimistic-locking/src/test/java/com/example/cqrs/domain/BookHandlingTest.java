package com.example.cqrs.domain;

import com.example.cqrs.domain.api.command.CorrectBookDetailsCommand;
import com.example.cqrs.domain.api.command.PurchaseBookCommand;
import com.example.cqrs.domain.api.event.BookDetailsCorrectedEvent;
import com.example.cqrs.domain.api.event.BookPurchasedEvent;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ConcurrentModificationException;
import java.util.List;

@CommandHandlingTest
public class BookHandlingTest {

    private static final String ISBN = "978-0-261-10295-4";
    private static final String TITLE = "The Lord of the Rings";
    private static final List<String> AUTHORS = List.of("J.R.R. Tolkien");

    @Test
    public void purchaseBookPublishesPurchasedEvent(
            @Autowired CommandHandlingTestFixture<PurchaseBookCommand> fixture) {
        fixture
                .givenNothing()
                .when(new PurchaseBookCommand(ISBN, TITLE, AUTHORS))
                .expectSuccessfulExecution()
                .expectSingleEvent(new BookPurchasedEvent(ISBN, TITLE, AUTHORS));
    }

    @Test
    public void correctBookDetailsPublishesCorrectionWhenTitleChanges(
            @Autowired CommandHandlingTestFixture<CorrectBookDetailsCommand> fixture) {
        fixture
                .given(event -> event
                        .id("event-1")
                        .payload(new BookPurchasedEvent(ISBN, TITLE, AUTHORS)))
                .when(new CorrectBookDetailsCommand(
                        ISBN,
                        "The Fellowship of the Ring",
                        AUTHORS,
                        "event-1"))
                .expectSuccessfulExecution()
                .expectSingleEvent(new BookDetailsCorrectedEvent(
                        ISBN,
                        "The Fellowship of the Ring",
                        AUTHORS));
    }

    @Test
    public void correctBookDetailsPublishesCorrectionWhenAuthorsChange(
            @Autowired CommandHandlingTestFixture<CorrectBookDetailsCommand> fixture) {
        var corrected = List.of("J.R.R. Tolkien", "Christopher Tolkien");
        fixture
                .given(event -> event
                        .id("event-1")
                        .payload(new BookPurchasedEvent(ISBN, TITLE, AUTHORS)))
                .when(new CorrectBookDetailsCommand(ISBN, TITLE, corrected, "event-1"))
                .expectSuccessfulExecution()
                .expectSingleEvent(new BookDetailsCorrectedEvent(ISBN, TITLE, corrected));
    }

    @Test
    public void correctBookDetailsIsNoOpWhenNothingChanges(
            @Autowired CommandHandlingTestFixture<CorrectBookDetailsCommand> fixture) {
        fixture
                .given(event -> event
                        .id("event-1")
                        .payload(new BookPurchasedEvent(ISBN, TITLE, AUTHORS)))
                .when(new CorrectBookDetailsCommand(ISBN, TITLE, AUTHORS, "event-1"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }

    @Test
    public void correctBookDetailsRejectsStaleVersion(
            @Autowired CommandHandlingTestFixture<CorrectBookDetailsCommand> fixture) {
        fixture
                .given(event -> event
                        .id("event-2")
                        .payload(new BookPurchasedEvent(ISBN, TITLE, AUTHORS)))
                .when(new CorrectBookDetailsCommand(ISBN, "New Title", AUTHORS, "event-1"))
                .expectException(ConcurrentModificationException.class);
    }

    @Test
    public void correctBookDetailsRejectsBlankTitle(
            @Autowired CommandHandlingTestFixture<CorrectBookDetailsCommand> fixture) {
        fixture
                .given(event -> event
                        .id("event-1")
                        .payload(new BookPurchasedEvent(ISBN, TITLE, AUTHORS)))
                .when(new CorrectBookDetailsCommand(ISBN, "  ", AUTHORS, "event-1"))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    public void correctBookDetailsRejectsEmptyAuthors(
            @Autowired CommandHandlingTestFixture<CorrectBookDetailsCommand> fixture) {
        fixture
                .given(event -> event
                        .id("event-1")
                        .payload(new BookPurchasedEvent(ISBN, TITLE, AUTHORS)))
                .when(new CorrectBookDetailsCommand(ISBN, TITLE, List.of(), "event-1"))
                .expectException(IllegalArgumentException.class);
    }
}
