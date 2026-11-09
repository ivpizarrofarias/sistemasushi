package cl.ipfsoftware.backend.service;

import cl.ipfsoftware.backend.domain.Category;
import cl.ipfsoftware.backend.dto.CategoryDto;
import cl.ipfsoftware.backend.dto.CategoryRequest;
import cl.ipfsoftware.backend.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> list() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(CategoryDto::from).toList();
    }

    @Transactional
    public CategoryDto create(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Ya existe una categoría con ese nombre");
        }
        Category category = new Category();
        category.setName(name);
        category.setColor(request.color() == null || request.color().isBlank() ? "#ef4444" : request.color());
        return CategoryDto.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Categoría no encontrada"));
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("Ya existe una categoría con ese nombre");
        }
        category.setName(name);
        if (request.color() != null && !request.color().isBlank()) {
            category.setColor(request.color());
        }
        return CategoryDto.from(category);
    }

    @Transactional
    public void deactivate(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Categoría no encontrada"));
        category.setActive(false);
    }
}
