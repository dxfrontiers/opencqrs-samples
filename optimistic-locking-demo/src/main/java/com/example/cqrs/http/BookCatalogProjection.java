package com.example.cqrs.http;

import com.opencqrs.esdb.client.Event;
import com.example.cqrs.domain.api.event.BookDetailsCorrectedEvent;
import com.example.cqrs.domain.api.event.BookPurchasedEvent;
import com.opencqrs.framework.eventhandler.EventHandling;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class BookCatalogProjection {

    private final ConcurrentMap<String, BookView> catalog = new ConcurrentHashMap<>();

    @EventHandling("book-catalog")
    public void on(BookPurchasedEvent event, Event rawEvent) {
        catalog.put(event.isbn(), new BookView(event.isbn(), event.title(), List.copyOf(event.authors()), rawEvent.id()));
    }

    @EventHandling("book-catalog")
    public void on(BookDetailsCorrectedEvent event, Event rawEvent) {
        catalog.computeIfPresent(event.isbn(), (isbn, current) ->
                new BookView(isbn, event.title(), List.copyOf(event.authors()), rawEvent.id()));
    }

    public Optional<BookView> findByIsbn(String isbn) {
        return Optional.ofNullable(catalog.get(isbn));
    }
}
