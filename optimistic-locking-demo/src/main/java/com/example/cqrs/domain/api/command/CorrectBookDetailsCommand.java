package com.example.cqrs.domain.api.command;

import com.opencqrs.framework.command.Command;

import java.util.ConcurrentModificationException;
import java.util.List;

public record CorrectBookDetailsCommand(String isbn, String title, List<String> authors, String expectedVersion) implements OptimisticCommand {
    @Override
    public String getSubject() {
        return "/books/" + isbn;
    }

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.EXISTS;
    }

}
