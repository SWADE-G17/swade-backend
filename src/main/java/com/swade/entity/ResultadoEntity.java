package com.swade.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "resultado")
public class ResultadoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudio_id", nullable = false, unique = true)
    private EstudioEntity estudio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prediction", columnDefinition = "jsonb")
    private JsonNode prediction;

    @Column(name = "heatmap_path", length = 255)
    private String heatmapPath;

    @Column(name = "orig_path", length = 255)
    private String origPath;

    @Column(name = "report_path", length = 255)
    private String reportPath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EstudioEntity getEstudio() { return estudio; }
    public void setEstudio(EstudioEntity estudio) { this.estudio = estudio; }

    public JsonNode getPrediction() { return prediction; }
    public void setPrediction(JsonNode prediction) { this.prediction = prediction; }

    public String getHeatmapPath() { return heatmapPath; }
    public void setHeatmapPath(String heatmapPath) { this.heatmapPath = heatmapPath; }

    public String getOrigPath() { return origPath; }
    public void setOrigPath(String origPath) { this.origPath = origPath; }

    public String getReportPath() { return reportPath; }
    public void setReportPath(String reportPath) { this.reportPath = reportPath; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
