package com.swade.security;

import com.swade.entity.UsuarioEntity;
import com.swade.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;

    public AuthService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public UUID getCurrentUserId() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return UUID.fromString(jwt.getSubject());
    }

    public Optional<UsuarioEntity> getCurrentUsuario() {
        return usuarioRepository.findById(getCurrentUserId());
    }

    public boolean usuarioExists(UUID id) {
        return usuarioRepository.existsById(id);
    }
}
