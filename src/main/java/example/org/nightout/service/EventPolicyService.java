package example.org.nightout.service;

import example.org.nightout.config.AppProperties;
import example.org.nightout.entity.EventLifecycleStatus;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.exception.BusinessRuleException;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class EventPolicyService {

    private final AppProperties properties;
    private final Clock clock;

    public EventPolicyService(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public EventLifecycleStatus statusFor(NightEvent event) {
        if (event.isCancelled()) {
            return EventLifecycleStatus.CANCELLED;
        }
        LocalDate today = today();
        if (event.getEventDate().isAfter(today)) {
            return EventLifecycleStatus.UPCOMING;
        }
        if (today.isAfter(expiresOn(event))) {
            return EventLifecycleStatus.EXPIRED;
        }
        if (event.getEventDate().isEqual(today)) {
            return EventLifecycleStatus.ACTIVE;
        }
        return EventLifecycleStatus.RECENT;
    }

    public LocalDate expiresOn(NightEvent event) {
        return event.getEventDate().plusDays(properties.getRetentionDays());
    }

    public boolean uploadAvailable(NightEvent event) {
        EventLifecycleStatus status = statusFor(event);
        return status == EventLifecycleStatus.ACTIVE || status == EventLifecycleStatus.RECENT;
    }

    public boolean galleryAvailable(NightEvent event) {
        return uploadAvailable(event);
    }

    public LocalDate expiredCutoffDate() {
        return today().minusDays(properties.getRetentionDays());
    }

    public void requireUploadAvailable(NightEvent event) {
        EventLifecycleStatus status = statusFor(event);
        if (status == EventLifecycleStatus.UPCOMING) {
            throw new BusinessRuleException("This night has not happened yet.");
        }
        if (status == EventLifecycleStatus.EXPIRED) {
            throw new BusinessRuleException("This gallery has expired.");
        }
        if (status == EventLifecycleStatus.CANCELLED) {
            throw new BusinessRuleException("This night has been cancelled.");
        }
    }

    public void requireGalleryAvailable(NightEvent event) {
        EventLifecycleStatus status = statusFor(event);
        if (status == EventLifecycleStatus.UPCOMING) {
            throw new BusinessRuleException("This night is coming soon.");
        }
        if (status == EventLifecycleStatus.EXPIRED) {
            throw new BusinessRuleException("This gallery has expired.");
        }
        if (status == EventLifecycleStatus.CANCELLED) {
            throw new BusinessRuleException("This night has been cancelled.");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
