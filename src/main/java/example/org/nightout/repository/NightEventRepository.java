package example.org.nightout.repository;

import example.org.nightout.entity.Club;
import example.org.nightout.entity.NightEvent;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NightEventRepository extends JpaRepository<NightEvent, Long> {
    List<NightEvent> findByClubAndCancelledFalseOrderByEventDateDesc(Club club);

    @EntityGraph(attributePaths = "club")
    Optional<NightEvent> findByIdAndClubSlug(Long id, String slug);

    List<NightEvent> findByEventDateBefore(LocalDate cutoffDate);

    @EntityGraph(attributePaths = "club")
    List<NightEvent> findAllByOrderByEventDateDesc();

    @EntityGraph(attributePaths = "club")
    Optional<NightEvent> findWithClubById(Long id);
}
