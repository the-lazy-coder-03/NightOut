package example.org.nightout.repository;

import example.org.nightout.entity.Club;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClubRepository extends JpaRepository<Club, Long> {
    List<Club> findByActiveTrueOrderByNameAsc();

    Optional<Club> findBySlugAndActiveTrue(String slug);

    Optional<Club> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
