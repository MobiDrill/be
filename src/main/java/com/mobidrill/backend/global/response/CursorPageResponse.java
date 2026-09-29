package com.mobidrill.backend.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Builder
@Schema(description = "커서와 페이지 정보를 함께 제공하는 페이지네이션 응답")
public record CursorPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious,
        Long nextCursor
) {

    public static <T> CursorPageResponse<T> of(Page<T> page, Long nextCursor) {
        return CursorPageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber() + 1)
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .nextCursor(page.hasNext() ? nextCursor : null)
                .build();
    }

    public static <E, T> CursorPageResponse<T> of(
            Page<E> page,
            Function<E, T> mapper,
            Long nextCursor
    ) {
        List<T> content = page.getContent().stream()
                .map(mapper)
                .toList();

        return CursorPageResponse.<T>builder()
                .content(content)
                .page(page.getNumber() + 1)
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .nextCursor(page.hasNext() ? nextCursor : null)
                .build();
    }
}
