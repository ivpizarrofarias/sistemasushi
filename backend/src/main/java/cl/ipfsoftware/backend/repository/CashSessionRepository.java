package cl.ipfsoftware.backend.repository;

import cl.ipfsoftware.backend.domain.CashSession;
import cl.ipfsoftware.backend.domain.CashSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashSessionRepository extends JpaRepository<CashSession, Long> {

    Optional<CashSession> findFirstByStatusOrderByOpenedAtDesc(CashSessionStatus status);

    List<CashSession> findTop20ByOrderByOpenedAtDesc();
}
