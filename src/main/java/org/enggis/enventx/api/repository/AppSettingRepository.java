package org.enggis.enventx.api.repository;

import org.enggis.enventx.api.domain.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppSettingRepository extends JpaRepository<AppSetting, UUID> {

    List<AppSetting> findByKeyStartingWithOrderByKeyAsc(String prefix);
}
