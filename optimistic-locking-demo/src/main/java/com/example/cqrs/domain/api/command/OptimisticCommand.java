package com.example.cqrs.domain.api.command;

import com.opencqrs.framework.command.Command;

import java.util.ConcurrentModificationException;

public interface OptimisticCommand extends Command {

    String expectedVersion();

    default void validateVersion(String version) {
        if (!version.equals(expectedVersion())) {
            throw new ConcurrentModificationException(
                    "Expected version " + expectedVersion() + " but found " + version);
        }
    }
}
