package pluto.upik.domain.option.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pluto.upik.domain.guide.data.DTO.KeywordGuideResponse;
import pluto.upik.domain.guide.service.KeywordGuideServiceInterface;
import pluto.upik.domain.option.data.DTO.GenerateOptionsResponse;
import pluto.upik.domain.option.data.DTO.GuideSimpleInfo;
import pluto.upik.domain.option.data.DTO.SimilarGuidesResponse;
import pluto.upik.shared.ai.config.ChatAiService;
import pluto.upik.shared.exception.BusinessException;
import pluto.upik.shared.exception.ResourceNotFoundException;
import pluto.upik.shared.translation.service.TranslationService;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 선택지 생성 서비스 구현 클래스
 * AI를 활용하여 제목에 맞는 선택지를 생성합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptionGeneratorServiceImpl implements OptionGeneratorServiceInterface {

    private final ChatAiService chatAiService;
    private final TranslationService translationService;
    private final KeywordGuideServiceInterface keywordGuideService;

    private static final int AI_RESPONSE_TIMEOUT_SECONDS = 30;
    private static final int MAX_SUMMARY_LENGTH = 100;

    /**
     * 제목에 맞는 선택지를 생성합니다.
     *
     * @param title 제목
     * @param count 생성할 선택지 개수
     * @return 생성된 선택지 응답
     */
    @Override
    public GenerateOptionsResponse generateOptions(String title, int count) {
        Map<String, Object> logParams = new HashMap<>();
        logParams.put("제목", title);
        logParams.put("요청 개수", count);
        log.info("선택지 생성 시작 - {}", LoggingUtils.formatParams(logParams));
        
        try {
            CompletableFuture<GenerateOptionsResponse> future = generateOptionsAsync(title, count);
            log.debug("비동기 선택지 생성 작업 시작됨 - 제목: {}, 타임아웃: {}초", title, AI_RESPONSE_TIMEOUT_SECONDS);
            
            GenerateOptionsResponse response = future.get(AI_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            logParams.put("생성된 선택지 개수", response.getOptions().size());
            logParams.put("성공 여부", response.isSuccess());
            log.info("선택지 생성 완료 - {}", LoggingUtils.formatParams(logParams));
            
            if (response.isSuccess() && response.getOptions().size() > 0) {
                log.debug("생성된 선택지 목록: {}", response.getOptions());
            }
            
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("선택지 생성 중 인터럽트 발생 - 제목: {}, 개수: {}", title, count, e);
            return buildErrorResponse("선택지 생성이 중단되었습니다.");
        } catch (ExecutionException e) {
            log.error("선택지 생성 중 실행 오류 발생 - 제목: {}, 개수: {}, 원인: {}", 
                    title, count, e.getCause() != null ? e.getCause().getMessage() : "알 수 없음", e);
            return buildErrorResponse("선택지 생성 중 오류가 발생했습니다: " + 
                    (e.getCause() != null ? e.getCause().getMessage() : "알 수 없는 오류"));
        } catch (TimeoutException e) {
            log.error("선택지 생성 시간 초과 - 제목: {}, 개수: {}, 타임아웃: {}초", title, count, AI_RESPONSE_TIMEOUT_SECONDS, e);
            return buildErrorResponse("선택지 생성 시간이 초과되었습니다. 나중에 다시 시도해주세요.");
        }
    }
    
    /**
     * 제목에 맞는 선택지를 비동기적으로 생성합니다.
     *
     * @param title 제목
     * @param count 생성할 선택지 개수
     * @return 생성된 선택지 응답의 CompletableFuture
     */
    @Async("taskExecutor")
    public CompletableFuture<GenerateOptionsResponse> generateOptionsAsync(String title, int count) {
        long startTime = System.currentTimeMillis();
        log.debug("비동기 선택지 생성 시작 - 제목: {}, 개수: {}, 시작 시간: {}", title, count, startTime);
        
        try {
            // 한국어 제목을 영어로 번역
            log.debug("제목 번역 시작 (한국어 → 영어) - 원본 제목: {}", title);
            String translatedTitle = translationService.translateKoreanToEnglish(title);
            log.debug("제목 번역 완료 (한국어 → 영어) - 원본: '{}', 번역: '{}'", title, translatedTitle);
            
            // AI에게 선택지 생성 요청 - 개선된 프롬프트
            String prompt = buildPrompt(translatedTitle, count);
            log.debug("AI 프롬프트 생성 완료 - 길이: {} 글자", prompt.length());

            log.debug("AI 요청 시작 - 번역된 제목: {}, 요청 선택지 개수: {}", translatedTitle, count);
            long aiRequestStartTime = System.currentTimeMillis();
            String aiResponse = chatAiService.askToDeepSeekAI(prompt);
            long aiRequestDuration = System.currentTimeMillis() - aiRequestStartTime;
            log.debug("AI 응답 수신 완료 - 소요시간: {}ms, 응답 길이: {} 글자", aiRequestDuration, aiResponse.length());
            log.trace("AI 응답 원문: {}", aiResponse);

            // AI 응답에서 선택지 추출
            log.debug("AI 응답에서 선택지 추출 시작");
            List<String> englishOptions = extractOptionsFromAiResponse(aiResponse, count);
            log.debug("선택지 추출 완료 - 추출된 영어 선택지 개수: {}", englishOptions.size());
            log.debug("추출된 영어 선택지 목록: {}", englishOptions);
            // 영어 선택지를 한국어로 번역
            log.debug("선택지 번역 시작 (영어 → 한국어) - 선택지 개수: {}", englishOptions.size());
            List<String> koreanOptions = new ArrayList<>();
            for (int i = 0; i < englishOptions.size(); i++) {
                String englishOption = englishOptions.get(i);
                log.trace("선택지 #{} 번역 시작 - 영어: '{}'", i+1, englishOption);
                String koreanOption = translationService.translateEnglishToKorean(englishOption);
                log.trace("선택지 #{} 번역 완료 - 영어: '{}', 한국어: '{}'", i+1, englishOption, koreanOption);
                koreanOptions.add(koreanOption);
            }
            log.debug("선택지 번역 완료 - 번역된 한국어 선택지 개수: {}", koreanOptions.size());
            log.debug("번역된 한국어 선택지 목록: {}", koreanOptions);

            long totalDuration = System.currentTimeMillis() - startTime;
            log.info("선택지 생성 프로세스 완료 - 제목: {}, 총 소요시간: {}ms, 생성된 선택지 개수: {}/{}",
                    title, totalDuration, koreanOptions.size(), count);

            return CompletableFuture.completedFuture(
                GenerateOptionsResponse.builder()
                .success(true)
                    .message("선택지가 성공적으로 생성되었습니다.")
                    .options(koreanOptions)
                    .build()
            );
        } catch (Exception e) {
            long errorTime = System.currentTimeMillis() - startTime;
            log.error("선택지 비동기 생성 중 오류 발생 - 제목: {}, 개수: {}, 소요시간: {}ms, 오류 유형: {}",
                    title, count, errorTime, e.getClass().getName(), e);
            throw new BusinessException("선택지 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    /**
     * AI에게 보낼 프롬프트를 구성합니다.
     *
     * @param title 번역된 제목
     * @param count 생성할 선택지 개수
     * @return 구성된 프롬프트
     */
    private String buildPrompt(String title, int count) {
        return String.format(
                "You are a poll option generator.\\n" +
                        "Generate exactly %d short and distinct options for the poll titled: \"%s\"\\n\\n" +

                        "Rules:\\n" +
                        "- Each option: 1~10 characters long (including spaces)\\n" +
                        "- No explanations, no sentences, no extra text\\n" +
                        "- Only specific, popular, diverse answers\\n" +
                        "- Use nouns only (e.g., 'Pizza', 'Seoul')\\n\\n" +

                        "Format:\\n" +
                        "1. Option\\n" +
                        "2. Option\\n" +
                        "...\\n\\n" +

                        "Respond ONLY with the numbered list.",
                count, title
        );


    }

    /**
     * AI 응답에서 선택지를 추출합니다.
     *
     * @param aiResponse AI 응답 텍스트
     * @param expectedCount 예상되는 선택지 개수
     * @return 추출된 선택지 목록
     */
    private List<String> extractOptionsFromAiResponse(String aiResponse, int expectedCount) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            log.warn("AI 응답이 비어있거나 null입니다.");
            throw new BusinessException("AI가 선택지를 생성하지 못했습니다.");
}

        // 번호 패턴(1., 2. 등)으로 시작하는 줄을 찾아 선택지로 추출
        List<String> options = new ArrayList<>();
        String[] lines = aiResponse.split("\n");

        log.debug("AI 응답 파싱 시작 - 총 라인 수: {}", lines.length);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            log.trace("라인 #{} 분석: '{}'", i+1, trimmedLine);

            // 번호 패턴 확인 (예: "1. ", "2. " 등)
            if (trimmedLine.matches("^\\d+\\..*")) {
                // 번호와 점(.)을 제거하고 앞뒤 공백 제거
                String option = trimmedLine.replaceFirst("^\\d+\\.\\s*", "").trim();
                log.trace("번호 패턴 매칭 - 추출된 선택지: '{}'", option);

                if (!option.isEmpty() && !option.endsWith("?")) {  // 질문 형태 제외
                    options.add(option);
                    log.trace("선택지 추가됨: '{}'", option);
                } else if (option.isEmpty()) {
                    log.trace("빈 선택지 무시됨");
                } else if (option.endsWith("?")) {
                    log.trace("질문 형태 선택지 무시됨: '{}'", option);
                }
            } else {
                log.trace("번호 패턴 매칭되지 않음, 무시됨");
            }
        }

        log.debug("번호 패턴 추출 결과 - 찾은 선택지 개수: {}/{}", options.size(), expectedCount);

        // 충분한 선택지가 추출되지 않았을 경우, 다른 방식으로 시도
        if (options.size() < expectedCount) {
            log.warn("번호 패턴으로 충분한 선택지를 추출하지 못했습니다. 다른 방식으로 시도합니다. (현재: {}/{})",
                    options.size(), expectedCount);

            List<String> alternativeOptions = Arrays.stream(lines)
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.endsWith("?"))  // 질문 형태 제외
                .limit(expectedCount)
                .collect(Collectors.toList());

            log.debug("대체 추출 방식 결과 - 찾은 선택지 개수: {}", alternativeOptions.size());

            if (alternativeOptions.size() > options.size()) {
                log.info("대체 추출 방식이 더 많은 선택지를 찾았습니다. 대체 결과를 사용합니다. ({} → {})",
                        options.size(), alternativeOptions.size());
                options = alternativeOptions;
            }
        }

        // 선택지가 여전히 부족하면 기본 선택지 추가
        if (options.size() < expectedCount) {
            int deficit = expectedCount - options.size();
            log.warn("충분한 선택지를 추출하지 못했습니다. 기본 선택지 {}개를 추가합니다.", deficit);

            List<String> defaultOptions = Arrays.asList(
                "Paris", "Tokyo", "New York", "London", "Rome",
                "Sydney", "Barcelona", "Seoul", "Venice", "Hawaii"
            );

            for (int i = 0; i < deficit && i < defaultOptions.size(); i++) {
                String defaultOption = defaultOptions.get(i);
                options.add(defaultOption);
                log.debug("기본 선택지 추가됨: '{}'", defaultOption);
            }

            log.debug("기본 선택지 추가 후 총 선택지 개수: {}/{}", options.size(), expectedCount);
        }

        // 요청한 개수만큼 반환 (부족하면 있는 만큼만)
        List<String> finalOptions = options.stream()
            .limit(expectedCount)
            .collect(Collectors.toList());

        log.debug("최종 선택지 목록 ({}개): {}", finalOptions.size(), finalOptions);
        return finalOptions;
    }

    /**
     * 오류 응답을 생성합니다.
     *
     * @param message 오류 메시지
     * @return 오류 응답
     */
    private GenerateOptionsResponse buildErrorResponse(String message) {
        log.warn("오류 응답 생성 - 메시지: {}", message);
        return GenerateOptionsResponse.builder()
            .success(false)
            .message(message)
            .options(new ArrayList<>())
            .build();
    }

    /**
     * 제목과 유사한 가이드를 검색합니다.
     *
     * @param title 검색할 제목
     * @return 유사 가이드 검색 결과
     */
    @Override
    public SimilarGuidesResponse findSimilarGuides(String title) {
        Map<String, Object> logParams = new HashMap<>();
        logParams.put("제목", title);
        log.info("유사 가이드 검색 시작 - {}", LoggingUtils.formatParams(logParams));

        if (title == null || title.trim().isEmpty()) {
            log.warn("유사 가이드 검색 실패 - 제목이 비어있거나 null입니다.");
            return SimilarGuidesResponse.builder()
                .success(false)
                .message("제목을 입력해주세요.")
                .guides(new ArrayList<>())
                .count(0)
                .build();
        }

        try {
            long startTime = System.currentTimeMillis();
            log.debug("키워드 서비스를 통한 가이드 검색 시작 - 키워드: '{}'", title);

            // 키워드 서비스를 통해 유사한 가이드 검색
            List<KeywordGuideResponse> keywordGuides = keywordGuideService.searchGuidesByKeyword(title);

            long searchDuration = System.currentTimeMillis() - startTime;
            log.debug("키워드 검색 완료 - 키워드: '{}', 찾은 가이드 수: {}, 소요시간: {}ms",
                    title, keywordGuides.size(), searchDuration);

            // 각 가이드에 대한 상세 정보 로깅
            for (int i = 0; i < keywordGuides.size(); i++) {
                KeywordGuideResponse guide = keywordGuides.get(i);
                log.debug("검색된 가이드 #{} - ID: {}, 제목: '{}', 내용 길이: {} 글자",
                        i+1, guide.getId(), guide.getTitle(),
                        guide.getContent() != null ? guide.getContent().length() : 0);
            }

            // GuideSimpleInfo로 변환
            List<GuideSimpleInfo> guideInfos = keywordGuides.stream()
                .map(guide -> {
                    String summary = createSummary(guide.getContent());
                    log.trace("가이드 요약 생성 - ID: {}, 원본 길이: {} 글자, 요약 길이: {} 글자",
                            guide.getId(),
                            guide.getContent() != null ? guide.getContent().length() : 0,
                            summary.length());

                    return GuideSimpleInfo.builder()
                        .id(guide.getId())
                        .title(guide.getTitle())
                        .summary(summary)
                        .build();
                })
                .collect(Collectors.toList());

            logParams.put("찾은 가이드 수", guideInfos.size());
            logParams.put("소요시간", System.currentTimeMillis() - startTime + "ms");
            log.info("유사 가이드 검색 완료 - {}", LoggingUtils.formatParams(logParams));

            return SimilarGuidesResponse.builder()
                .success(true)
                .message("유사한 가이드를 찾았습니다.")
                .guides(guideInfos)
                .count(guideInfos.size())
                .build();

        } catch (ResourceNotFoundException e) {
            log.info("유사 가이드 검색 결과 없음 - 제목: '{}', 사유: {}", title, e.getMessage());
            return SimilarGuidesResponse.builder()
                .success(true)
                .message("유사한 가이드가 없습니다.")
                .guides(new ArrayList<>())
                .count(0)
                .build();
        } catch (Exception e) {
            log.error("유사 가이드 검색 중 예기치 않은 오류 발생 - 제목: '{}', 오류 유형: {}, 메시지: {}",
                    title, e.getClass().getName(), e.getMessage(), e);
            return SimilarGuidesResponse.builder()
                .success(false)
                .message("가이드 검색 중 오류가 발생했습니다: " + e.getMessage())
                .guides(new ArrayList<>())
                .count(0)
                .build();
        }
    }

    /**
     * 가이드 내용에서 요약을 생성합니다.
     *
     * @param content 가이드 내용
     * @return 요약된 내용
     */
    private String createSummary(String content) {
        if (content == null || content.isEmpty()) {
            log.trace("요약 생성 - 원본 내용이 비어있거나 null입니다.");
            return "";
        }

        // 내용이 최대 길이보다 길면 잘라서 "..." 추가
        if (content.length() > MAX_SUMMARY_LENGTH) {
            String summary = content.substring(0, MAX_SUMMARY_LENGTH) + "...";
            log.trace("요약 생성 - 원본 길이: {} 글자, 요약 길이: {} 글자", content.length(), summary.length());
            return summary;
        }

        log.trace("요약 생성 - 내용이 충분히 짧아 그대로 사용 ({} 글자)", content.length());
        return content;
    }

    /**
     * 파라미터 맵을 문자열로 포맷팅합니다.
     * LoggingUtils 클래스가 없을 경우를 대비한 내부 메서드
     */
    private static class LoggingUtils {
        public static String formatParams(Map<String, Object> params) {
            if (params == null || params.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            params.forEach((key, value) -> {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(key).append(": ").append(value);
            });

            return sb.toString();
        }
    }
}
