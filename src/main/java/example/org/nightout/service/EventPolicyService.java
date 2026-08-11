package example.org.nightout.service;

import example.org.nightout.config.AppProperties;
import example.org.nightout.entity.EventLifecycleStatus;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.exception.BusinessRuleException;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class EventPolicyService {

    private final AppProperties properties;
    private final NightlifeDateService nightlifeDateService;

    public EventPolicyService(AppProperties properties, NightlifeDateService nightlifeDateService) {
        this.properties = properties;
        this.nightlifeDateService = nightlifeDateService;
    }

    public EventLifecycleStatus statusFor(NightEvent event) {
        return statusFor(event.getEventDate(), event.isCancelled());
    }

    public boolean uploadAvailable(LocalDate eventDate) {
        EventLifecycleStatus status = statusFor(eventDate, false);
        return status == EventLifecycleStatus.ACTIVE || status == EventLifecycleStatus.RECENT;
    }

    public void requireUploadAvailable(LocalDate eventDate) {
        requireUploadAvailable(statusFor(eventDate, false));
    }

    private EventLifecycleStatus statusFor(LocalDate eventDate, boolean cancelled) {
        if (cancelled) {
            return EventLifecycleStatus.CANCELLED;
        }
        LocalDate today = today();
        if (eventDate.isAfter(today)) {
            return EventLifecycleStatus.UPCOMING;
        }
        if (today.isAfter(eventDate.plusDays(properties.getRetentionDays()))) {
            return EventLifecycleStatus.EXPIRED;
        }
        if (eventDate.isEqual(today)) {
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

    public LocalDate currentGalleryDate() {
        return today();
    }

    public void requireUploadAvailable(NightEvent event) {
        requireUploadAvailable(statusFor(event));
    }

    private static void requireUploadAvailable(EventLifecycleStatus status) {
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
        return nightlifeDateService.currentNightDate();
    }
}
