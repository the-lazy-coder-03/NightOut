package example.org.nightout.repository;

import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.entity.PhotoOptimizationStatus;
import example.org.nightout.entity.PhotoStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    long countByEventAndStatus(NightEvent event, PhotoStatus status);

    List<Photo> findByEventAndStatusOrderByUploadedAtDesc(NightEvent event, PhotoStatus status);

    @EntityGraph(attributePaths = {"event", "event.club"})
    List<Photo> findByEventInAndStatusOrderByUploadedAtDesc(List<NightEvent> events, PhotoStatus status);

    @Query("""
            select p
            from Photo p
            join fetch p.event e
            join fetch e.club c
            where p.status = :status
              and e.cancelled = false
              and c.active = true
              and c.area = :area
              and e.eventDate between :startDate and :endDate
            order by e.eventDate desc, p.uploadedAt desc
            """)
    List<Photo> findAreaApprovedPhotosForPreload(
            @Param("status") PhotoStatus status,
            @Param("area") String area,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<Photo> findByEvent_EventDateBefore(LocalDate cutoffDate);

    @EntityGraph(attributePaths = {"event", "event.club"})
    List<Photo> findAllByOrderByUploadedAtDesc();

    @EntityGraph(attributePaths = {"event", "event.club"})
    List<Photo> findByEvent_Club_IdOrderByUploadedAtDesc(Long clubId);

    @EntityGraph(attributePaths = {"event", "event.club"})
    Optional<Photo> findWithEventById(Long id);

    List<Photo> findByOptimizationStatusOrderByUploadedAtAsc(PhotoOptimizationStatus status, Pageable pageable);

    @Modifying
    @Query("""
            update Photo photo
            set photo.optimizationStatus = :processing,
                photo.optimizationAttempts = photo.optimizationAttempts + 1,
                photo.optimizationStartedAt = :startedAt,
                photo.optimizationError = null
            where photo.id = :photoId
              and photo.optimizationStatus = :pending
            """)
    int markOptimizationStarted(
            @Param("photoId") Long photoId,
            @Param("pending") PhotoOptimizationStatus pending,
            @Param("processing") PhotoOptimizationStatus processing,
            @Param("startedAt") Instant startedAt
    );
}
