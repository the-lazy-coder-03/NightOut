package example.org.nightout.repository;

import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.PrivateEvent;
import example.org.nightout.entity.PrivateEventMembership;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrivateEventMembershipRepository extends JpaRepository<PrivateEventMembership, Long> {
    boolean existsByPrivateEventAndUser(PrivateEvent privateEvent, AppUser user);

    Optional<PrivateEventMembership> findByPrivateEventAndUser(PrivateEvent privateEvent, AppUser user);

    @EntityGraph(attributePaths = {"privateEvent", "privateEvent.creator"})
    List<PrivateEventMembership> findByUserOrderByJoinedAtDesc(AppUser user);
}
