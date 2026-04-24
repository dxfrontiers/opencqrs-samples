package com.example.cqrs.domain.api.event;

public record EmailAddressDeniedEvent(
        String email,
        String username
) {}
