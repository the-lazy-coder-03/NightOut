package example.org.nightout.repository;

import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.entity.PhotoStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    long countByEventAndStatus(NightEvent event, PhotoStatus status);

    List<Photo> findByEventAndStatusOrderByUploadedAtDesc(NightEvent event, PhotoStatus status);

    @EntityGraph(attributePaths = {"event", "event.club"})
    List<Photo> findByEventInAndStatusOrderByUploadedAtDesc(List<NightEvent> events, PhotoStatus status);

    List<Photo> findByEvent_EventDateBefore(LocalDate cutoffDate);

    @EntityGraph(attributePaths = {"event", "event.club"})
    List<Photo> findAllByOrderByUploadedAtDesc();

    @EntityGraph(attributePaths = {"event", "event.club"})
    List<Photo> findByEvent_Club_IdOrderByUploadedAtDesc(Long clubId);

    @EntityGraph(attributePaths = {"event", "event.club"})
    Optional<Photo> findWithEventById(Long id);
}
