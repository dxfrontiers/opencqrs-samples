package com.example.cqrs.domain.api.command;

import com.opencqrs.framework.command.Command;

public interface ValidatedCommand extends Command {

    void validate(Object o);
}
