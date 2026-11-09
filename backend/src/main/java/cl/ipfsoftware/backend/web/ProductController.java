package cl.ipfsoftware.backend.web;

import cl.ipfsoftware.backend.dto.ProductDto;
import cl.ipfsoftware.backend.dto.ProductRequest;
import cl.ipfsoftware.backend.dto.RestockRequest;
import cl.ipfsoftware.backend.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductDto> list(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(required = false) Long categoryId
    ) {
        return productService.list(activeOnly, categoryId);
    }

    @GetMapping("/{id}")
    public ProductDto get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductDto update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request,
            Authentication authentication
    ) {
        return productService.update(id, request, authentication.getName());
    }

    @PatchMapping("/{id}/restock")
    public ProductDto restock(
            @PathVariable Long id,
            @Valid @RequestBody RestockRequest request,
            Authentication authentication
    ) {
        return productService.restock(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        productService.deactivate(id);
    }
}
