package com.quantbackengine.backend.repository;

import com.quantbackengine.backend.domain.MlLabRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MlLabRunRepository extends JpaRepository<MlLabRun, String> {

    boolean existsByStatusIn(Collection<MlLabRun.Status> statuses);

    List<MlLabRun> findAllByOrderByCreatedAtDesc();
}
