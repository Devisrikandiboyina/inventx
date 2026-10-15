package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.entity.Guideline;

import java.util.UUID;

public record GuidelineDto(UUID id, String title, String content, int sequenceOrder) {

    public static GuidelineDto from(Guideline g) {
        return new GuidelineDto(g.getId(), g.getTitle(), g.getContent(), g.getSequenceOrder());
    }
}
