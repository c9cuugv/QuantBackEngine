package com.quantbackengine.backend.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One ML Lab experiment: a qlib train→predict→backtest pipeline run
 * executed asynchronously by a detached Python process.
 */
@Entity
@Table(name = "mllab_run", indexes = @Index(name = "idx_mllab_status", columnList = "status"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlLabRun {

    public enum Status { QUEUED, RUNNING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    /** Request params as JSON (symbols, date range, model config). */
    @Lob
    @Column(nullable = false)
    private String paramsJson;

    /** Result payload as JSON once DONE (metrics + equity curve). */
    @Lob
    private String resultJson;

    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
