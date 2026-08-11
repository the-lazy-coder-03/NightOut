package example.org.nightout.repository;

import example.org.nightout.entity.AppUser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByLogtoSubject(String logtoSubject);

    List<AppUser> findByRoleOrderByFullNameAsc(example.org.nightout.entity.UserRole role);
}
