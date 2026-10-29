package ru.practicum.ewm.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import ru.practicum.ewm.exception.BadRequestException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageRequestFactoryTest {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "id");

    @Test
    void shouldCalculatePageNumberFromFrom() {
        Pageable pageable = PageRequestFactory.create(20, 10, DEFAULT_SORT);;

        assertEquals(2, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
    }

    @Test
    void shouldReturnFirstPageWhenFromIsZero() {
        Pageable pageable = PageRequestFactory.create(0, 5, DEFAULT_SORT);

        assertEquals(0, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());
    }

    @Test
    void shouldApplySort() {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdOn");

        Pageable pageable = PageRequestFactory.create(0, 10, sort);

        assertEquals(sort, pageable.getSort());
    }

    @Test
    void shouldThrowWhenFromIsNegative() {
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> PageRequestFactory.create(-1, 10, DEFAULT_SORT));

        assertEquals("Параметр 'from' должен быть больше или равен 0", exception.getMessage());
    }

    @Test
    void shouldThrowWhenSizeIsZero() {
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> PageRequestFactory.create(0, 0, DEFAULT_SORT));

        assertEquals("Параметр 'size' должен быть больше 0", exception.getMessage());
    }

    @Test
    void shouldThrowWhenSizeIsNegative() {
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> PageRequestFactory.create(0, -5, DEFAULT_SORT));

        assertEquals("Параметр 'size' должен быть больше 0", exception.getMessage());
    }
}
