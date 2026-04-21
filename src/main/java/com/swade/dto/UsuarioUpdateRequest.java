package com.swade.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioUpdateRequest(
        @Size(min = 1, max = 255, message = "El nombre de usuario debe tener entre 1 y 255 caracteres")
        String username,

        @Pattern(regexp = "Administrador|Clínico|Académico",
                 message = "El rol debe ser Administrador, Clínico o Académico")
        String role
) {}
