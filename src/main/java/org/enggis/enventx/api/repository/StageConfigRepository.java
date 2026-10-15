package org.enggis.enventx.api.repository;

import org.enggis.enventx.api.domain.entity.StageConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import org.enggis.enventx.api.domain.enums.Stage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StageConfigRepository extends JpaRepository<StageConfig, UUID> {

    List<StageConfig> findByEnabledTrueOrderBySequenceOrderAsc();

    Optional<StageConfig> findByStage(Stage stage);



    List<StageConfig> findAllByEnabledTrueOrderBySequenceOrderAsc();
}
