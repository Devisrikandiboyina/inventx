package org.enggis.enventx.api.service;

import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.domain.entity.AppSetting;
import org.enggis.enventx.api.domain.enums.AuditAction;
import org.enggis.enventx.api.dto.AppSettingsDto;
import org.enggis.enventx.api.dto.UpdateSettingsRequest;
import org.enggis.enventx.api.repository.AppSettingRepository;
import org.enggis.enventx.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizerSettingsService {

    public static final int REQUIRED_TOTAL_WEIGHT = 100;

    private final AppSettingRepository settings;
    private final UserRepository users;
    private final AuditService audit;
    private final ContentService content;

    /** Replaces the whole set of judging criteria; limits stay fixed in code and cannot be edited here. */
    @Transactional
    public AppSettingsDto update(UUID actorId, UpdateSettingsRequest request) {
        var total = request.judgingCriteria().values().stream().mapToInt(Integer::intValue).sum();
        if (total != REQUIRED_TOTAL_WEIGHT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JUDGING_WEIGHTS_MUST_SUM_TO_100");
        }
        var actor = users.findById(actorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND"));
        var wanted = new TreeMap<String, Integer>();
        request.judgingCriteria().forEach((name, weight) -> wanted.put(name, weight));

        var existing = settings.findByKeyStartingWithOrderByKeyAsc(ContentService.JUDGING_PREFIX);
        var before = new TreeMap<String, String>();
        existing.forEach(s -> before.put(s.getKey().substring(ContentService.JUDGING_PREFIX.length()), s.getValue()));

        for (var setting : existing) {
            var name = setting.getKey().substring(ContentService.JUDGING_PREFIX.length());
            if (!wanted.containsKey(name)) {
                settings.delete(setting);
            }
        }
        for (var entry : wanted.entrySet()) {
            var key = ContentService.JUDGING_PREFIX + entry.getKey();
            var setting = existing.stream().filter(s -> s.getKey().equals(key)).findFirst().orElseGet(() -> {
                var created = new AppSetting();
                created.setKey(key);
                return created;
            });
            setting.setValue(entry.getValue().toString());
            settings.save(setting);
        }
        settings.flush();
        audit.record(actor, AuditAction.SETTINGS_UPDATED, "SETTINGS", "judging", before + " -> " + wanted);
        return content.settings();
    }
}
