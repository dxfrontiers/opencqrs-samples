package com.example.cqrs.domain.api.event;

import com.example.cqrs.domain.api.Purpose;

public record EmailAddressDeniedEvent(
        String email,
        String username,
        Purpose purpose
) {}
