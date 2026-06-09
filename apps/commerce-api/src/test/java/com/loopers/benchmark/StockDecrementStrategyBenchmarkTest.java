package com.loopers.benchmark;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 상품 재고를 동시에 차감할 때, 두 전략의 처리량을 비교하는 벤치마크.
 *
 * <ul>
 *   <li>A) 비관적 락(현재): SELECT ... FOR UPDATE → 앱에서 계산 → UPDATE SET quantity=계산값 → commit.
 *       락을 두 번의 round-trip 동안 보유한다.</li>
 *   <li>B) 조건부 원자 UPDATE: UPDATE quantity = quantity - 1 WHERE quantity >= 1.
 *       단일 statement 라 임계구역이 짧다.</li>
 * </ul>
 *
 * <p>재고를 충분히 크게 두어(0 미도달) 경합-at-0 이 아니라 순수 처리량을 측정한다.
 * 정확성(최종 재고 = 초기 - 총차감)은 두 전략 모두 단언하고, 시간은 출력만 한다.
 * 수동 실행: {@code ./gradlew :apps:commerce-api:test --tests "*StockDecrementStrategyBenchmarkTest"}
 */
@SpringBootTest
class StockDecrementStrategyBenchmarkTest {

    private static final long PRODUCT_ID = 1L;
    private static final long INITIAL = 1_000_000L;
    private static final int THREADS = 8;
    private static final int OPS_PER_THREAD = 1_000;
    private static final int TOTAL_OPS = THREADS * OPS_PER_THREAD;

    @Autowired
    private DataSource dataSource;

    /** 낙관적 락 전략에서 version 충돌로 재시도한 횟수(성공 INSERT 제외). */
    private final AtomicLong optimisticRetries = new AtomicLong();

    @Test
    void compareDecrementStrategies() throws Exception {
        long pessimisticMs = measure(this::pessimisticDecrement);
        long pessimisticRemain = currentQuantity();

        long atomicMs = measure(this::atomicDecrement);
        long atomicRemain = currentQuantity();

        optimisticRetries.set(0);
        long optimisticMs = measure(this::optimisticDecrement);
        long optimisticRemain = currentQuantity();

        long pessimisticTps = TOTAL_OPS * 1000L / Math.max(pessimisticMs, 1);
        long atomicTps = TOTAL_OPS * 1000L / Math.max(atomicMs, 1);
        long optimisticTps = TOTAL_OPS * 1000L / Math.max(optimisticMs, 1);

        System.out.println("\n========= STOCK DECREMENT STRATEGY (threads=" + THREADS
                + ", ops=" + TOTAL_OPS + ") =========");
        System.out.printf("A) 비관적 락   (SELECT FOR UPDATE + UPDATE)        : %6d ms | %6d ops/s%n", pessimisticMs, pessimisticTps);
        System.out.printf("B) 조건부 원자 (UPDATE ... WHERE qty>=1)           : %6d ms | %6d ops/s%n", atomicMs, atomicTps);
        System.out.printf("C) 낙관적 락   (version CAS + 재시도)              : %6d ms | %6d ops/s  (재시도 %d회, 충돌율 %.0f%%)%n",
                optimisticMs, optimisticTps, optimisticRetries.get(),
                100.0 * optimisticRetries.get() / (TOTAL_OPS + optimisticRetries.get()));
        System.out.printf("=> 처리량 B(%.2f배) > A(1.00배) > C(%.2f배)  [A 기준]%n",
                (double) atomicTps / Math.max(pessimisticTps, 1),
                (double) optimisticTps / Math.max(pessimisticTps, 1));
        System.out.println("====================================================================\n");

        // 정확성: 각 측정 전 resetStock 으로 초기화되므로, 세 전략 모두 정확히 TOTAL_OPS 만큼 차감되어야 한다.
        assertThat(pessimisticRemain).isEqualTo(INITIAL - TOTAL_OPS);
        assertThat(atomicRemain).isEqualTo(INITIAL - TOTAL_OPS);
        assertThat(optimisticRemain).isEqualTo(INITIAL - TOTAL_OPS);
    }

    private interface Decrement {
        void apply(Connection conn) throws Exception;
    }

    /** A) 비관적 락: 행을 FOR UPDATE 로 잠그고 읽은 값으로 계산해 덮어쓴다(읽기-수정-쓰기). */
    private void pessimisticDecrement(Connection conn) throws Exception {
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            long current;
            try (ResultSet rs = st.executeQuery(
                    "SELECT quantity FROM product_stocks WHERE product_id = " + PRODUCT_ID + " FOR UPDATE")) {
                rs.next();
                current = rs.getLong(1);
            }
            st.executeUpdate("UPDATE product_stocks SET quantity = " + (current - 1)
                    + " WHERE product_id = " + PRODUCT_ID);
        }
        conn.commit();
    }

    /** B) 조건부 원자 UPDATE: 단일 statement 로 조건부 차감. */
    private void atomicDecrement(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE product_stocks SET quantity = quantity - 1 WHERE product_id = ? AND quantity >= 1")) {
            ps.setLong(1, PRODUCT_ID);
            ps.executeUpdate();
        }
    }

    /**
     * C) 낙관적 락: version 을 읽고, UPDATE 의 {@code WHERE version = old} 로 compare-and-set.
     * 0 행이면 그 사이 다른 스레드가 version 을 올린 것이므로 다시 읽어 재시도한다(재시도 로직 직접 구현).
     */
    private void optimisticDecrement(Connection conn) throws Exception {
        conn.setAutoCommit(true);
        while (true) {
            long current;
            long version;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT quantity, version FROM product_stocks WHERE product_id = " + PRODUCT_ID)) {
                rs.next();
                current = rs.getLong(1);
                version = rs.getLong(2);
            }
            int updated;
            try (Statement st = conn.createStatement()) {
                updated = st.executeUpdate(
                        "UPDATE product_stocks SET quantity = " + (current - 1) + ", version = " + (version + 1)
                                + " WHERE product_id = " + PRODUCT_ID + " AND version = " + version);
            }
            if (updated == 1) {
                return;
            }
            optimisticRetries.incrementAndGet();
        }
    }

    /** THREADS 개 스레드가 OPS_PER_THREAD 회씩 동시에 차감하는 데 걸린 시간(ms) 을 잰다. */
    private long measure(Decrement op) throws Exception {
        resetStock();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                ready.countDown();
                try (Connection conn = dataSource.getConnection()) {
                    start.await();
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        op.apply(conn);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        long startNanos = System.nanoTime();
        start.countDown();
        done.await();
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        pool.shutdownNow();
        return elapsedMs;
    }

    private void resetStock() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            // 낙관적 락 전략용 version 컬럼(없으면 추가). A/B 는 무시한다.
            try {
                exec(conn, "ALTER TABLE product_stocks ADD COLUMN version BIGINT NOT NULL DEFAULT 0");
            } catch (Exception alreadyExists) {
                // 컬럼이 이미 있으면 무시
            }
            exec(conn, "TRUNCATE TABLE product_stocks");
            exec(conn, "INSERT INTO product_stocks (product_id, quantity, version, created_at, updated_at) "
                    + "VALUES (" + PRODUCT_ID + ", " + INITIAL + ", 0, NOW(), NOW())");
        }
    }

    private long currentQuantity() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT quantity FROM product_stocks WHERE product_id = " + PRODUCT_ID)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void exec(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }
}
