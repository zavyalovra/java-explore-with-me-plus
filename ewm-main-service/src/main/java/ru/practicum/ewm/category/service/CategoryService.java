package ru.practicum.ewm.category.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.dto.NewCategoryDto;
import ru.practicum.ewm.category.mapper.CategoryMapper;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "id");

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public CategoryDto create(NewCategoryDto newCategoryDto) {
        if (categoryRepository.existsByName(newCategoryDto.getName())) {
            throw new ConflictException("Category with name=" + newCategoryDto.getName() + " already exists");
        }

        Category category = categoryRepository.save(categoryMapper.toEntity(newCategoryDto));
        return categoryMapper.toDto(category);
    }

    @Transactional
    public CategoryDto update(Long catId, CategoryDto categoryDto) {
        Category category = findCategory(catId);

        if (categoryRepository.existsByNameAndIdNot(categoryDto.getName(), catId)) {
            throw new ConflictException("Category with name=" + categoryDto.getName() + " already exists");
        }

        category.setName(categoryDto.getName());
        return categoryMapper.toDto(category);
    }

    @Transactional
    public void delete(Long catId) {
        Category category = findCategory(catId);
        categoryRepository.delete(category);
        categoryRepository.flush();
    }

    public List<CategoryDto> getAll(int from, int size) {
        validatePagination(from, size);

        Pageable pageable = PageRequest.of(from / size, size, DEFAULT_SORT);
        Page<Category> page = categoryRepository.findAll(pageable);

        return page.getContent().stream()
                .map(categoryMapper::toDto)
                .toList();
    }

    public CategoryDto getById(Long catId) {
        return categoryMapper.toDto(findCategory(catId));
    }

    private void validatePagination(int from, int size) {
        if (from < 0) {
            throw new BadRequestException("Параметр 'from' должен быть больше или равен 0");
        }
        if (size <= 0) {
            throw new BadRequestException("Параметр 'size' должен быть больше 0");
        }
    }

    private Category findCategory(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
    }
}
