package com.example.cqrs.domain;

import com.opencqrs.esdb.client.Event;

import java.util.List;

public record Book(String version, String isbn, String title, List<String> authors, int rating) {
    public Book(Event event, String isbn, String title, List<String> authors, int rating) {
        this(event != null ? event.id() : null, isbn, title, authors, rating);
    }
}
