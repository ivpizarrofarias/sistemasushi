package cl.ipfsoftware.backend.service;

import cl.ipfsoftware.backend.domain.CashSession;
import cl.ipfsoftware.backend.domain.CashSessionStatus;
import cl.ipfsoftware.backend.domain.PaymentMethod;
import cl.ipfsoftware.backend.domain.Sale;
import cl.ipfsoftware.backend.domain.SaleStatus;
import cl.ipfsoftware.backend.domain.User;
import cl.ipfsoftware.backend.dto.CashSessionDto;
import cl.ipfsoftware.backend.dto.CloseCashRequest;
import cl.ipfsoftware.backend.dto.OpenCashRequest;
import cl.ipfsoftware.backend.repository.CashSessionRepository;
import cl.ipfsoftware.backend.repository.SaleRepository;
import cl.ipfsoftware.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CashService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final CashSessionRepository cashSessionRepository;
    private final SaleRepository saleRepository;
    private final UserRepository userRepository;

    public CashService(
            CashSessionRepository cashSessionRepository,
            SaleRepository saleRepository,
            UserRepository userRepository
    ) {
        this.cashSessionRepository = cashSessionRepository;
        this.saleRepository = saleRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CashSessionDto current() {
        return cashSessionRepository.findFirstByStatusOrderByOpenedAtDesc(CashSessionStatus.OPEN)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public CashSessionDto open(OpenCashRequest request, String username) {
        if (cashSessionRepository.findFirstByStatusOrderByOpenedAtDesc(CashSessionStatus.OPEN).isPresent()) {
            throw new IllegalStateException("Ya existe una caja abierta");
        }
        User user = requireUser(username);
        CashSession session = new CashSession();
        session.setOpenedBy(user);
        session.setOpeningAmount(request.openingAmount().setScale(2, RoundingMode.HALF_UP));
        session.setStatus(CashSessionStatus.OPEN);
        return toDto(cashSessionRepository.save(session));
    }

    @Transactional
    public CashSessionDto close(CloseCashRequest request, String username) {
        CashSession session = cashSessionRepository
                .findFirstByStatusOrderByOpenedAtDesc(CashSessionStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("No hay una caja abierta"));
        User user = requireUser(username);

        List<Sale> sales = saleRepository.findByCashSessionIdOrderByCreatedAtDesc(session.getId());
        BigDecimal cashSales = sum(sales, PaymentMethod.CASH);
        BigDecimal expected = session.getOpeningAmount().add(cashSales).setScale(2, RoundingMode.HALF_UP);

        session.setClosingAmount(request.closingAmount().setScale(2, RoundingMode.HALF_UP));
        session.setExpectedAmount(expected);
        session.setDifference(session.getClosingAmount().subtract(expected).setScale(2, RoundingMode.HALF_UP));
        session.setClosedAt(Instant.now());
        session.setClosedBy(user);
        session.setStatus(CashSessionStatus.CLOSED);
        session.setNotes(request.notes());

        return toDto(session);
    }

    @Transactional(readOnly = true)
    public List<CashSessionDto> history() {
        return cashSessionRepository.findTop20ByOrderByOpenedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    private CashSessionDto toDto(CashSession session) {
        List<Sale> sales = saleRepository.findByCashSessionIdOrderByCreatedAtDesc(session.getId());
        List<Sale> completed = sales.stream()
                .filter(sale -> sale.getStatus() == SaleStatus.COMPLETED)
                .toList();

        BigDecimal cashSales = sum(completed, PaymentMethod.CASH);
        BigDecimal totalSales = completed.stream()
                .map(Sale::getTotal)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal expected = session.getExpectedAmount() != null
                ? session.getExpectedAmount()
                : session.getOpeningAmount().add(cashSales).setScale(2, RoundingMode.HALF_UP);

        return new CashSessionDto(
                session.getId(),
                session.getStatus().name(),
                session.getOpenedAt(),
                session.getClosedAt(),
                session.getOpenedBy().getFullName(),
                session.getClosedBy() != null ? session.getClosedBy().getFullName() : null,
                session.getOpeningAmount(),
                session.getClosingAmount(),
                expected,
                session.getDifference(),
                cashSales,
                totalSales,
                completed.size(),
                session.getNotes()
        );
    }

    private BigDecimal sum(List<Sale> sales, PaymentMethod method) {
        return sales.stream()
                .filter(sale -> sale.getStatus() == SaleStatus.COMPLETED)
                .filter(sale -> sale.getPaymentMethod() == method)
                .map(Sale::getTotal)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
    }
}
