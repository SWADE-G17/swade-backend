package com.swade.service;

import com.swade.dto.UsuarioUpdateRequest;
import com.swade.entity.UsuarioEntity;
import com.swade.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public List<UsuarioEntity> listAll() {
        return usuarioRepository.findAll();
    }

    public Optional<UsuarioEntity> findById(UUID id) {
        return usuarioRepository.findById(id);
    }

    @Transactional
    public Optional<UsuarioEntity> update(UUID id, UsuarioUpdateRequest request) {
        return usuarioRepository.findById(id).map(usuario -> {
            if (request.username() != null) {
                usuario.setUsername(request.username());
            }
            if (request.role() != null) {
                usuario.setRole(request.role());
            }
            return usuarioRepository.save(usuario);
        });
    }

    @Transactional
    public boolean delete(UUID id) {
        if (!usuarioRepository.existsById(id)) {
            return false;
        }
        usuarioRepository.deleteById(id);
        return true;
    }
}
