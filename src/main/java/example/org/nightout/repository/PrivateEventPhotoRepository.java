package example.org.nightout.repository;

import example.org.nightout.entity.PrivateEvent;
import example.org.nightout.entity.PrivateEventPhoto;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrivateEventPhotoRepository extends JpaRepository<PrivateEventPhoto, Long> {

    @EntityGraph(attributePaths = {"privateEvent", "privateEvent.creator", "uploadedBy"})
    List<PrivateEventPhoto> findByPrivateEventOrderByUploadedAtDesc(PrivateEvent privateEvent);

    @EntityGraph(attributePaths = {"privateEvent", "privateEvent.creator", "uploadedBy"})
    Optional<PrivateEventPhoto> findWithPrivateEventById(Long id);
}
