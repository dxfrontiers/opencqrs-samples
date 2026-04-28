package com.example.cqrs.domain.api.command;

import com.example.cqrs.domain.api.Versioned;
import com.opencqrs.framework.command.Command;

import java.util.ConcurrentModificationException;

public interface VersionedCommand extends Command {
    String expectedVersion();

    default void verifyAgainst(Versioned state) {
        if (!expectedVersion().equals(state.version()))
            throw new ConcurrentModificationException(
                    "Expected version " + expectedVersion() + " but found " + state.version());
    }
}
