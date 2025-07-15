package pluto.upik.domain.report.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pluto.upik.domain.guide.data.model.Guide;
import pluto.upik.domain.guide.repository.GuideRepository;
import pluto.upik.domain.option.repository.OptionRepository;
import pluto.upik.domain.report.data.DTO.*;
import pluto.upik.domain.report.data.model.Report;
import pluto.upik.domain.report.repository.ReportRepository;
import pluto.upik.domain.vote.data.model.Vote;
import pluto.upik.domain.vote.repository.VoteRepository;
import pluto.upik.domain.voteResponse.repository.VoteResponseRepository;
import pluto.upik.shared.exception.BusinessException;
import pluto.upik.shared.exception.ResourceNotFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 신고 관련 비즈니스 로직을 처리하는 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService implements ReportServiceInterface {

    private final ReportRepository reportRepository;
    private final GuideRepository guideRepository;
    private final VoteRepository voteRepository;
    private final OptionRepository optionRepository;
    private final VoteResponseRepository voteResponseRepository; // 추가: VoteResponse 레포지토리 주입

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteReport(UUID userId, UUID targetId) {
        log.info("신고 삭제 요청 시작 - userId: {}, targetId: {}", userId, targetId);

        try {
            // 존재 여부 확인
            if (!reportRepository.existsByUserIdAndTargetId(userId, targetId)) {
                log.warn("신고 삭제 실패 - 신고가 존재하지 않음 (userId: {}, targetId: {})", userId, targetId);
                throw new ResourceNotFoundException("해당 신고가 존재하지 않습니다.");
            }

            // 삭제
            reportRepository.deleteByUserIdAndTargetId(userId, targetId);
            log.info("신고 삭제 성공 - 신고 데이터 삭제 완료 (userId: {}, targetId: {})", userId, targetId);

            // 가이드의 revote 카운트 감소
            try {
                guideRepository.decrementRevoteCount(targetId);
                log.info("가이드 revote 카운트 감소 완료 - targetId: {}", targetId);
            } catch (Exception e) {
                log.error("가이드 revote 카운트 감소 중 오류 - targetId: {}, error: {}", targetId, e.getMessage(), e);
                throw new BusinessException("가이드 revote 카운트 감소 중 오류가 발생했습니다.");
            }
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("신고 삭제 중 알 수 없는 오류 - userId: {}, targetId: {}, error: {}", userId, targetId, e.getMessage(), e);
            throw new BusinessException("신고 삭제 중 오류가 발생했습니다.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ReportResponse> getReportsByUser(UUID userId) {
        log.info("사용자 신고 목록 조회 요청 시작 - userId: {}", userId);

        try {
            List<Report> reportList = reportRepository.findByUserId(userId);

            if (reportList == null || reportList.isEmpty()) {
                log.warn("사용자 신고 목록 조회 - 신고 내역 없음 (userId: {})", userId);
                return new ArrayList<>();
            }

            List<ReportResponse> reports = reportList.stream()
                    .map(this::mapToReportResponse)
                    .collect(Collectors.toList());


            log.info("사용자 신고 목록 조회 완료 - userId: {}, 결과 개수: {}", userId, reports.size());
            return reports;
        } catch (Exception e) {
            log.error("사용자 신고 목록 조회 중 오류 - userId: {}, error: {}", userId, e.getMessage(), e);
            throw new BusinessException("사용자 신고 목록 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ReportResponse> getReportsByTarget(UUID targetId) {
        log.info("신고 대상 목록 조회 요청 시작 - targetId: {}", targetId);

        try {
            List<Report> reportList = reportRepository.findByTargetId(targetId);

            if (reportList == null || reportList.isEmpty()) {
                log.warn("신고 대상 목록 조회 - 신고 내역 없음 (targetId: {})", targetId);
                return new ArrayList<>();
            }

            List<ReportResponse> reports = reportList.stream()
                    .map(this::mapToReportResponse)
                    .collect(Collectors.toList());

            log.info("신고 대상 목록 조회 완료 - targetId: {}, 결과 개수: {}", targetId, reports.size());
            return reports;
        } catch (Exception e) {
            log.error("신고 대상 목록 조회 중 오류 - targetId: {}, error: {}", targetId, e.getMessage(), e);
            throw new BusinessException("신고 대상 목록 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ReportResponse> getAllReports() {
        log.info("모든 신고 목록 조회 요청 시작");

        try {
            List<Report> reportList = reportRepository.findAll();

            if (reportList == null || reportList.isEmpty()) {
                log.warn("모든 신고 목록 조회 - 신고 내역 없음");
                return new ArrayList<>();
            }

            List<ReportResponse> reports = reportList.stream()
                    .map(this::mapToReportResponse)
                    .collect(Collectors.toList());

            log.info("모든 신고 목록 조회 완료 - 결과 개수: {}", reports.size());
            return reports;
        } catch (Exception e) {
            log.error("모든 신고 목록 조회 중 오류 - error: {}", e.getMessage(), e);
            throw new BusinessException("모든 신고 목록 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public AcceptGuideReportResponse acceptGuideReport(AcceptGuideReportRequest request) {
        log.info("가이드 신고 수락 처리 시작 - userId: {}, guideId: {}", request.getUserId(), request.getGuideId());

        try {
            // 1. 신고 존재 여부 확인
            UUID userId = request.getUserId();
            UUID guideId = request.getGuideId();

            if (!reportRepository.existsByUserIdAndTargetId(userId, guideId)) {
                log.warn("가이드 신고 수락 실패 - 신고가 존재하지 않음 (userId: {}, guideId: {})", userId, guideId);
                throw new ResourceNotFoundException("해당 신고가 존재하지 않습니다.");
            }

            // 2. 가이드 조회
            Guide guide = guideRepository.findById(guideId)
                    .orElseThrow(() -> {
                        log.warn("가이드 신고 수락 실패 - 가이드가 존재하지 않음 (guideId: {})", guideId);
                        return new ResourceNotFoundException("해당 가이드가 존재하지 않습니다.");
                    });

            // 3. 새로운 질문 생성 (Vote)
            Vote vote = guide.getVote();
            if (vote == null) {
                log.warn("가이드 신고 수락 실패 - 가이드에 연결된 질문이 없음 (guideId: {})", guideId);
                throw new BusinessException("가이드에 연결된 질문이 없습니다.");
            }

            // 4. 질문 상태를 OPEN으로 설정하고 종료일을 2일 후로 설정
            vote.setStatus(Vote.Status.OPEN);
            LocalDate twoLaterDate = LocalDate.now().plusDays(2);
            vote.setFinishedAt(twoLaterDate);
            voteRepository.save(vote);

            // 5. 신고 삭제
            reportRepository.deleteByUserIdAndTargetId(userId, guideId);

            log.info("가이드 신고 수락 처리 완료 - userId: {}, guideId: {}, 새 질문 ID: {}",
                    userId, guideId, vote.getId());

            return AcceptGuideReportResponse.builder()
                    .message("가이드 신고가 수락되었습니다. 질문이 다시 열렸습니다.")
                    .newQuestionId(vote.getId())
                    .success(true)
                    .build();

        } catch (ResourceNotFoundException e) {
            log.error("가이드 신고 수락 실패 - 리소스 없음: {}", e.getMessage());
            throw e;
        } catch (BusinessException e) {
            log.error("가이드 신고 수락 실패 - 비즈니스 오류: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("가이드 신고 수락 중 알 수 없는 오류 - userId: {}, guideId: {}, error: {}",
                    request.getUserId(), request.getGuideId(), e.getMessage(), e);
            throw new BusinessException("가이드 신고 수락 중 오류가 발생했습니다.");
        }
    }
    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public QuestionReportResponse reportQuestion(QuestionReportRequest request) {
        log.info("질문 신고 처리 시작 - userId: {}, questionId: {}", request.getUserId(), request.getQuestionId());

        try {
            // 1. 질문 존재 여부 확인
            UUID questionId = request.getQuestionId();
            Vote vote = voteRepository.findById(questionId)
                    .orElseThrow(() -> {
                        log.warn("질문 신고 실패 - 질문이 존재하지 않음 (questionId: {})", questionId);
                        return new ResourceNotFoundException("해당 질문이 존재하지 않습니다.");
                    });

            // 2. 이미 신고한 경우 체크
            UUID userId = request.getUserId();
            if (reportRepository.existsByUserIdAndTargetId(userId, questionId)) {
                log.warn("질문 신고 실패 - 이미 신고한 질문 (userId: {}, questionId: {})", userId, questionId);
                throw new BusinessException("이미 신고한 질문입니다.");
            }

            // 3. 신고 생성
            Report report = Report.builder()
                    .userId(userId)
                    .targetId(questionId)
                    .reason(request.getReason())
                    .createdAt(LocalDate.now())
                    .build();

            reportRepository.save(report);

            log.info("질문 신고 처리 완료 - userId: {}, questionId: {}", userId, questionId);

            return QuestionReportResponse.builder()
                    .message("질문 신고가 접수되었습니다.")
                    .success(true)
                    .build();

        } catch (ResourceNotFoundException e) {
            log.error("질문 신고 실패 - 리소스 없음: {}", e.getMessage());
            throw e;
        } catch (BusinessException e) {
            log.error("질문 신고 실패 - 비즈니스 오류: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("질문 신고 중 알 수 없는 오류 - userId: {}, questionId: {}, error: {}",
                    request.getUserId(), request.getQuestionId(), e.getMessage(), e);
            throw new BusinessException("질문 신고 중 오류가 발생했습니다.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public QuestionReportResponse rejectQuestionReport(RejectQuestionReportRequest request) {
        log.info("질문 신고 거부 처리 시작 - userId: {}, questionId: {}", request.getUserId(), request.getQuestionId());

        try {
            UUID userId = request.getUserId();
            UUID questionId = request.getQuestionId();

            // 1. 신고 존재 여부 확인
            if (!reportRepository.existsByUserIdAndTargetId(userId, questionId)) {
                log.warn("질문 신고 거부 실패 - 신고가 존재하지 않음 (userId: {}, questionId: {})", userId, questionId);
                throw new ResourceNotFoundException("해당 신고가 존재하지 않습니다.");
            }

            // 2. 신고 삭제
            reportRepository.deleteByUserIdAndTargetId(userId, questionId);

            log.info("질문 신고 거부 처리 완료 - userId: {}, questionId: {}", userId, questionId);

            return QuestionReportResponse.builder()
                    .message("질문 신고가 거부되었습니다.")
                    .success(true)
                    .build();

        } catch (ResourceNotFoundException e) {
            log.error("질문 신고 거부 실패 - 리소스 없음: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("질문 신고 거부 중 알 수 없는 오류 - userId: {}, questionId: {}, error: {}",
                    request.getUserId(), request.getQuestionId(), e.getMessage(), e);
            throw new BusinessException("질문 신고 거부 중 오류가 발생했습니다.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public QuestionReportResponse acceptQuestionReport(AcceptQuestionReportRequest request) {
        log.info("질문 신고 수락 처리 시작 - userId: {}, questionId: {}", request.getUserId(), request.getQuestionId());

        try {
            UUID userId = request.getUserId();
            UUID questionId = request.getQuestionId();

            // 1. 신고 존재 여부 확인
            if (!reportRepository.existsByUserIdAndTargetId(userId, questionId)) {
                log.warn("질문 신고 수락 실패 - 신고가 존재하지 않음 (userId: {}, questionId: {})", userId, questionId);
                throw new ResourceNotFoundException("해당 신고가 존재하지 않습니다.");
            }

            // 2. 질문 존재 여부 확인
            Vote vote = voteRepository.findById(questionId)
                    .orElseThrow(() -> {
                        log.warn("질문 신고 수락 실패 - 질문이 존재하지 않음 (questionId: {})", questionId);
                        return new ResourceNotFoundException("해당 질문이 존재하지 않습니다.");
                    });

            try {
                // 3. 먼저 vote_response 삭제 (외래키 제약조건 위반 방지)
                log.info("질문 관련 응답 삭제 시작 - questionId: {}", questionId);
                // option_id를 참조하는 vote_response 먼저 삭제
                voteResponseRepository.deleteByVoteId(questionId);
                log.info("질문 관련 응답 삭제 완료 - questionId: {}", questionId);
        } catch (Exception e) {
                throw new BusinessException("질문 관련 옵션 삭제 중 오류가 발생했습니다: " + e.getMessage());
        }

            try {
                // 4. 질문 삭제
                voteRepository.delete(vote);
                log.info("질문 삭제 완료 - questionId: {}", questionId);
            } catch (Exception e) {
                log.error("질문 삭제 중 오류 - questionId: {}, error: {}", questionId, e.getMessage(), e);
                throw new BusinessException("질문 삭제 중 오류가 발생했습니다: " + e.getMessage());
    }

            try {
                // 5. 관련 신고 모두 삭제 (해당 질문에 대한 모든 신고)
                reportRepository.deleteByTargetId(questionId);
                log.info("질문 관련 신고 모두 삭제 완료 - questionId: {}", questionId);
            } catch (Exception e) {
                log.error("질문 관련 신고 삭제 중 오류 - questionId: {}, error: {}", questionId, e.getMessage(), e);
                throw new BusinessException("질문 관련 신고 삭제 중 오류가 발생했습니다: " + e.getMessage());
            }

            log.info("질문 신고 수락 처리 완료 - userId: {}, questionId: {}", userId, questionId);

            return QuestionReportResponse.builder()
                    .message("질문 신고가 수락되었습니다. 질문이 삭제되었습니다.")
                    .success(true)
                    .build();

        } catch (ResourceNotFoundException e) {
            log.error("질문 신고 수락 실패 - 리소스 없음: {}", e.getMessage());
            throw e;
        } catch (BusinessException e) {
            log.error("질문 신고 수락 실패 - 비즈니스 오류: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("질문 신고 수락 중 알 수 없는 오류 - userId: {}, questionId: {}, error: {}",
                    request.getUserId(), request.getQuestionId(), e.getMessage(), e);
            throw new BusinessException("질문 신고 수락 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * Report 엔티티를 ReportResponse DTO로 변환합니다.
     * 이 과정에서 targetType 필드도 설정합니다.
     *
     * @param report 변환할 Report 엔티티
     * @return 변환된 ReportResponse 객체
     */
    private ReportResponse mapToReportResponse(Report report) {
        log.debug("Report 엔티티를 ReportResponse로 변환 중 - reportId: {}", report.getUserId());

        // targetType 결정: 가이드에 존재하면 "guide", 아니면 "vote"
        String targetType;
        String targetTitle = null;
        String authorId = null;
        String authorName = null;
        String authorProfileImage = null; // 프로필 이미지 필드 (User 클래스에 없음)
        String category = null;
        String guideType = null;
        Long likeCount = null;
        Long revoteCount = null;
        LocalDate targetCreatedAt = null;
        String content = null;
        String status = null;

        if(guideRepository.existsById(report.getTargetId())) {
            targetType = "guide";
            // 가이드 정보 가져오기
            Guide guide = guideRepository.findById(report.getTargetId()).orElse(null);
            if(guide != null) {
                targetTitle = guide.getTitle();
                category = guide.getCategory();
                guideType = guide.getGuideType();
                likeCount = guide.getLike();
                revoteCount = guide.getRevoteCount();
                targetCreatedAt = guide.getCreatedAt();
                content = guide.getContent();

                // 가이드는 투표와 연결되어 있으므로 투표 작성자를 가져옴
                if(guide.getVote() != null && guide.getVote().getUser() != null) {
                    authorId = guide.getVote().getUser().getId().toString();
                    authorName = guide.getVote().getUser().getName();
                    // User 클래스에 getProfileImage 메서드가 없으므로 제거
                    // authorProfileImage = guide.getVote().getUser().getProfileImage();
                }
            }
        } else {
            targetType = "vote";
            // 투표 정보 가져오기
            Vote vote = voteRepository.findById(report.getTargetId()).orElse(null);
            if(vote != null) {
                targetTitle = vote.getQuestion();
                category = vote.getCategory();
                // Vote 클래스에 getCreatedAt 메서드가 없으므로 제거
                // targetCreatedAt = vote.getCreatedAt();
                status = vote.getStatus().name();

                if(vote.getUser() != null) {
                    authorId = vote.getUser().getId().toString();
                    authorName = vote.getUser().getName();
                    // User 클래스에 getProfileImage 메서드가 없으므로 제거
                    // authorProfileImage = vote.getUser().getProfileImage();
                }

                // 투표 옵션들도 가져오기
                List<String> options = optionRepository.findByVoteId(report.getTargetId())
                    .stream()
                    .map(option -> option.getContent())
                    .collect(Collectors.toList());

                content = String.join(", ", options);
            }
        }

        ReportResponse response = ReportResponse.builder()
                .userId(report.getUserId())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .targetType(targetType)
                .createdAt(report.getCreatedAt())
                .targetTitle(targetTitle)
                .authorId(authorId)
                .authorName(authorName)
                .authorProfileImage(authorProfileImage) // null이 될 수 있음
                .category(category)
                .guideType(guideType)
                .likeCount(likeCount)
                .revoteCount(revoteCount)
                .targetCreatedAt(targetCreatedAt) // Vote의 경우 null이 될 수 있음
                .content(content)
                .status(status)
                .build();
        log.debug("Report 엔티티 변환 완료 - reportId: {}, targetType: {}, guideType: {}",
                report.getUserId(), targetType, guideType);
        return response;
    }
}