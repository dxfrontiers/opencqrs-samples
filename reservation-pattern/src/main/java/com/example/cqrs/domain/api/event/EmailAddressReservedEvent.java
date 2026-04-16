package com.example.cqrs.domain.api.event;

import com.example.cqrs.domain.api.Purpose;

public record EmailAddressReservedEvent(
        String email,
        String username,
        Purpose purpose
) {}
