package example.org.nightout.repository;

import example.org.nightout.entity.PrivateEvent;
import example.org.nightout.entity.AppUser;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PrivateEventRepository extends JpaRepository<PrivateEvent, Long> {
    boolean existsByJoinCode(String joinCode);

    boolean existsByInviteToken(String inviteToken);

    @EntityGraph(attributePaths = "creator")
    Optional<PrivateEvent> findByJoinCode(String joinCode);

    @EntityGraph(attributePaths = "creator")
    Optional<PrivateEvent> findByInviteToken(String inviteToken);

    @EntityGraph(attributePaths = "creator")
    @Query("""
            select distinct event
            from PrivateEvent event
            left join event.memberships membership
            where event.creator = :user
               or membership.user = :user
            order by event.createdAt desc
            """)
    List<PrivateEvent> findVisibleToUser(@Param("user") AppUser user);
}
