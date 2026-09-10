package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * One page of anything, in a shape this API controls.
 * <p>
 * Spring Data {@code Page} is deliberately not serialised directly: its JSON carries the whole
 * {@code Pageable} and {@code Sort} machinery, the field set is not part of its contract, and
 * Spring itself warns against exposing it. Four fields are all a client needs to render a pager,
 * and they will not change under it on a Spring upgrade.
 *
 * @param <T> what the page contains
 */
@Schema(name = "Paged", description = "One page of results with its position in the whole")
public record PagedResponse<T>(

        @Schema(description = "The items on this page")
        List<T> content,

        @Schema(description = "Zero-based page number", example = "0")
        int page,

        @Schema(description = "How many items were requested per page", example = "20")
        int size,

        @Schema(description = "How many items exist in total", example = "42")
        long totalElements,

        @Schema(description = "How many pages that makes", example = "3")
        int totalPages
) {

    /** Wraps a Spring page, mapping its contents on the way out. */
    public static <E, T> PagedResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
