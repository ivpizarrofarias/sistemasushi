package cl.ipfsoftware.backend.service;

import cl.ipfsoftware.backend.domain.Category;
import cl.ipfsoftware.backend.domain.Product;
import cl.ipfsoftware.backend.domain.StockMovement;
import cl.ipfsoftware.backend.domain.StockMovementType;
import cl.ipfsoftware.backend.domain.User;
import cl.ipfsoftware.backend.dto.ProductDto;
import cl.ipfsoftware.backend.dto.ProductRequest;
import cl.ipfsoftware.backend.dto.RestockRequest;
import cl.ipfsoftware.backend.dto.StockMovementDto;
import cl.ipfsoftware.backend.repository.CategoryRepository;
import cl.ipfsoftware.backend.repository.ProductRepository;
import cl.ipfsoftware.backend.repository.StockMovementRepository;
import cl.ipfsoftware.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            StockMovementRepository stockMovementRepository,
            UserRepository userRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductDto> list(boolean activeOnly, Long categoryId) {
        List<Product> products;
        if (categoryId != null) {
            products = activeOnly
                    ? productRepository.findByCategoryIdAndActiveTrueOrderByNameAsc(categoryId)
                    : productRepository.findAllByOrderByNameAsc().stream()
                    .filter(p -> p.getCategory().getId().equals(categoryId))
                    .toList();
        } else {
            products = activeOnly
                    ? productRepository.findByActiveTrueOrderByNameAsc()
                    : productRepository.findAllByOrderByNameAsc();
        }
        return products.stream().map(ProductDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductDto get(Long id) {
        return ProductDto.from(requireProduct(id));
    }

    @Transactional
    public ProductDto create(ProductRequest request) {
        String name = request.name().trim();
        if (productRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Ya existe un producto con ese nombre");
        }
        Category category = requireCategory(request.categoryId());
        Product product = new Product();
        product.setName(name);
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setMinStock(request.minStock());
        product.setCategory(category);
        product.setActive(true);
        return ProductDto.from(productRepository.save(product));
    }

    @Transactional
    public ProductDto update(Long id, ProductRequest request, String username) {
        Product product = requireProductForUpdate(id);
        String name = request.name().trim();
        if (productRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("Ya existe un producto con ese nombre");
        }
        Category category = requireCategory(request.categoryId());

        int previousStock = product.getStock();
        int newStock = request.stock();

        product.setName(name);
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(newStock);
        product.setMinStock(request.minStock());
        product.setCategory(category);

        if (previousStock != newStock) {
            saveMovement(product, StockMovementType.ADJUSTMENT, newStock - previousStock,
                    previousStock, newStock, "Ajuste manual de stock", username);
        }

        return ProductDto.from(product);
    }

    @Transactional
    public ProductDto restock(Long id, RestockRequest request, String username) {
        Product product = requireProductForUpdate(id);

        int previous = product.getStock();
        int updated = previous + request.quantity();
        product.setStock(updated);

        saveMovement(product, StockMovementType.RESTOCK, request.quantity(),
                previous, updated,
                request.reason() == null || request.reason().isBlank()
                        ? "Reposición de stock"
                        : request.reason(),
                username);

        return ProductDto.from(product);
    }

    @Transactional
    public void deactivate(Long id) {
        Product product = requireProduct(id);
        product.setActive(false);
    }

    @Transactional(readOnly = true)
    public List<StockMovementDto> movements() {
        return stockMovementRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(StockMovementDto::from)
                .toList();
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado"));
    }

    private Product requireProductForUpdate(Long id) {
        return productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado"));
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Categoría no encontrada"));
    }

    private void saveMovement(
            Product product,
            StockMovementType type,
            int quantity,
            int previousStock,
            int newStock,
            String reason,
            String username
    ) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newStock);
        movement.setReason(reason);
        movement.setUser(user);
        stockMovementRepository.save(movement);
    }
}
