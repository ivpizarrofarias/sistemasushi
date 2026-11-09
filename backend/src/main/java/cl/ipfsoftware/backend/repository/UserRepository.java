package cl.ipfsoftware.backend.repository;

import cl.ipfsoftware.backend.domain.Role;
import cl.ipfsoftware.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<User> findAllByOrderByFullNameAsc();

    long countByRoleAndActiveTrue(Role role);
}
