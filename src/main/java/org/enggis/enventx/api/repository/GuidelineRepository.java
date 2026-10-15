package org.enggis.enventx.api.repository;

import org.enggis.enventx.api.domain.entity.Guideline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GuidelineRepository extends JpaRepository<Guideline, UUID> {

    List<Guideline> findByActiveTrueOrderBySequenceOrderAsc();

    List<Guideline> findAllByOrderBySequenceOrderAsc();

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(MAX(g.sequenceOrder), 0) FROM Guideline g")
    int maxSequenceOrder();
}
