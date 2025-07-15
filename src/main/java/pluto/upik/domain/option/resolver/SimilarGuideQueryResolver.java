package pluto.upik.domain.option.resolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import pluto.upik.domain.option.data.DTO.SimilarGuidesResponse;
import pluto.upik.domain.option.service.OptionGeneratorServiceInterface;
import pluto.upik.shared.exception.BusinessException;

import java.util.Collections;

/**
 * 유사 가이드 검색 GraphQL 쿼리 리졸버
 * 제목을 받아 유사한 가이드를 검색하는 GraphQL 쿼리를 처리합니다.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SimilarGuideQueryResolver {
    private final OptionGeneratorServiceInterface optionGeneratorService;

    /**
     * 제목과 유사한 가이드를 검색하는 메서드
     * OptionGeneratorQuery의 findSimilarGuidesByTitle 필드에 매핑됩니다.
     * 가이드 타입과 유저 정보를 포함한 모든 데이터를 반환합니다.
     *
     * @param parent 부모 객체
     * @param title 검색할 제목
     * @return 유사 가이드 검색 결과 (가이드 타입과 유저 정보 포함)
     */
    @SchemaMapping(typeName = "OptionGeneratorQuery", field = "findSimilarGuidesByTitle")
    public SimilarGuidesResponse findSimilarGuidesByTitle(Object parent, @Argument String title) {
        log.info("GraphQL 쿼리 - 유사 가이드 검색 요청: 제목={}", title);

        try {
            // 입력값 검증
            if (title == null || title.trim().isEmpty()) {
                log.warn("GraphQL 쿼리 - 유사 가이드 검색 실패: 제목이 비어있음");
                return SimilarGuidesResponse.builder()
                        .success(false)
                        .message("제목을 입력해주세요.")
                        .guides(Collections.emptyList())
                        .count(0)
                        .build();
            }

            // 서비스 호출 - 가이드 타입과 유저 정보를 포함한 모든 데이터 조회
            SimilarGuidesResponse response = optionGeneratorService.findSimilarGuides(title);

            if (response.isSuccess() && response.getCount() > 0) {
                log.info("GraphQL 쿼리 - 유사 가이드 검색 성공: 제목={}, 찾은 가이드 수={}, 첫 번째 가이드 타입={}",
                        title, response.getCount(),
                        response.getGuides().isEmpty() ? "없음" : response.getGuides().get(0).getGuideType());
            } else {
                log.info("GraphQL 쿼리 - 유사 가이드 검색 결과 없음: 제목={}", title);
            }

            return response;
        } catch (BusinessException e) {
            log.warn("GraphQL 쿼리 - 유사 가이드 검색 중 비즈니스 오류: 제목={}, 사유={}",
                    title, e.getMessage());
            return SimilarGuidesResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .guides(Collections.emptyList())
                    .count(0)
                    .build();
        } catch (Exception e) {
            log.error("GraphQL 쿼리 - 유사 가이드 검색 중 예상치 못한 오류: 제목={}, 오류={}",
                    title, e.getMessage(), e);
            return SimilarGuidesResponse.builder()
                    .success(false)
                    .message("가이드 검색 중 오류가 발생했습니다: " + e.getMessage())
                    .guides(Collections.emptyList())
                    .count(0)
                    .build();
        }
    }
}