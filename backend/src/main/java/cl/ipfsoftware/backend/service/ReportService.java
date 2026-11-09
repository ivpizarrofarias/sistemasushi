package cl.ipfsoftware.backend.service;

import cl.ipfsoftware.backend.domain.PaymentMethod;
import cl.ipfsoftware.backend.domain.Sale;
import cl.ipfsoftware.backend.domain.SaleItem;
import cl.ipfsoftware.backend.domain.SaleStatus;
import cl.ipfsoftware.backend.dto.PaymentBreakdownDto;
import cl.ipfsoftware.backend.dto.SalesReportDto;
import cl.ipfsoftware.backend.dto.TopProductDto;
import cl.ipfsoftware.backend.repository.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final SaleRepository saleRepository;

    public ReportService(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    @Transactional(readOnly = true)
    public SalesReportDto report(LocalDate from, LocalDate to) {
        LocalDate startDate = from == null ? LocalDate.now(SaleService.BUSINESS_ZONE) : from;
        LocalDate endDate = to == null ? startDate : to;
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La fecha hasta no puede ser anterior a la fecha desde");
        }

        Instant start = startDate.atStartOfDay(SaleService.BUSINESS_ZONE).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(SaleService.BUSINESS_ZONE).toInstant();
        List<Sale> sales = saleRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatusOrderByCreatedAtDesc(
                        start, end, SaleStatus.COMPLETED);

        BigDecimal total = BigDecimal.ZERO;
        Map<PaymentMethod, BigDecimal> paymentTotals = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, Long> paymentCounts = new EnumMap<>(PaymentMethod.class);
        Map<String, long[]> topQuantity = new HashMap<>();
        Map<String, BigDecimal> topRevenue = new HashMap<>();
        Map<String, Long> topIds = new HashMap<>();
        Map<String, String> topNames = new HashMap<>();
        long totalUnits = 0;

        for (Sale sale : sales) {
            total = total.add(sale.getTotal());
            paymentTotals.merge(sale.getPaymentMethod(), sale.getTotal(), BigDecimal::add);
            paymentCounts.merge(sale.getPaymentMethod(), 1L, Long::sum);

            // Las ventas vienen ordenadas de más reciente a más antigua, por lo que el
            // primer nombre que aparece para un producto es el más actualizado.
            for (SaleItem item : sale.getItems()) {
                totalUnits += item.getQuantity();
                Long productId = item.getProduct() == null ? null : item.getProduct().getId();
                String key = productId == null ? "name:" + item.getProductName() : "id:" + productId;
                topQuantity.computeIfAbsent(key, k -> new long[1])[0] += item.getQuantity();
                topRevenue.merge(key, item.getLineTotal(), BigDecimal::add);
                topNames.putIfAbsent(key, item.getProductName());
                if (productId != null) {
                    topIds.put(key, productId);
                }
            }
        }

        total = total.setScale(2, RoundingMode.HALF_UP);
        long count = sales.size();

        List<PaymentBreakdownDto> payments = new ArrayList<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            payments.add(new PaymentBreakdownDto(
                    method.name(),
                    paymentTotals.getOrDefault(method, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                    paymentCounts.getOrDefault(method, 0L)
            ));
        }

        List<TopProductDto> topProducts = topQuantity.entrySet().stream()
                .map(entry -> new TopProductDto(
                        topIds.get(entry.getKey()),
                        topNames.get(entry.getKey()),
                        entry.getValue()[0],
                        topRevenue.getOrDefault(entry.getKey(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                ))
                .sorted(Comparator.comparingLong(TopProductDto::quantity).reversed())
                .limit(10)
                .toList();

        BigDecimal cash = paymentTotals.getOrDefault(PaymentMethod.CASH, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal card = paymentTotals.getOrDefault(PaymentMethod.DEBIT, BigDecimal.ZERO)
                .add(paymentTotals.getOrDefault(PaymentMethod.CREDIT, BigDecimal.ZERO))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal transfer = paymentTotals.getOrDefault(PaymentMethod.TRANSFER, BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return new SalesReportDto(
                startDate,
                endDate,
                total,
                count,
                totalUnits,
                cash,
                card,
                transfer,
                payments,
                topProducts
        );
    }
}
