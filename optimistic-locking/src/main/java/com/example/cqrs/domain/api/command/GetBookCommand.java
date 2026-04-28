package com.example.cqrs.domain.api.command;

import com.opencqrs.framework.command.Command;

public record GetBookCommand(String isbn) implements Command {
    @Override
    public String getSubject() {
        return "/books/" + isbn;
    }

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.EXISTS;
    }
}
