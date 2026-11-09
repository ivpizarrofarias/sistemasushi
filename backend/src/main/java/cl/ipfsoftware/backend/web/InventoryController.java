package cl.ipfsoftware.backend.web;

import cl.ipfsoftware.backend.dto.StockMovementDto;
import cl.ipfsoftware.backend.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final ProductService productService;

    public InventoryController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/movements")
    public List<StockMovementDto> movements() {
        return productService.movements();
    }
}
