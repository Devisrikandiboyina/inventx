package org.enggis.enventx.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PagedResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static <E, T> PagedResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().<T>map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
