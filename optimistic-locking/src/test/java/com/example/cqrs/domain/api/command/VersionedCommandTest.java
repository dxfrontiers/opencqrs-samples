package com.example.cqrs.domain.api.command;

import com.example.cqrs.domain.api.Versioned;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ConcurrentModificationException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionedCommandTest {

    @Mock
    Versioned state;

    @Test
    void verifyAgainstPassesWhenVersionsMatch() {
        VersionedCommand command = new CorrectBookDetailsCommand(
                "isbn", "title", List.of("author"), "v1");
        when(state.version()).thenReturn("v1");

        assertThatCode(() -> command.verifyAgainst(state)).doesNotThrowAnyException();
    }

    @Test
    void verifyAgainstThrowsWhenStateIsAhead() {
        VersionedCommand command = new CorrectBookDetailsCommand(
                "isbn", "title", List.of("author"), "v1");
        when(state.version()).thenReturn("v2");

        assertThatThrownBy(() -> command.verifyAgainst(state))
                .isInstanceOf(ConcurrentModificationException.class)
                .hasMessageContaining("v1")
                .hasMessageContaining("v2");
    }
}
