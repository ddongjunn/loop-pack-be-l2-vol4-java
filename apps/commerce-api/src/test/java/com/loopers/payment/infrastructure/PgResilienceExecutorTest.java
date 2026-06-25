package com.loopers.payment.infrastructure;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

class PgResilienceExecutorTest {

    private final RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(10))
            .retryExceptions(FeignException.InternalServerError.class)
            .build());
    private final PgResilienceExecutor executor = new PgResilienceExecutor(
            CircuitBreakerRegistry.ofDefaults(), retryRegistry, RateLimiterRegistry.ofDefaults());

    @Test
    @DisplayName("5xx 로 실패하면 설정(max-attempts=3)대로 재시도해서 성공한다")
    void given5xxThenSuccess_whenCall_thenRetriesAndSucceeds() {
        FeignException.InternalServerError serverError = mock(FeignException.InternalServerError.class);
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> flaky = () -> {
            if (attempts.incrementAndGet() < 3) {
                throw serverError;
            }
            return "ok";
        };

        String result = executor.call("toss", flaky);

        assertAll(
                () -> assertThat(result).isEqualTo("ok"),
                () -> assertThat(attempts.get()).isEqualTo(3)
        );
    }

    @Test
    @DisplayName("retry 대상이 아닌 예외(4xx)는 재시도하지 않고 즉시 전파한다")
    void givenNonRetryable4xx_whenCall_thenNoRetry() {
        FeignException.BadRequest badRequest = mock(FeignException.BadRequest.class);
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> failing = () -> {
            attempts.incrementAndGet();
            throw badRequest;
        };

        try {
            executor.call("toss", failing);
        } catch (FeignException ignored) {
            // 전파 기대
        }

        assertThat(attempts.get()).isEqualTo(1);
    }
}
