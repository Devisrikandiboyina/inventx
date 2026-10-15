package org.enggis.enventx.api.repository;

import org.enggis.enventx.api.domain.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    @Query("SELECT a FROM Announcement a     WHERE a.state = org.enggis.enventx.api.domain.enums.AnnouncementState.PUBLISHED "
                + "AND a.publishAt IS NOT NULL AND a.publishAt <= :now ORDER BY a.pinned DESC, a.publishAt DESC")
    List<Announcement> findAllPublishedAndActive(@Param("now") Instant now);

    default List<Announcement> findAllPublishedAndActive(LocalDateTime now) {
        return findAllPublishedAndActive(now.atZone(ZoneId.systemDefault()).toInstant());
    }

    List<Announcement> findAllByOrderByCreatedAtDesc();
}
