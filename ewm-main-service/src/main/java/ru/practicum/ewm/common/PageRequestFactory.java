package ru.practicum.ewm.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import ru.practicum.ewm.exception.BadRequestException;

/**
 * Единая точка создания Pageable для всех доменов.
 * Принимает параметры пагинации из запроса (from/size) и проверяет их корректность.
 */
public final class PageRequestFactory {

    private PageRequestFactory() {
    }

    public static Pageable create(int from, int size, Sort sort) {
        if (from < 0) {
            throw new BadRequestException("Параметр 'from' должен быть больше или равен 0");
        }
        if (size <= 0) {
            throw new BadRequestException("Параметр 'size' должен быть больше 0");
        }

        return PageRequest.of(from / size, size, sort);
    }
}
