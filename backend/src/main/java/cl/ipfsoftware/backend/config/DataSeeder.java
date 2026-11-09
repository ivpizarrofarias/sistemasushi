package cl.ipfsoftware.backend.config;

import cl.ipfsoftware.backend.domain.Category;
import cl.ipfsoftware.backend.domain.Product;
import cl.ipfsoftware.backend.domain.Role;
import cl.ipfsoftware.backend.domain.User;
import cl.ipfsoftware.backend.repository.CategoryRepository;
import cl.ipfsoftware.backend.repository.ProductRepository;
import cl.ipfsoftware.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedData(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (userRepository.count() == 0) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("<ADMIN_PASSWORD>"));
                admin.setFullName("Administrador");
                admin.setRole(Role.ADMIN);
                userRepository.save(admin);

                User cashier = new User();
                cashier.setUsername("cajero");
                cashier.setPassword(passwordEncoder.encode("<CASHIER_PASSWORD>"));
                cashier.setFullName("Cajero");
                cashier.setRole(Role.CASHIER);
                userRepository.save(cashier);
            }

            Map<String, Category> categories = new LinkedHashMap<>();
            categories.put("Rolls", findOrCreateCategory(categoryRepository, "Rolls", "#ef4444"));
            categories.put("Combos", findOrCreateCategory(categoryRepository, "Combos", "#f59e0b"));
            categories.put("Bebidas", findOrCreateCategory(categoryRepository, "Bebidas", "#06b6d4"));
            categories.put("Postres", findOrCreateCategory(categoryRepository, "Postres", "#a855f7"));

            if (productRepository.count() == 0) {
                createProduct(productRepository, "Roll California (8 piezas)", "Palta, pepino y surimi", 4990, 30, 5, categories.get("Rolls"));
                createProduct(productRepository, "Roll Philadelphia (8 piezas)", "Salmón, queso crema y cebollín", 5490, 25, 5, categories.get("Rolls"));
                createProduct(productRepository, "Roll Acevichado (8 piezas)", "Salmón, palta y salsa acevichada", 5990, 20, 5, categories.get("Rolls"));
                createProduct(productRepository, "Combo 20 piezas", "Selección de rolls para 2 personas", 12990, 15, 3, categories.get("Combos"));
                createProduct(productRepository, "Combo 30 piezas", "Selección de rolls para 3-4 personas", 17990, 10, 3, categories.get("Combos"));
                createProduct(productRepository, "Bebida 500 ml", "Bebida en lata o botella", 1500, 40, 10, categories.get("Bebidas"));
                createProduct(productRepository, "Té helado", "Vaso de té helado", 1800, 30, 10, categories.get("Bebidas"));
                createProduct(productRepository, "Mochi (2 unidades)", "Mochi relleno de helado", 2500, 20, 5, categories.get("Postres"));
                createProduct(productRepository, "Cheesecake", "Porción de cheesecake", 3500, 10, 3, categories.get("Postres"));
            }
        };
    }

    private Category findOrCreateCategory(CategoryRepository repository, String name, String color) {
        return repository.findAllByOrderByNameAsc().stream()
                .filter(category -> category.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setName(name);
                    category.setColor(color);
                    return repository.save(category);
                });
    }

    private void createProduct(
            ProductRepository repository,
            String name,
            String description,
            double price,
            int stock,
            int minStock,
            Category category
    ) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(BigDecimal.valueOf(price));
        product.setStock(stock);
        product.setMinStock(minStock);
        product.setCategory(category);
        repository.save(product);
    }
}
