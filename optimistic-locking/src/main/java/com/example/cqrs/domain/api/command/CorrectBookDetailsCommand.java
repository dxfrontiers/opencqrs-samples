package com.example.cqrs.domain.api.command;

import java.util.List;

public record CorrectBookDetailsCommand(String isbn, String title, List<String> authors, String expectedVersion)
        implements VersionedCommand, ValidatedCommand {

    @Override
    public String getSubject() {
        return "/books/" + isbn;
    }

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.EXISTS;
    }

    @Override
    public void validate() {
        if (isbn == null || isbn.isBlank())
            throw new IllegalArgumentException("isbn must not be blank");
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("title must not be blank");
        if (authors == null || authors.isEmpty())
            throw new IllegalArgumentException("authors must not be empty");
        if (expectedVersion == null || expectedVersion.isBlank())
            throw new IllegalArgumentException("expectedVersion must not be blank");
    }
}
