package com.wanderpool.be.party.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanderpool.be.domain.ParticipantStatus;
import com.wanderpool.be.domain.Party;
import com.wanderpool.be.domain.PartyParticipant;
import com.wanderpool.be.domain.PartyStatus;
import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.common.apiResponse.code.PartyErrorCode;
import com.wanderpool.be.party.common.apiResponse.exception.PartyException;
import com.wanderpool.be.party.repository.PartyParticipantRepository;
import com.wanderpool.be.party.repository.PartyRepository;
import com.wanderpool.be.party.service.PartyService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 참여 승낙(acceptJoinRequest)의 낙관적/비관적 락 전략을 경합 강도별로 비교하는 벤치마크.
 *
 * <p>핵심은 모든 요청의 결과를 SUCCESS/CONFLICT/CAPACITY_FULL/ERROR 4가지로 분류해서 집계하는 것이다.
 * 전체 평균만 보면 "실패가 많아서 평균이 낮아진 것"과 "실제로 빠른 것"을 구분할 수 없다.
 *
 * <p>실행 방법 (PostgreSQL Testcontainers 사용, Docker 필요):
 * <pre>
 * PARTY_APPROVAL_LOCK_STRATEGY=OPTIMISTIC  ./gradlew test --tests '*PartyApprovalLockBenchmarkTest*'
 * PARTY_APPROVAL_LOCK_STRATEGY=PESSIMISTIC ./gradlew test --tests '*PartyApprovalLockBenchmarkTest*'
 * </pre>
 *
 * <p>{@code party.approval.lock-strategy}는 빈 생성 시점에 한 번 주입되므로, 전략을 바꾸려면
 * 반드시 JVM(테스트 프로세스)을 재시작해야 한다 — 같은 실행 안에서 두 전략을 번갈아 테스트할 수 없다.
 *
 * <p><b>OPTIMISTIC 재시도(PartyApprovalAttempt)에 대한 캐비어트:</b> 이 벤치마크는 정원 1명
 * 파티에 최대 32명이 동시에 경쟁하는 "완전 경쟁" 구조라, 재시도해도 이길 수 있는 사람은 처음부터
 * 1명뿐이다. 즉 이 시나리오에서는 재시도가 패자들의 지연(그리고 CONFLICT 평균 소요시간)만 늘리고
 * 성공/실패 결과 자체는 바꾸지 못한다. 재시도가 실제로 결과를 바꾸려면, 재시도 도중 다른 참여자의
 * 취소(cancelParticipation)로 자리가 다시 열리는 시나리오가 필요하다 — 이 테스트는 그런 시나리오를
 * 다루지 않는다.
 */
@Testcontainers
@SpringBootTest(properties = "grpc.server.port=0")
class PartyApprovalLockBenchmarkTest {

    private static final Long DRIVER_ID = 1L;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void hikariProperties(DynamicPropertyRegistry registry) {
        // 동시 요청 수(최대 32건)만큼 커넥션을 동시에 점유할 수 있어야 한다.
        // 비관적 락에서는 대기 중인 트랜잭션도 커넥션을 물고 있으므로 풀이 작으면
        // 락 경합이 아니라 커넥션 고갈로 실패하는, 측정을 왜곡하는 상황이 생긴다.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "50");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "60000");
    }

    @Autowired
    private PartyService partyService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PartyParticipantRepository participantRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${party.approval.lock-strategy:OPTIMISTIC}")
    private String lockStrategy;

    /** 이 벤치마크는 memberClient를 호출하지 않는다 — 컨텍스트 기동을 위해서만 목으로 대체한다. */
    @MockBean
    private MemberClient memberClient;

    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
    }

    /** 요청 1건의 처리 결과 */
    record Outcome(Result result, long elapsedNanos) {}

    /** 라운드(=한 번의 동시 승낙 시도) 1회의 결과 묶음 */
    record RoundResult(List<Outcome> outcomes, long wallClockNanos) {}

    enum Result {
        SUCCESS,       // 승인 성공
        CONFLICT,      // 낙관적 락 버전 충돌 — 재시도하면 성공할 수 있는 실패
        CAPACITY_FULL, // 정원 초과/이미 마감으로 인한 정상 거절 — 재시도해도 무의미한 실패
        ERROR          // 그 외 예외 — 이게 0이어야 측정이 유효하다
    }

    @ParameterizedTest(name = "동시 요청 {0}건")
    @ValueSource(ints = {2, 8, 32})
    @DisplayName("경합 강도별 락 전략 비교 — 성공/실패 분리 집계")
    void benchmark(int concurrency) throws Exception {
        int warmup = 3;
        int rounds = 15;
        List<RoundResult> measured = new ArrayList<>();

        for (int r = 0; r < warmup + rounds; r++) {
            RoundResult roundResult = runRound(concurrency);
            if (r >= warmup) {
                measured.add(roundResult);
            }
        }

        printReport(concurrency, measured);

        long errorCount = measured.stream()
                .flatMap(round -> round.outcomes().stream())
                .filter(outcome -> outcome.result() == Result.ERROR)
                .count();
        assertThat(errorCount)
                .as("ERROR 건수는 0이어야 측정 자체가 유효하다")
                .isZero();
    }

    private RoundResult runRound(int concurrency) throws Exception {
        Long partyId = create정원1명파티();
        List<Long> participantIds = create대기중참여요청(partyId, concurrency);

        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);

        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Long participantId : participantIds) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    long t0 = System.nanoTime();
                    try {
                        // 각 스레드가 독립 트랜잭션·커넥션으로 실제 DB 경합을 재현한다.
                        txTemplate.execute(status ->
                                partyService.acceptJoinRequest(partyId, participantId, DRIVER_ID));
                        return new Outcome(Result.SUCCESS, System.nanoTime() - t0);
                    } catch (OptimisticLockingFailureException e) {
                        return new Outcome(Result.CONFLICT, System.nanoTime() - t0);
                    } catch (PartyException e) {
                        // CAPACITY_EXCEEDED: incrementPassengers() 내부에서 정원 초과를 감지한 경우.
                        // PARTY_NOT_RECRUITING: 비관적 락 하에서 락 획득 후 재조회했더니 이미
                        //   다른 스레드가 커밋을 마쳐 CLOSED로 전환된 경우 — 정원1명 파티에서는
                        //   이쪽이 훨씬 흔한 "정상 거절" 경로다. 둘 다 재시도가 무의미한 거절이므로
                        //   같은 버킷(CAPACITY_FULL)으로 묶는다.
                        boolean normalRejection = e.getErrorCode() == PartyErrorCode.CAPACITY_EXCEEDED
                                || e.getErrorCode() == PartyErrorCode.PARTY_NOT_RECRUITING;
                        return new Outcome(
                                normalRejection ? Result.CAPACITY_FULL : Result.ERROR,
                                System.nanoTime() - t0);
                    } catch (Exception e) {
                        return new Outcome(Result.ERROR, System.nanoTime() - t0);
                    }
                }));
            }

            ready.await();
            long roundStart = System.nanoTime();
            start.countDown(); // 일제 시작

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> f : futures) {
                outcomes.add(f.get());
            }
            long roundWallClockNanos = System.nanoTime() - roundStart;

            // 정합성 검증 — 라운드마다
            Party party = partyRepository.findById(partyId).orElseThrow();
            assertThat(party.getCurrentPassengers()).isEqualTo(1);
            assertThat(party.getStatus()).isEqualTo(PartyStatus.CLOSED);
            assertThat(count승인된참여자(partyId)).isEqualTo(1);

            return new RoundResult(outcomes, roundWallClockNanos);
        } finally {
            pool.shutdown();
        }
    }

    private Long create정원1명파티() {
        return txTemplate.execute(status -> {
            Party party = Party.create(
                    DRIVER_ID, "락 전략 벤치마크", "동시성 측정용 파티",
                    "출발지", 37.0, 127.0,
                    "목적지", 37.1, 127.1,
                    LocalDateTime.now().plusHours(1),
                    LocalDateTime.now().plusHours(2),
                    1
            );
            return partyRepository.save(party).getId();
        });
    }

    private List<Long> create대기중참여요청(Long partyId, int concurrency) {
        return txTemplate.execute(status -> {
            Party party = partyRepository.findById(partyId).orElseThrow();
            List<PartyParticipant> participants = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                long memberId = 10_000L + i;
                participants.add(PartyParticipant.create(
                        party, memberId,
                        "픽업" + i, 37.0 + i * 0.001, 127.0 + i * 0.001,
                        "하차" + i, null, null,
                        0, false
                ));
            }
            return participantRepository.saveAll(participants).stream()
                    .map(PartyParticipant::getId)
                    .toList();
        });
    }

    private long count승인된참여자(Long partyId) {
        return participantRepository.findAllByPartyIdAndStatusOrderByIdAsc(partyId, ParticipantStatus.ACCEPTED)
                .size();
    }

    // ════════════════════════════════════════
    //  리포트 출력
    // ════════════════════════════════════════

    private void printReport(int concurrency, List<RoundResult> results) {
        List<Outcome> all = results.stream()
                .flatMap(round -> round.outcomes().stream())
                .toList();

        Map<Result, List<Outcome>> byResult = all.stream()
                .collect(Collectors.groupingBy(Outcome::result));

        List<Outcome> successes = byResult.getOrDefault(Result.SUCCESS, List.of());
        List<Outcome> conflicts = byResult.getOrDefault(Result.CONFLICT, List.of());
        List<Outcome> capacityFull = byResult.getOrDefault(Result.CAPACITY_FULL, List.of());
        List<Outcome> errors = byResult.getOrDefault(Result.ERROR, List.of());

        List<Outcome> failures = new ArrayList<>(conflicts);
        failures.addAll(capacityFull);

        long total = all.size();
        double successRate = total == 0 ? 0.0 : (double) successes.size() / total * 100.0;
        double conflictShareOfFailures = failures.isEmpty()
                ? 0.0
                : (double) conflicts.size() / failures.size() * 100.0;
        double avgWallClockMs = results.stream()
                .mapToLong(RoundResult::wallClockNanos)
                .average().orElse(0) / 1_000_000.0;

        System.out.println("========================================================");
        System.out.printf("락 전략: %s | 동시 요청: %d건 | 라운드: %d회%n",
                lockStrategy, concurrency, results.size());
        System.out.println("========================================================");
        System.out.printf("총 요청           : %d건%n", total);
        System.out.printf("성공률            : %d / %d (%.1f%%)%n", successes.size(), total, successRate);
        System.out.printf("성공 평균 / p95   : %.3fms / %.3fms%n", avgMs(successes), p95Ms(successes));
        System.out.printf("실패 평균         : %.3fms (전체 실패 %d건)%n", avgMs(failures), failures.size());
        System.out.printf("  - CONFLICT      : %d건, 평균 %.3fms%n", conflicts.size(), avgMs(conflicts));
        System.out.printf("  - CAPACITY_FULL : %d건, 평균 %.3fms%n", capacityFull.size(), avgMs(capacityFull));
        System.out.printf("  - CONFLICT 비중 : 실패 중 %.1f%% (재시도로 구제 가능한 비율)%n", conflictShareOfFailures);
        System.out.printf("라운드 wall-clock : 평균 %.3fms%n", avgWallClockMs);
        System.out.printf("ERROR             : %d건 (0이 아니면 측정 무효)%n", errors.size());
        System.out.println();
    }

    private double avgMs(List<Outcome> outcomes) {
        return outcomes.stream()
                .mapToLong(Outcome::elapsedNanos)
                .average().orElse(0) / 1_000_000.0;
    }

    private double p95Ms(List<Outcome> outcomes) {
        if (outcomes.isEmpty()) {
            return 0.0;
        }
        List<Long> sorted = outcomes.stream()
                .map(Outcome::elapsedNanos)
                .sorted()
                .toList();
        int idx = (int) Math.ceil(0.95 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx) / 1_000_000.0;
    }
}
