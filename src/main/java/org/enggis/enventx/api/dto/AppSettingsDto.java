package org.enggis.enventx.api.dto;

import java.util.Map;

/** Fixed business limits plus the stored settings whose key starts with {@code judging.}. */
public record AppSettingsDto(
        int maxActiveIdeasPerUser,
        int maxTeamSize,
        int maxTeamMembersBesidesOwner,
        int maxUploadSizeMb,
        Map<String, String> judgingCriteria
) {

    public AppSettingsDto {
        judgingCriteria = judgingCriteria == null ? Map.of() : Map.copyOf(judgingCriteria);
    }
}
