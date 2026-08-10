package example.org.nightout.dto;

import java.time.LocalDate;
import java.util.List;

public record NightDateView(
        LocalDate date,
        boolean current,
        List<EventView> events,
        long photoCount
) {
}
