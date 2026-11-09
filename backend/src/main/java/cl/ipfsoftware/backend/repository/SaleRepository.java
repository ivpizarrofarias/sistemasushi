package cl.ipfsoftware.backend.repository;

import cl.ipfsoftware.backend.domain.Sale;
import cl.ipfsoftware.backend.domain.SaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(Instant from, Instant to);

    List<Sale> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatusOrderByCreatedAtDesc(
            Instant from, Instant to, SaleStatus status);

    List<Sale> findTop10ByOrderByCreatedAtDesc();

    List<Sale> findByCashSessionIdOrderByCreatedAtDesc(Long cashSessionId);
}
