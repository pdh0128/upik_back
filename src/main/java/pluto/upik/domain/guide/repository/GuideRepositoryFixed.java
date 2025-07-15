package pluto.upik.domain.guide.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pluto.upik.domain.guide.data.model.Guide;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 가이드 레포지토리
 * 가이드 엔티티에 대한 데이터베이스 접근을 제공합니다.
 */
@Repository
public interface GuideRepositoryFixed extends JpaRepository<Guide, UUID> {

    /**
     * 특정 투표에 대한 가이드를 조회합니다.
     *
     * @param voteId 투표 ID
     * @return 가이드 (존재하지 않을 경우 빈 Optional)
     */
    Optional<Guide> findByVoteId(UUID voteId);

    /**
     * 특정 투표에 대한 가이드가 존재하는지 확인합니다.
     *
     * @param voteId 투표 ID
     * @return 존재 여부
     */
    boolean existsByVoteId(UUID voteId);

    /**
     * 특정 카테고리에 속한 모든 가이드를 조회합니다.
     *
     * @param category 카테고리
     * @return 해당 카테고리의 가이드 목록
     */
    List<Guide> findAllByCategory(String category);

    /**
     * 제목에 특정 키워드가 포함된 가이드를 검색합니다.
     *
     * @param keyword 검색할 키워드
     * @return 키워드가 포함된 가이드 목록
     */
    @Query("SELECT g FROM Guide g WHERE g.title LIKE %:keyword%")
    List<Guide> findGuidesByTitleContaining(@Param("keyword") String keyword);

    /**
     * 특정 사용자가 생성한 가이드를 조회합니다.
     * 투표 테이블을 통해 사용자가 생성한 가이드를 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자가 생성한 가이드 목록
     */
    @Query("SELECT g FROM Guide g JOIN g.vote v WHERE v.user.id = :userId")
    List<Guide> findGuidesByUserId(@Param("userId") UUID userId);

    /**
     * 가이드의 좋아요 수를 1 증가시킵니다.
     *
     * @param guideId 가이드 ID
     * @return 영향받은 행 수
     */
    @Transactional
    @Modifying
    @Query("UPDATE Guide g SET g.like = g.like + 1 WHERE g.id = :guideId")
    int incrementLikeCount(@Param("guideId") UUID guideId);

    /**
     * 가이드의 재투표 수를 1 증가시킵니다.
     *
     * @param id 가이드 ID
     * @return 영향받은 행 수
     */
    @Transactional
    @Modifying
    @Query("UPDATE Guide g SET g.revoteCount = g.revoteCount + 1 WHERE g.id = :id")
    int incrementRevoteCount(@Param("id") UUID id);

    /**
     * 가이드의 좋아요 수를 1 감소시킵니다. (0 미만으로 내려가지 않도록 함)
     *
     * @param id 가이드 ID
     * @return 영향받은 행 수
     */
    @Modifying
    @Transactional
    @Query("update Guide g set g.like = g.like - 1 where g.id = :id and g.like > 0")
    int decrementLikeCount(@Param("id") UUID id);

    /**
     * 가이드의 재투표 수를 1 감소시킵니다. (0 미만으로 내려가지 않도록 함)
     *
     * @param id 가이드 ID
     * @return 영향받은 행 수
     */
    @Modifying
    @Transactional
    @Query("update Guide g set g.revoteCount = g.revoteCount - 1 where g.id = :id and g.revoteCount > 0")
    int decrementRevoteCount(@Param("id") UUID id);
}