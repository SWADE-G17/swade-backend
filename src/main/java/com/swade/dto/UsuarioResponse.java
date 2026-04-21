package com.swade.dto;

import java.time.Instant;
import java.util.UUID;

public record UsuarioResponse(
        UUID id,
        String username,
        String role,
        String email,
        Instant createdAt,
        Instant updatedAt
) {}
