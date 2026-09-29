package com.mobidrill.backend.global.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CursorPageResponseTest {

    @Test
    @DisplayName("Page 결과를 1부터 시작하는 페이지 정보와 다음 커서로 변환한다")
    void 페이지와_다음_커서_변환_성공() {
        // given
        PageImpl<Long> page = new PageImpl<>(
                List.of(11L, 10L),
                PageRequest.of(0, 2),
                4
        );

        // when
        CursorPageResponse<String> response = CursorPageResponse.of(
                page,
                String::valueOf,
                10L
        );

        // then
        assertThat(response.content()).containsExactly("11", "10");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(4);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.hasPrevious()).isFalse();
        assertThat(response.nextCursor()).isEqualTo(10L);
    }

    @Test
    @DisplayName("마지막 페이지는 다음 커서를 노출하지 않는다")
    void 마지막_페이지_다음_커서_미노출_성공() {
        // given
        PageImpl<Long> page = new PageImpl<>(
                List.of(9L),
                PageRequest.of(1, 2),
                3
        );

        // when
        CursorPageResponse<Long> response = CursorPageResponse.of(page, 9L);

        // then
        assertThat(response.hasNext()).isFalse();
        assertThat(response.hasPrevious()).isTrue();
        assertThat(response.nextCursor()).isNull();
    }
}
