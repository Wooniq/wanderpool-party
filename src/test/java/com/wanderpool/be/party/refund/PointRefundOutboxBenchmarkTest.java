package com.wanderpool.be.party.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.wanderpool.be.party.client.MemberClient;
import com.wanderpool.be.party.client.PointRefundCommand;
import com.wanderpool.be.party.client.RetryableMemberClientException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Outbox 릴레이(PointRefundOutboxProcessor)의 동시성·처리량 벤치마크.
 *
 * <p>Party 서비스가 여러 Pod로 떠 있으면 {@code @Scheduled processReadyRefunds()}가 Pod마다
 * 독립적으로 돈다 — 즉 여러 Pod의 스케줄러가 동시에 같은 PENDING 항목들을 두고 경쟁하는
 * 상황이 실제로 발생할 수 있다. 이 클래스는 그 경쟁 상황에서 {@code claimReady()}의 원자적
 * 선점(claim)이 실제로 중복 처리를 막아주는지, 그리고 그 상태에서 처리량이 얼마나 나오는지를
 * {@link com.wanderpool.be.party.concurrency.PartyApprovalLockBenchmarkTest}와 같은 스타일
 * (Testcontainers 실측 + 라운드 반복 + 콘솔 리포트)로 검증한다.
 *
 * <p>실행 방법 (PostgreSQL Testcontainers 사용, Docker 필요):
 * <pre>
 * ./gradlew test --tests '*PointRefundOutboxBenchmarkTest*'
 * </pre>
 *
 * <p>{@code processor}는 실제 {@code @Scheduled} 빈이지만, 각 테스트는 이 클래스가 관리하는
 * 스레드에서 {@code processReadyRefunds()}를 직접(수동으로) 반복 호출해 폴링 주기를 흉내낸다.
 * 앱 전역에 켜져 있는 실제 스케줄러가 백그라운드에서 끼어들어 측정을 오염시키지 않도록,
 * {@code party.refund-outbox.poll-delay-ms}를 테스트 동안 사실상 발동하지 않을 만큼 크게
 * 오버라이드해 둔다.
 */
@Testcontainers
@SpringBootTest(properties = "grpc.server.port=0")
class PointRefundOutboxBenchmarkTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // 동시 폴러(최대 8개) × 배치당 여러 커넥션을 동시에 점유할 수 있어야 한다.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "60000");
        // 실제 @Scheduled 트리거가 테스트 도중 끼어들어 측정을 오염시키지 않도록 사실상 무한대로 늦춘다.
        // 이 테스트는 processReadyRefunds()를 직접 호출해서 폴링 주기를 스스로 재현한다.
        registry.add("party.refund-outbox.poll-delay-ms", () -> "999999999");
    }

    @Autowired
    private PointRefundOutboxProcessor processor;

    @Autowired
    private PointRefundOutboxRepository outboxRepository;

    /** 이 벤치마크는 refundPoints() 호출만 관찰하면 되므로, 다른 메서드는 목으로 대체한다. */
    @MockBean
    private MemberClient memberClient;

    @BeforeEach
    void resetProcessorDefaults() {
        // PointRefundOutboxProcessorTest와 동일한 방식으로, 실행 중인 빈의 @Value 필드를 직접
        // 조작한다 — 테스트 메서드 간 설정이 새어나가지 않도록 매 테스트 시작 시 기본값으로 되돌린다.
        ReflectionTestUtils.setField(processor, "maxAttempts", 5);
        ReflectionTestUtils.setField(processor, "retryDelaySeconds", 30L);
        ReflectionTestUtils.setField(processor, "processingTimeoutSeconds", 120L);
    }

    // ════════════════════════════════════════
    //  시나리오 1 — 동시 폴러(멀티 Pod 시뮬레이션) 하의 중복 처리 방지
    // ════════════════════════════════════════

    record DedupeRoundResult(long wallClockNanos, Map<Integer, Integer> claimsPerPoller, int duplicateCount) {}

    @ParameterizedTest(name = "폴러 {0}개 동시 폴링")
    @ValueSource(ints = {2, 4, 8})
    @DisplayName("동시 폴러(멀티 Pod 시뮬레이션) 하의 중복 처리 방지 — PENDING 200건")
    void concurrentPollers_noDuplicateProcessing(int pollerCount) throws Exception {
        int warmup = 1;
        int rounds = 3;
        int itemsPerRound = 200;
        List<DedupeRoundResult> measured = new ArrayList<>();

        for (int r = 0; r < warmup + rounds; r++) {
            DedupeRoundResult result = runDedupeRound(pollerCount, itemsPerRound, r);
            if (r >= warmup) {
                measured.add(result);
            }
        }

        printDedupeReport(pollerCount, itemsPerRound, measured);

        long totalDuplicates = measured.stream().mapToLong(DedupeRoundResult::duplicateCount).sum();
        assertThat(totalDuplicates)
                .as("한 요청이 두 번 이상 처리되면 안 된다 — claimReady()의 원자적 선점이 깨진 것")
                .isZero();
    }

    private DedupeRoundResult runDedupeRound(int pollerCount, int itemsPerRound, int roundIndex) throws Exception {
        List<Long> ids = createPendingOutboxItems(itemsPerRound, "dedupe-p" + pollerCount + "-r" + roundIndex);

        // requestId별 호출 횟수(중복 처리 감지용)와, 폴러 슬롯 번호별 claim 성공 건수(분포 관찰용)를 기록한다.
        ConcurrentHashMap<String, AtomicInteger> callsPerRequest = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer, AtomicInteger> claimsPerPoller = new ConcurrentHashMap<>();
        ThreadLocal<Integer> pollerIndexHolder = new ThreadLocal<>();

        doAnswer(invocation -> {
            PointRefundCommand command = invocation.getArgument(0);
            callsPerRequest.computeIfAbsent(command.requestId(), k -> new AtomicInteger()).incrementAndGet();
            Integer pollerIndex = pollerIndexHolder.get();
            if (pollerIndex != null) {
                claimsPerPoller.computeIfAbsent(pollerIndex, k -> new AtomicInteger()).incrementAndGet();
            }
            return null;
        }).when(memberClient).refundPoints(any());

        AtomicBoolean done = new AtomicBoolean(false);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(pollerCount);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int p = 0; p < pollerCount; p++) {
                int pollerIndex = p;
                futures.add(pool.submit(() -> {
                    pollerIndexHolder.set(pollerIndex);
                    start.await();
                    // 각 스레드가 별도 Pod의 폴링 사이클을 도는 것처럼, 전량 처리될 때까지 반복 호출한다.
                    while (!done.get()) {
                        processor.processReadyRefunds();
                        if (callsPerRequest.size() >= itemsPerRound) {
                            done.set(true);
                        }
                    }
                    return null;
                }));
            }

            long t0 = System.nanoTime();
            start.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
            long wallClockNanos = System.nanoTime() - t0;

            // 정합성 검증: DB 기준으로 전량 SUCCEEDED, 그리고 요청당 정확히 1회씩만 호출됐는지.
            List<PointRefundOutbox> finalItems = outboxRepository.findAllById(ids);
            assertThat(finalItems).hasSize(itemsPerRound);
            assertThat(finalItems).allMatch(o -> o.getStatus() == PointRefundOutboxStatus.SUCCEEDED);
            assertThat(callsPerRequest).hasSize(itemsPerRound);

            int duplicateCount = (int) callsPerRequest.values().stream()
                    .filter(counter -> counter.get() != 1)
                    .count();

            Map<Integer, Integer> claimsSnapshot = new TreeMap<>();
            claimsPerPoller.forEach((k, v) -> claimsSnapshot.put(k, v.get()));

            return new DedupeRoundResult(wallClockNanos, claimsSnapshot, duplicateCount);
        } finally {
            pool.shutdown();
        }
    }

    private void printDedupeReport(int pollerCount, int itemsPerRound, List<DedupeRoundResult> results) {
        double avgWallClockMs = results.stream()
                .mapToLong(DedupeRoundResult::wallClockNanos)
                .average().orElse(0) / 1_000_000.0;
        double avgThroughput = itemsPerRound / (avgWallClockMs / 1000.0);
        long totalDuplicates = results.stream().mapToLong(DedupeRoundResult::duplicateCount).sum();

        System.out.println("========================================================");
        System.out.printf("[Outbox 중복 처리 방지] 폴러 수: %d개 | 항목: %d건 | 라운드: %d회%n",
                pollerCount, itemsPerRound, results.size());
        System.out.println("========================================================");
        System.out.printf("라운드 wall-clock : 평균 %.3fms%n", avgWallClockMs);
        System.out.printf("추정 처리량       : 평균 %.1f건/s%n", avgThroughput);
        System.out.printf("중복 처리 건수    : %d건 (0이어야 정상)%n", totalDuplicates);
        System.out.println("폴러별 claim 성공 건수 분포 (라운드별):");
        for (int r = 0; r < results.size(); r++) {
            DedupeRoundResult result = results.get(r);
            String distribution = result.claimsPerPoller().entrySet().stream()
                    .map(e -> "poller-" + e.getKey() + "=" + e.getValue())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(claim 없음)");
            System.out.printf("  - 라운드 %d (%.3fms) : %s%n", r + 1, result.wallClockNanos() / 1_000_000.0, distribution);
        }
        System.out.println();
    }

    // ════════════════════════════════════════
    //  시나리오 2 — 재시도 → 데드레터 전환의 시간적 동작
    // ════════════════════════════════════════

    @Test
    @DisplayName("재시도 → 데드레터 전환의 시간적 동작 (max-attempts=3, retry-delay=1s)")
    void retryThenDeadLetter_timingBehavior() throws Exception {
        int maxAttempts = 3;
        ReflectionTestUtils.setField(processor, "maxAttempts", maxAttempts);
        ReflectionTestUtils.setField(processor, "retryDelaySeconds", 1L);

        // recoveringItem: 2번 실패(=1,2회차) 후 3회차(=max-attempts)에 성공 → SUCCEEDED로 수렴해야 한다.
        // exhaustedItem : 매번 실패 → attemptCount가 max-attempts에 도달하는 순간 FAILED_MANUAL_REVIEW.
        PointRefundOutbox recoveringItem = createPendingOutbox("retry-timing-recovering-" + System.nanoTime());
        PointRefundOutbox exhaustedItem = createPendingOutbox("retry-timing-exhausted-" + System.nanoTime());

        Map<String, Integer> failUntilAttempt = Map.of(
                recoveringItem.getRequestId(), 2,
                exhaustedItem.getRequestId(), Integer.MAX_VALUE
        );
        ConcurrentHashMap<String, AtomicInteger> attemptsSoFar = new ConcurrentHashMap<>();

        doAnswer(invocation -> {
            PointRefundCommand command = invocation.getArgument(0);
            int attempt = attemptsSoFar.computeIfAbsent(command.requestId(), k -> new AtomicInteger()).incrementAndGet();
            if (attempt <= failUntilAttempt.get(command.requestId())) {
                throw new RetryableMemberClientException("simulated transient member-service failure", null);
            }
            return null;
        }).when(memberClient).refundPoints(any());

        long t0 = System.nanoTime();
        long deadlineNanos = t0 + Duration.ofSeconds(20).toNanos();
        Long succeededAtNanos = null;
        Long deadLetteredAtNanos = null;
        int pollCycles = 0;

        while (System.nanoTime() < deadlineNanos && (succeededAtNanos == null || deadLetteredAtNanos == null)) {
            processor.processReadyRefunds();
            pollCycles++;

            if (succeededAtNanos == null) {
                PointRefundOutboxStatus status = outboxRepository.findById(recoveringItem.getId()).orElseThrow().getStatus();
                if (status == PointRefundOutboxStatus.SUCCEEDED) {
                    succeededAtNanos = System.nanoTime();
                }
            }
            if (deadLetteredAtNanos == null) {
                PointRefundOutboxStatus status = outboxRepository.findById(exhaustedItem.getId()).orElseThrow().getStatus();
                if (status == PointRefundOutboxStatus.FAILED_MANUAL_REVIEW) {
                    deadLetteredAtNanos = System.nanoTime();
                }
            }
            if (succeededAtNanos == null || deadLetteredAtNanos == null) {
                Thread.sleep(200); // 폴링 주기를 흉내낸다 — retry-delay-seconds(1s)가 실제로 흘러야 한다.
            }
        }

        assertThat(succeededAtNanos).as("재시도 끝에 SUCCEEDED로 수렴해야 한다").isNotNull();
        assertThat(deadLetteredAtNanos).as("max-attempts 초과 시 FAILED_MANUAL_REVIEW로 전환돼야 한다").isNotNull();

        PointRefundOutbox finalRecovering = outboxRepository.findById(recoveringItem.getId()).orElseThrow();
        PointRefundOutbox finalExhausted = outboxRepository.findById(exhaustedItem.getId()).orElseThrow();
        assertThat(finalRecovering.getAttemptCount()).isEqualTo(maxAttempts);
        assertThat(finalExhausted.getAttemptCount()).isEqualTo(maxAttempts);

        System.out.println("========================================================");
        System.out.println("[Outbox 재시도 → 데드레터 전환] max-attempts=3 | retry-delay=1s");
        System.out.println("========================================================");
        System.out.printf("폴링 사이클 수                  : %d회%n", pollCycles);
        System.out.printf("recovering 항목 SUCCEEDED까지    : %.3fms (attemptCount=%d)%n",
                (succeededAtNanos - t0) / 1_000_000.0, finalRecovering.getAttemptCount());
        System.out.printf("exhausted 항목 FAILED_MANUAL_REVIEW까지 : %.3fms (attemptCount=%d)%n",
                (deadLetteredAtNanos - t0) / 1_000_000.0, finalExhausted.getAttemptCount());
        System.out.println();
    }

    // ════════════════════════════════════════
    //  시나리오 3 — 처리량(throughput) 측정
    // ════════════════════════════════════════

    @Test
    @DisplayName("처리량 측정 — PENDING 1000건을 배치 20건 제약 하 단일 폴러로 완전히 소진")
    void throughput_singlePollerDrainsBacklog() throws Exception {
        int totalItems = 1000;
        List<Long> ids = createPendingOutboxItems(totalItems, "throughput-" + System.nanoTime());

        AtomicInteger succeededCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            succeededCount.incrementAndGet();
            return null;
        }).when(memberClient).refundPoints(any());

        long t0 = System.nanoTime();
        long deadlineNanos = t0 + Duration.ofSeconds(60).toNanos();
        int pollCycles = 0;
        // poll-delay-ms(운영 설정)는 순수 대기 시간이므로 여기서는 재현하지 않는다 — 이 측정은
        // 배치 크기(20건/회)와 처리 로직 자체가 만드는 처리량 상한을 보기 위한 것이다.
        while (succeededCount.get() < totalItems) {
            processor.processReadyRefunds();
            pollCycles++;
            if (System.nanoTime() > deadlineNanos) {
                throw new AssertionError("처리량 측정이 60초 안에 끝나지 않았습니다 — 남은 항목: "
                        + (totalItems - succeededCount.get()));
            }
        }
        long wallClockNanos = System.nanoTime() - t0;

        List<PointRefundOutbox> finalItems = outboxRepository.findAllById(ids);
        assertThat(finalItems).hasSize(totalItems);
        assertThat(finalItems).allMatch(o -> o.getStatus() == PointRefundOutboxStatus.SUCCEEDED);

        double wallClockSeconds = wallClockNanos / 1_000_000_000.0;
        double throughputPerSecond = totalItems / wallClockSeconds;

        System.out.println("========================================================");
        System.out.println("[Outbox 처리량] 단일 폴러 | 배치 20건/회 | PENDING 1000건");
        System.out.println("========================================================");
        System.out.printf("총 처리 wall-clock : %.3fs%n", wallClockSeconds);
        System.out.printf("폴링 사이클 수      : %d회 (평균 %.1f건/사이클)%n",
                pollCycles, totalItems / (double) pollCycles);
        System.out.printf("초당 처리량         : %.1f건/s%n", throughputPerSecond);
        System.out.println();
    }

    // ════════════════════════════════════════
    //  공통 헬퍼
    // ════════════════════════════════════════

    private List<Long> createPendingOutboxItems(int count, String requestIdPrefix) {
        List<PointRefundOutbox> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            PointRefundCommand command = new PointRefundCommand(
                    1000L + i, 100, 1L, (long) (i + 1),
                    requestIdPrefix + "-" + i
            );
            items.add(PointRefundOutbox.from(command));
        }
        return outboxRepository.saveAll(items).stream()
                .map(PointRefundOutbox::getId)
                .toList();
    }

    private PointRefundOutbox createPendingOutbox(String requestId) {
        PointRefundCommand command = new PointRefundCommand(9999L, 100, 1L, 1L, requestId);
        return outboxRepository.save(PointRefundOutbox.from(command));
    }
}
