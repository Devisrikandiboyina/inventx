package org.enggis.enventx.api.service;

import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.dto.NotificationDto;
import org.enggis.enventx.api.dto.ReadAllResultDto;
import org.enggis.enventx.api.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;

    @Transactional(readOnly = true)
    public List<NotificationDto> latest(UUID userId) {
        return notifications.findTop50ByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationDto::from).toList();
    }

    @Transactional
    public ReadAllResultDto markAllRead(UUID userId) {
        return new ReadAllResultDto(notifications.markAllRead(userId));
    }
}
