package example.org.nightout.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class NightlifeDateServiceTest {

    @Test
    void beforeNoonUsesPreviousCalendarDate() {
        NightlifeDateService service = serviceAt("2026-08-08T03:00:00Z");

        assertThat(service.currentNightDate()).isEqualTo(LocalDate.of(2026, 8, 7));
    }

    @Test
    void atNoonUsesCurrentCalendarDate() {
        NightlifeDateService service = serviceAt("2026-08-08T12:00:00Z");

        assertThat(service.currentNightDate()).isEqualTo(LocalDate.of(2026, 8, 8));
    }

    @Test
    void currentAndPreviousNightDatesIncludesCurrentNightAndSevenPreviousDates() {
        NightlifeDateService service = serviceAt("2026-08-08T12:00:00Z");

        assertThat(service.currentAndPreviousNightDates(7))
                .containsExactly(
                        LocalDate.of(2026, 8, 8),
                        LocalDate.of(2026, 8, 7),
                        LocalDate.of(2026, 8, 6),
                        LocalDate.of(2026, 8, 5),
                        LocalDate.of(2026, 8, 4),
                        LocalDate.of(2026, 8, 3),
                        LocalDate.of(2026, 8, 2),
                        LocalDate.of(2026, 8, 1)
                );
    }

    private NightlifeDateService serviceAt(String instant) {
        return new NightlifeDateService(Clock.fixed(Instant.parse(instant), ZoneId.of("UTC")));
    }
}
