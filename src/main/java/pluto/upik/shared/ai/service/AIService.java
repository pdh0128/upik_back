package pluto.upik.shared.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pluto.upik.domain.guide.data.model.Guide;
import pluto.upik.domain.guide.repository.GuideRepository;
import pluto.upik.domain.option.data.model.Option;
import pluto.upik.domain.option.repository.OptionRepository;
import pluto.upik.domain.tail.data.model.Tail;
import pluto.upik.domain.tail.repository.TailRepository;
import pluto.upik.domain.tail.repository.TailResponseRepository;
import pluto.upik.domain.user.repository.UserRepository;
import pluto.upik.domain.vote.data.model.Vote;
import pluto.upik.domain.vote.repository.VoteRepository;
import pluto.upik.domain.voteResponse.data.model.VoteResponse;
import pluto.upik.domain.voteResponse.repository.VoteResponseRepository;
import pluto.upik.shared.ai.config.ChatAiService;
import pluto.upik.shared.ai.data.DTO.GuideResponseDTO;
import pluto.upik.shared.exception.BusinessException;
import pluto.upik.shared.exception.ResourceNotFoundException;
import pluto.upik.shared.translation.service.TranslationService;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService implements DisposableBean {
    private static final int MAX_CHUNK_SIZE = 450;

    private final TranslationService translationService;
    private final UserRepository userRepository;
    private final VoteRepository voteRepository;
    private final VoteResponseRepository voteResponseRepository;
    private final OptionRepository optionRepository;
    private final TailRepository tailRepository;
    private final TailResponseRepository tailResponseRepository;
    private final ChatAiService chatAiService;
    private final GuideRepository guideRepository;

    // 현재 진행 중인 AI 요청을 추적하기 위한 맵 (요청 ID -> 취소 플래그)
    private final Map<String, AtomicBoolean> activeRequests = new ConcurrentHashMap<>();

    // 전역 취소 플래그 (서버 종료 시 사용)
    private final AtomicBoolean globalCancellationFlag = new AtomicBoolean(false);

    private String removeThinkTags(String response) {
        if (response == null) return null;
        return response.replaceAll("(?is)<think>.*?</think>", "").trim();
    }

    private List<String> splitTextIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        String[] sentences = text.split("(?<=[.!?]\\s)");
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() > MAX_CHUNK_SIZE) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
            }
            currentChunk.append(sentence);
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private String translateLongText(String text, boolean koreanToEnglish) {
        List<String> chunks = splitTextIntoChunks(text);
        StringBuilder translatedText = new StringBuilder();

        for (String chunk : chunks) {
            String translatedChunk = koreanToEnglish
                    ? translationService.translateKoreanToEnglish(chunk)
                    : translationService.translateEnglishToKorean(chunk);
            translatedText.append(translatedChunk).append(" ");
        }

        return translatedText.toString().trim();
    }

    /**
     * 클라이언트 요청에 대한 처리를 시작합니다.
     * @param requestKey 요청을 식별하는 키
     * @return 이미 진행 중인 요청이 있었으면 true, 아니면 false
     */
    public boolean startRequest(String requestKey) {
        // 이전 요청이 있으면 취소
        boolean hadPreviousRequest = false;
        if (activeRequests.containsKey(requestKey)) {
            hadPreviousRequest = true;
            AtomicBoolean flag = activeRequests.get(requestKey);
            if (flag != null) {
                flag.set(true); // 취소 플래그 설정
                log.info("이전 요청 취소: {}", requestKey);
        }
        }

        // 새 요청 등록
        activeRequests.put(requestKey, new AtomicBoolean(false));
        log.info("새 요청 시작: {}", requestKey);
        return hadPreviousRequest;
    }
    /**
     * 클라이언트 요청 처리를 종료합니다.
     * @param requestKey 요청을 식별하는 키
     */
    public void endRequest(String requestKey) {
        activeRequests.remove(requestKey);
        log.info("요청 종료: {}", requestKey);
}
    /**
     * 클라이언트 연결이 끊어졌음을 표시합니다.
     * @param requestKey 요청을 식별하는 키
     */
    public void markClientDisconnected(String requestKey) {
        AtomicBoolean flag = activeRequests.get(requestKey);
        if (flag != null) {
            flag.set(true);
            log.info("클라이언트 연결 끊김 감지: {}", requestKey);
        }
    }
    /**
     * 요청이 취소되었는지 확인합니다.
     * @param requestKey 요청을 식별하는 키
     * @throws BusinessException 요청이 취소되었거나 서버가 종료 중이면 예외 발생
     */
    private void checkCancellation(String requestKey) {
        // 서버 종료 확인
        if (globalCancellationFlag.get()) {
            log.info("서버 종료로 인한 작업 취소");
            throw new BusinessException("서버 종료로 인한 작업 취소");
        }

        // 요청 취소 확인
        AtomicBoolean flag = activeRequests.get(requestKey);
        if (flag == null || flag.get()) {
            log.info("요청이 취소되었습니다: {}", requestKey);
            throw new BusinessException("요청이 취소되었습니다");
        }
    }
    /**
     * AI에게 질문하고 응답을 반환합니다.
     * @param question AI에게 물어볼 질문
     * @param requestKey 요청을 식별하는 키
     * @return AI 응답
     */
    private String askToDeepSeekAI(String question, String requestKey) {
        try {
            log.info("AI 요청 시작: {}", requestKey);

            // 취소 여부 확인
            checkCancellation(requestKey);

            // 질문 번역
            log.info("질문 번역 시작: {}", requestKey);
            String translatedQuestion = translateLongText(question, true);
            log.info("질문 번역 완료: {}", requestKey);

            // 취소 여부 다시 확인
            checkCancellation(requestKey);

            // AI에게 질문
            log.info("AI 호출 시작: {}", requestKey);
            String englishResponse = chatAiService.askToDeepSeekAI(translatedQuestion);
            log.info("AI 호출 완료: {}", requestKey);

            // 취소 여부 다시 확인
            checkCancellation(requestKey);

            // 응답 처리
            String result = removeThinkTags(englishResponse);
            log.info("AI 응답 처리 완료: {}", requestKey);

            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 서비스 호출 중 오류: {}", e.getMessage(), e);
            throw new BusinessException("AI 서비스 호출 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public GuideResponseDTO generateAndSaveGuide(UUID voteId, String type) {
        // 요청 키 생성 (voteId와 type으로 구성)
        String requestKey = voteId.toString() + "-" + type;

        try {
            // 요청 시작
            startRequest(requestKey);

            Vote vote = voteRepository.findById(voteId)
                    .orElseThrow(() -> new ResourceNotFoundException("투표를 찾을 수 없습니다."));

            vote.setStatus(Vote.Status.valueOf("CLOSED"));

            String voteTitle = vote.getQuestion();
            String voteDescription = optionRepository.findTopByVoteOrderByIdAsc(vote)
                    .map(Option::getContent)
                    .orElse("No description");

            // 취소 여부 확인
            checkCancellation(requestKey);

            // voteId로 실제 투표 옵션 및 응답을 가져와서 퍼센트 계산
            List<Option> options = optionRepository.findByVoteId(vote.getId());
            if (options == null || options.isEmpty()) {
                throw new ResourceNotFoundException("투표 옵션이 존재하지 않습니다.");
            }
            List<VoteResponse> voteResponses = voteResponseRepository.findByVoteId(vote.getId());

            Map<UUID, Long> voteCounts = new HashMap<>();
            long totalVotes = voteResponses.size();

            for (VoteResponse vr : voteResponses) {
                voteCounts.merge(vr.getSelectedOption().getId(), 1L, Long::sum);
            }

            StringBuilder optionPercentsBuilder = new StringBuilder();
            for (Option option : options) {
                long count = voteCounts.getOrDefault(option.getId(), 0L);
                double percent = totalVotes > 0 ? (count * 100.0 / totalVotes) : 0.0;
                optionPercentsBuilder
                        .append(option.getContent())
                        .append(" - ")
                        .append(String.format("%.1f", percent))
                        .append("%\n");
            }
            String optionsWithPercents = optionPercentsBuilder.toString().trim();

            // 취소 여부 확인
            checkCancellation(requestKey);

            Tail tail = tailRepository.findFirstByVote(vote)
                    .orElseThrow(() -> new ResourceNotFoundException("Tail 질문이 없습니다."));

            List<String> tailAnswers = tailResponseRepository.findByTail(tail).stream()
                    .map(tr -> tr.getAnswer())
                    .toList();

            String tailResponses = String.join("\n", tailAnswers);

            String prompt = String.format(
                    "Please generate a guide title and guide content for the following vote and responses. For each choice, don't put anything like \\ and just give it as plain text." +
                            "<content> Don't wrap it up like this"+
                            "The guide should be clear, informative, and in-depth.\n\n" +
                            "Vote Title: %s\n" +
                            "Option with the highest votes : %s\n" +
                            "Voting Results (percentages):\n%s\n\n" +
                            "Tail Question: %s\n" +
                            "Tail Responses:\n%s\n\n" +
                            "Write it like this :\n%s\n\n" +
                            "Please return the result in the following format I will keep my word unconditionally:\n" +
                            "Guide Title:\n<<title>>\n\n" +
                            "Guide Content:\n<<content>>\n ",
                    voteTitle, voteDescription, optionsWithPercents,
                    tail.getQuestion(), tailResponses, type
            );

            // 취소 여부 확인
            checkCancellation(requestKey);

            // AI 요청
            String result = askToDeepSeekAI(prompt, requestKey);

            // 취소 여부 확인
            checkCancellation(requestKey);

            int titleStart = result.indexOf("Guide Title:");
            int contentStart = result.indexOf("Guide Content:");

            if (titleStart == -1 || contentStart == -1) {
                log.error("AI 응답 포맷이 예상과 다릅니다. result: {}", result);
                throw new BusinessException("AI 응답 포맷이 예상과 다릅니다.");
            }

            String extractedTitle = result.substring(titleStart + "Guide Title:".length(), contentStart).trim();
            String extractedContent = result.substring(contentStart + "Guide Content:".length()).trim();

            // 취소 여부 확인
            checkCancellation(requestKey);

            // 제목과 내용 번역
            log.info("제목 번역 시작: {}", requestKey);
            String translatedTitle = translateLongText(extractedTitle, false);
            log.info("제목 번역 완료: {}", requestKey);

            // 취소 여부 확인
            checkCancellation(requestKey);

            log.info("내용 번역 시작: {}", requestKey);
            String translatedContent = translateLongText(extractedContent, false);
            log.info("내용 번역 완료: {}", requestKey);

            // 취소 여부 확인
            checkCancellation(requestKey);

            Guide guide = Guide.builder()
                    .vote(vote)
                    .title(translatedTitle)
                    .content(translatedContent)
                    .createdAt(LocalDate.now())
                    .category(vote.getCategory())
                    .guideType(type)
                    .revoteCount(0L)
                    .like(0L)
                    .build();

            guideRepository.save(guide);

            // GuideResponseDTO 형식으로 반환
            return GuideResponseDTO.builder()
                    .id(guide.getId())
                    .voteId(guide.getVote().getId())
                    .title(guide.getTitle())
                    .content(guide.getContent())
                    .createdAt(guide.getCreatedAt())
                    .category(guide.getCategory())
                    .guideType(type)
                    .revoteCount(guide.getRevoteCount())
                    .like(guide.getLike())
                    .build();
        } catch (ResourceNotFoundException | BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("가이드 생성 중 알 수 없는 오류: {}", e.getMessage(), e);
            throw new BusinessException("가이드 생성 중 오류가 발생했습니다.");
        } finally {
            // 요청 종료
            endRequest(requestKey);
        }
    }

    /**
     * 서버 종료 시 호출되는 메서드
     */
    @Override
    public void destroy() {
        log.info("AIService 종료 중...");
        globalCancellationFlag.set(true);
        log.info("AIService 종료 완료");
    }
}
