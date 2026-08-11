package example.org.nightout.repository;

import example.org.nightout.entity.PrivateEvent;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrivateEventRepository extends JpaRepository<PrivateEvent, Long> {
    boolean existsByJoinCode(String joinCode);

    @EntityGraph(attributePaths = "creator")
    Optional<PrivateEvent> findByJoinCode(String joinCode);
}
