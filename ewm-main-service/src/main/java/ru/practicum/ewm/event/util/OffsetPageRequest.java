package ru.practicum.ewm.event.util;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import ru.practicum.ewm.exception.BadRequestException;

public class OffsetPageRequest implements Pageable {

    private final long offset;
    private final int size;
    private final Sort sort;

    public OffsetPageRequest(long offset, int size, Sort sort) {
        if (offset < 0) {
            throw new BadRequestException("Параметр from не может быть отрицательным");
        }

        if (size <= 0) {
            throw new BadRequestException("Параметр size должен быть больше нуля");
        }

        this.offset = offset;
        this.size = size;
        this.sort = sort;
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / size);
    }

    @Override
    public int getPageSize() {
        return size;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetPageRequest(offset + size, size, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return new OffsetPageRequest(
                Math.max(0, offset - size),
                size,
                sort
        );
    }

    @Override
    public Pageable first() {
        return new OffsetPageRequest(0, size, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        if (pageNumber < 0) {
            throw new BadRequestException("Номер страницы не может быть отрицательным");
        }

        return new OffsetPageRequest(
                (long) pageNumber * size,
                size,
                sort
        );
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}