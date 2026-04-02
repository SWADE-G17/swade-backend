package com.swade.repository;

import com.swade.entity.EstudioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EstudioRepository extends JpaRepository<EstudioEntity, Long> {

    List<EstudioEntity> findByUsuarioIdOrderByCreatedAtDesc(UUID usuarioId);

    Optional<EstudioEntity> findByIdAndUsuarioId(Long id, UUID usuarioId);
}
