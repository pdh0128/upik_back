package pluto.upik.domain.vote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pluto.upik.domain.option.data.model.Option;
import pluto.upik.domain.option.repository.OptionRepository;
import pluto.upik.domain.user.data.model.User;
import pluto.upik.domain.user.repository.UserRepository;
import pluto.upik.domain.vote.data.DTO.CreateVoteInput;
import pluto.upik.domain.vote.data.DTO.OptionWithStatsPayload;
import pluto.upik.domain.vote.data.DTO.VoteDetailPayload;
import pluto.upik.domain.vote.data.DTO.VotePayload;
import pluto.upik.domain.vote.data.model.Vote;
import pluto.upik.domain.vote.repository.VoteRepository;
import pluto.upik.domain.voteResponse.repository.VoteResponseRepository;
import pluto.upik.domain.voteResponse.service.VoteResponseService;
import pluto.upik.shared.exception.ResourceNotFoundException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class VoteServiceWithMyVotes {

    private final VoteRepository voteRepository;
    private final OptionRepository optionRepository;
    private final VoteResponseRepository voteResponseRepository;
    private final UserRepository userRepository;
    private final VoteResponseService voteResponseService;

    // 더미 사용자 ID
    private static final UUID DUMMY_USER_ID = UUID.fromString("e49207e8-471a-11f0-937c-42010a800003");

    public VotePayload createVote(CreateVoteInput input) {
        // 더미 사용자 조회
        User dummyUser = userRepository.findById(DUMMY_USER_ID)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다: " + DUMMY_USER_ID));
        
        // 1. Vote 엔티티 생성 (빌더에 user 정보 추가)
        Vote vote = Vote.builder()
                .id(UUID.randomUUID())
                .question(input.getTitle())
                .category(input.getCategory())
                .status(Vote.Status.OPEN)
                .finishedAt(LocalDate.now().plusDays(3)) // 예: 3일 뒤 종료
                .user(dummyUser) // 빌더에 user 정보 직접 추가
                .build();

        // 2. Vote 저장
        Vote savedVote = voteRepository.save(vote);

        // 3. Option들 생성
        List<Option> options = input.getOptions().stream().map(content ->
                Option.builder()
                        .vote(savedVote)
                        .content(content)
                        .build()
        ).toList();
        // 4. Option들 저장
        List<Option> savedOptions = optionRepository.saveAll(options);

        // 5. 정적 팩토리 메서드 사용하여 VotePayload 반환
        return VotePayload.fromEntity(savedVote, savedOptions);
    }

    @Transactional(readOnly = true)
    public List<VotePayload> getAllVotes() {
        return getAllVotes(DUMMY_USER_ID);
    }

    @Transactional(readOnly = true)
    public List<VotePayload> getAllVotes(UUID userId) {
        List<Vote> votes = voteRepository.findAll();
        List<VotePayload> votePayloads = new ArrayList<>();

        for (Vote vote : votes) {
            List<Option> options = optionRepository.findByVoteId(vote.getId());
            Long totalResponses = voteResponseRepository.countByVoteId(vote.getId());
            List<OptionWithStatsPayload> optionStats = new ArrayList<>();
            for (Option option : options) {
                Long optionCount = voteResponseRepository.countByOptionId(option.getId());
                float percentage = totalResponses > 0 ? (float) optionCount * 100 / totalResponses : 0;

                optionStats.add(new OptionWithStatsPayload(
                    option.getId(),
                    option.getContent(),
                    optionCount.intValue(),
                    percentage
                ));
            }

            // 사용자가 이 투표에 참여했는지 확인
            boolean hasVoted = voteResponseService.hasUserVoted(userId, vote.getId());

            votePayloads.add(VotePayload.fromEntityWithStats(
                vote,
                options,
                optionStats,
                totalResponses.intValue(),
                hasVoted
            ));
        }

        return votePayloads;
    }

    @Transactional(readOnly = true)
    public VoteDetailPayload getVoteById(UUID voteId) {
        return getVoteById(voteId, DUMMY_USER_ID);
    }

    @Transactional(readOnly = true)
    public VoteDetailPayload getVoteById(UUID voteId, UUID userId) {
        Vote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new ResourceNotFoundException("투표를 찾을 수 없습니다: " + voteId));

        List<Option> options = optionRepository.findByVoteId(voteId);
        Long totalResponses = voteResponseRepository.countByVoteId(voteId);

        List<OptionWithStatsPayload> optionStats = new ArrayList<>();
        for (Option option : options) {
            Long optionCount = voteResponseRepository.countByOptionId(option.getId());
            float percentage = totalResponses > 0 ? (float) optionCount * 100 / totalResponses : 0;

            optionStats.add(new OptionWithStatsPayload(
                option.getId(),
                option.getContent(),
                optionCount.intValue(),
                percentage
            ));
        }

        String creatorName = null;
        if (vote.getUser() != null) {
            User creator = userRepository.findById(vote.getUser().getId())
                    .orElse(null);
            if (creator != null) {
                creatorName = creator.getUsername();
            }
        }

        // 사용자가 이 투표에 참여했는지 확인
        boolean hasVoted = voteResponseService.hasUserVoted(userId, voteId);

        return VoteDetailPayload.builder()
                .id(vote.getId())
                .title(vote.getQuestion())
                .category(vote.getCategory())
                .status(vote.getStatus().name())
                .createdBy(creatorName)
                .finishedAt(vote.getFinishedAt().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .totalResponses(totalResponses.intValue())
                .options(optionStats)
                .hasVoted(hasVoted)
                .build();
    }

    // 새로 추가하는 메서드: 응답 수가 가장 많은 OPEN 상태 투표 3개 조회
    @Transactional(readOnly = true)
    public List<VotePayload> getMostPopularOpenVote() {
        List<Vote> openVotes = voteRepository.findByStatus(Vote.Status.OPEN);
        if (openVotes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Vote, Long> voteResponseCounts = new HashMap<>();
        for (Vote vote : openVotes) {
            Long responseCount = voteResponseRepository.countByVoteId(vote.getId());
            voteResponseCounts.put(vote, responseCount);
        }

        // 응답 수가 많은 순으로 정렬
        List<Map.Entry<Vote, Long>> sortedVotes = voteResponseCounts.entrySet().stream()
            .sorted(Map.Entry.<Vote, Long>comparingByValue().reversed())
            .limit(3) // 상위 3개만 선택
            .collect(Collectors.toList());

        List<VotePayload> result = new ArrayList<>();
        for (Map.Entry<Vote, Long> entry : sortedVotes) {
            Vote vote = entry.getKey();
            Long totalResponses = entry.getValue();
            List<Option> options = optionRepository.findByVoteId(vote.getId());

            List<OptionWithStatsPayload> optionStats = new ArrayList<>();
            for (Option option : options) {
                Long optionCount = voteResponseRepository.countByOptionId(option.getId());
                float percentage = totalResponses > 0 ? (float) optionCount * 100 / totalResponses : 0;

                optionStats.add(new OptionWithStatsPayload(
                    option.getId(),
                    option.getContent(),
                    optionCount.intValue(),
                    percentage
                ));
            }

            // 요청에 따라 투표하지 않은 것으로 표시
            result.add(VotePayload.fromEntityWithStats(vote, options, optionStats, totalResponses.intValue(), false));
        }

        return result;
    }

    // 새로 추가하는 메서드: 응답 수가 가장 적은 OPEN 상태 투표 조회
    @Transactional(readOnly = true)
    public VotePayload getLeastPopularOpenVote() {
        List<Vote> openVotes = voteRepository.findByStatus(Vote.Status.OPEN);
        if (openVotes.isEmpty()) {
            return null;
        }

        Map<Vote, Long> voteResponseCounts = new HashMap<>();
        for (Vote vote : openVotes) {
            Long responseCount = voteResponseRepository.countByVoteId(vote.getId());
            voteResponseCounts.put(vote, responseCount);
        }

        // 응답 수가 가장 적은 투표 찾기
        Map.Entry<Vote, Long> leastPopular = Collections.min(
            voteResponseCounts.entrySet(),
            Map.Entry.comparingByValue()
        );

        Vote vote = leastPopular.getKey();
        Long totalResponses = leastPopular.getValue();
        List<Option> options = optionRepository.findByVoteId(vote.getId());

        List<OptionWithStatsPayload> optionStats = new ArrayList<>();
        for (Option option : options) {
            Long optionCount = voteResponseRepository.countByOptionId(option.getId());
            float percentage = totalResponses > 0 ? (float) optionCount * 100 / totalResponses : 0;

            optionStats.add(new OptionWithStatsPayload(
                option.getId(),
                option.getContent(),
                optionCount.intValue(),
                percentage
            ));
        }

        // 요청에 따라 투표하지 않은 것으로 표시
        return VotePayload.fromEntityWithStats(vote, options, optionStats, totalResponses.intValue(), false);
    }
    
    /**
     * 특정 사용자가 생성한 투표 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자가 생성한 투표 목록
     */
    @Transactional(readOnly = true)
    public List<VotePayload> getVotesByUserId(UUID userId) {
        // 사용자가 생성한 투표 목록 조회
        List<Vote> votes = voteRepository.findByUserId(userId);
        List<VotePayload> votePayloads = new ArrayList<>();

        for (Vote vote : votes) {
            List<Option> options = optionRepository.findByVoteId(vote.getId());
            Long totalResponses = voteResponseRepository.countByVoteId(vote.getId());
            List<OptionWithStatsPayload> optionStats = new ArrayList<>();
            for (Option option : options) {
                Long optionCount = voteResponseRepository.countByOptionId(option.getId());
                float percentage = totalResponses > 0 ? (float) optionCount * 100 / totalResponses : 0;

                optionStats.add(new OptionWithStatsPayload(
                    option.getId(),
                    option.getContent(),
                    optionCount.intValue(),
                    percentage
                ));
            }

            // 사용자가 이 투표에 참여했는지 확인
            boolean hasVoted = voteResponseService.hasUserVoted(userId, vote.getId());

            votePayloads.add(VotePayload.fromEntityWithStats(
                vote,
                options,
                optionStats,
                totalResponses.intValue(),
                hasVoted
            ));
        }

        return votePayloads;
    }

    /**
     * 현재 사용자가 생성한 투표 목록을 조회합니다.
     * 더미 사용자 ID를 사용합니다.
     *
     * @return 현재 사용자가 생성한 투표 목록
     */
    @Transactional(readOnly = true)
    public List<VotePayload> getMyVotes() {
        return getVotesByUserId(DUMMY_USER_ID);
    }
}