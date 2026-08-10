package example.org.nightout.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class NightlifeDateService {

    private static final LocalTime NEXT_NIGHT_CUTOFF = LocalTime.NOON;

    private final Clock clock;

    public NightlifeDateService(Clock clock) {
        this.clock = clock;
    }

    public LocalDate currentNightDate() {
        LocalDate calendarDate = LocalDate.now(clock);
        LocalTime localTime = LocalTime.now(clock);
        return localTime.isBefore(NEXT_NIGHT_CUTOFF) ? calendarDate.minusDays(1) : calendarDate;
    }

    public List<LocalDate> currentAndPreviousNightDates(int previousDays) {
        LocalDate currentNightDate = currentNightDate();
        return IntStream.rangeClosed(0, previousDays)
                .mapToObj(currentNightDate::minusDays)
                .toList();
    }
}
