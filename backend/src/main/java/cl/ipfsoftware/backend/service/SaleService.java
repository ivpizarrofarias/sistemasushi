package cl.ipfsoftware.backend.service;

import cl.ipfsoftware.backend.domain.CashSession;
import cl.ipfsoftware.backend.domain.CashSessionStatus;
import cl.ipfsoftware.backend.domain.PaymentMethod;
import cl.ipfsoftware.backend.domain.Product;
import cl.ipfsoftware.backend.domain.Sale;
import cl.ipfsoftware.backend.domain.SaleItem;
import cl.ipfsoftware.backend.domain.SaleStatus;
import cl.ipfsoftware.backend.domain.StockMovement;
import cl.ipfsoftware.backend.domain.StockMovementType;
import cl.ipfsoftware.backend.domain.User;
import cl.ipfsoftware.backend.dto.SaleDto;
import cl.ipfsoftware.backend.dto.SaleItemRequest;
import cl.ipfsoftware.backend.dto.SaleRequest;
import cl.ipfsoftware.backend.repository.CashSessionRepository;
import cl.ipfsoftware.backend.repository.ProductRepository;
import cl.ipfsoftware.backend.repository.SaleRepository;
import cl.ipfsoftware.backend.repository.StockMovementRepository;
import cl.ipfsoftware.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class SaleService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Santiago");

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CashSessionRepository cashSessionRepository;
    private final StockMovementRepository stockMovementRepository;

    public SaleService(
            SaleRepository saleRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            CashSessionRepository cashSessionRepository,
            StockMovementRepository stockMovementRepository
    ) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.cashSessionRepository = cashSessionRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional
    public SaleDto create(SaleRequest request, String username) {
        User cashier = requireUser(username);
        CashSession cashSession = cashSessionRepository
                .findFirstByStatusOrderByOpenedAtDesc(CashSessionStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Debes abrir la caja antes de registrar una venta"));

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (SaleItemRequest item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        Sale sale = new Sale();
        sale.setCashier(cashier);
        sale.setCashSession(cashSession);
        sale.setPaymentMethod(request.paymentMethod());

        // Bloquea los productos en orden de id para evitar sobreventa y deadlocks.
        List<Map.Entry<Long, Integer>> orderedItems = quantities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : orderedItems) {
            Product product = productRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new NoSuchElementException("Producto no encontrado"));
            int quantity = entry.getValue();

            if (!product.isActive()) {
                throw new IllegalArgumentException("El producto " + product.getName() + " no está disponible");
            }
            if (product.getStock() < quantity) {
                throw new IllegalStateException("Stock insuficiente para " + product.getName()
                        + " (disponible: " + product.getStock() + ")");
            }

            BigDecimal lineTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);

            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(quantity);
            item.setLineTotal(lineTotal);
            sale.addItem(item);

            subtotal = subtotal.add(lineTotal);
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = request.discount() == null
                ? BigDecimal.ZERO.setScale(2)
                : request.discount().setScale(2, RoundingMode.HALF_UP);

        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("El descuento no puede superar el subtotal");
        }

        BigDecimal total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal received;
        if (request.paymentMethod() == PaymentMethod.CASH) {
            received = request.amountReceived();
            if (received == null || received.compareTo(total) < 0) {
                throw new IllegalArgumentException("El monto recibido es insuficiente");
            }
            received = received.setScale(2, RoundingMode.HALF_UP);
        } else {
            received = total;
        }

        sale.setSubtotal(subtotal);
        sale.setDiscount(discount);
        sale.setTotal(total);
        sale.setAmountReceived(received);
        sale.setChangeAmount(received.subtract(total).setScale(2, RoundingMode.HALF_UP));
        sale.setStatus(SaleStatus.COMPLETED);

        Sale saved = saleRepository.saveAndFlush(sale);
        saved.setNumber(String.format("V-%06d", saved.getId()));
        saleRepository.saveAndFlush(saved);

        for (SaleItem item : saved.getItems()) {
            Product product = item.getProduct();
            int previous = product.getStock();
            int updated = previous - item.getQuantity();
            product.setStock(updated);

            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setType(StockMovementType.SALE);
            movement.setQuantity(item.getQuantity());
            movement.setPreviousStock(previous);
            movement.setNewStock(updated);
            movement.setReason("Venta " + saved.getNumber());
            movement.setUser(cashier);
            stockMovementRepository.save(movement);
        }

        return SaleDto.from(saved);
    }

    @Transactional
    public SaleDto voidSale(Long id, String username) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Venta no encontrada"));
        if (sale.getStatus() == SaleStatus.VOIDED) {
            throw new IllegalStateException("La venta ya está anulada");
        }

        User user = requireUser(username);
        sale.setStatus(SaleStatus.VOIDED);

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (SaleItem item : sale.getItems()) {
            if (item.getProduct() != null) {
                quantities.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
            }
        }

        // Bloquea los productos en orden de id para evitar deadlocks.
        List<Map.Entry<Long, Integer>> orderedItems = quantities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        for (Map.Entry<Long, Integer> entry : orderedItems) {
            Product product = productRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new NoSuchElementException("Producto no encontrado"));
            int quantity = entry.getValue();
            int previous = product.getStock();
            int updated = previous + quantity;
            product.setStock(updated);

            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setType(StockMovementType.SALE_VOID);
            movement.setQuantity(quantity);
            movement.setPreviousStock(previous);
            movement.setNewStock(updated);
            movement.setReason("Anulación de venta " + sale.getNumber());
            movement.setUser(user);
            stockMovementRepository.save(movement);
        }

        return SaleDto.from(sale);
    }

    @Transactional(readOnly = true)
    public List<SaleDto> byDate(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now(BUSINESS_ZONE) : date;
        Instant start = target.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant end = target.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        return saleRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(start, end)
                .stream()
                .map(SaleDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SaleDto get(Long id) {
        return SaleDto.from(saleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Venta no encontrada")));
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
    }
}
