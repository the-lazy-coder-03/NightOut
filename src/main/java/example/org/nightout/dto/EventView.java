package example.org.nightout.dto;

import example.org.nightout.entity.EventLifecycleStatus;
import example.org.nightout.entity.NightEvent;

import java.time.LocalDate;

public record EventView(
        NightEvent event,
        LocalDate expiresOn,
        EventLifecycleStatus status,
        long photoCount,
        boolean uploadAvailable,
        boolean galleryAvailable
) {
}
