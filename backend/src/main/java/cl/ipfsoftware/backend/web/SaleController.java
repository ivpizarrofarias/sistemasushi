package cl.ipfsoftware.backend.web;

import cl.ipfsoftware.backend.dto.SaleDto;
import cl.ipfsoftware.backend.dto.SaleRequest;
import cl.ipfsoftware.backend.service.SaleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleDto create(@Valid @RequestBody SaleRequest request, Authentication authentication) {
        return saleService.create(request, authentication.getName());
    }

    @GetMapping
    public List<SaleDto> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return saleService.byDate(date);
    }

    @GetMapping("/{id}")
    public SaleDto get(@PathVariable Long id) {
        return saleService.get(id);
    }

    @PostMapping("/{id}/void")
    public SaleDto voidSale(@PathVariable Long id, Authentication authentication) {
        return saleService.voidSale(id, authentication.getName());
    }
}
