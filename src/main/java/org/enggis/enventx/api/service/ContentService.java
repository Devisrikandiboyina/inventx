package org.enggis.enventx.api.service;

import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.domain.entity.AppSetting;
import org.enggis.enventx.api.dto.AnnouncementDto;
import org.enggis.enventx.api.dto.AppSettingsDto;
import org.enggis.enventx.api.dto.GuidelineDto;
import org.enggis.enventx.api.dto.StageConfigDto;
import org.enggis.enventx.api.repository.AnnouncementRepository;
import org.enggis.enventx.api.repository.AppSettingRepository;
import org.enggis.enventx.api.repository.GuidelineRepository;
import org.enggis.enventx.api.repository.StageConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

    public static final String JUDGING_PREFIX = "judging.";
    public static final int MAX_UPLOAD_SIZE_MB = 20;
    public static final int MAX_TEAM_SIZE = IdeaService.MAX_TEAM_MEMBERS + 1;

    private final StageConfigRepository stages;
    private final GuidelineRepository guidelines;
    private final AnnouncementRepository announcements;
    private final AppSettingRepository settings;

    public List<StageConfigDto> stages() {
        return stages.findByEnabledTrueOrderBySequenceOrderAsc().stream().map(StageConfigDto::from).toList();
    }

    public List<GuidelineDto> guidelines() {
        return guidelines.findByActiveTrueOrderBySequenceOrderAsc().stream().map(GuidelineDto::from).toList();
    }

    /** Active and already published, pinned first, then newest first. */
    public List<AnnouncementDto> announcements() {
        return announcements.findAllPublishedAndActive(Instant.now()).stream().map(AnnouncementDto::from).toList();
    }

    /** Only {@code judging.*} keys are exposed to employees; other stored settings stay private. */
    public AppSettingsDto settings() {
        var judging = new LinkedHashMap<String, String>();
        for (AppSetting s : settings.findByKeyStartingWithOrderByKeyAsc(JUDGING_PREFIX)) {
            judging.put(s.getKey().substring(JUDGING_PREFIX.length()), s.getValue());
        }
        return new AppSettingsDto(IdeaService.MAX_ACTIVE_IDEAS, MAX_TEAM_SIZE, IdeaService.MAX_TEAM_MEMBERS,
                MAX_UPLOAD_SIZE_MB, judging);
    }
}
