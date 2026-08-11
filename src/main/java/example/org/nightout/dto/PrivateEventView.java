package example.org.nightout.dto;

import example.org.nightout.entity.PrivateEvent;

import java.time.LocalDate;

public record PrivateEventView(
        PrivateEvent event,
        LocalDate expiresOn,
        boolean expired,
        boolean member,
        boolean creator
) {
}
