package de.jexcellence.hibernate.repository;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OptimisticLockHandler")
class OptimisticLockHandlerTest {

    @Nested
    @DisplayName("executeWithRetry")
    class ExecuteWithRetry {

        @Test
        @DisplayName("should return result on successful execution")
        void shouldReturnResultOnSuccess() throws Exception {
            var result = OptimisticLockHandler.executeWithRetry(
                () -> "success",
                "TestEntity"
            );

            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("should retry on OptimisticLockException")
        void shouldRetryOnOptimisticLockException() throws Exception {
            var attempts = new AtomicInteger(0);

            var result = OptimisticLockHandler.executeWithRetry(
                () -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new OptimisticLockException("Concurrent modification");
                    }
                    return "success after retry";
                },
                "TestEntity"
            );

            assertThat(result).isEqualTo("success after retry");
            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("should throw after max retries exceeded")
        void shouldThrowAfterMaxRetriesExceeded() {
            var attempts = new AtomicInteger(0);

            assertThatThrownBy(() -> OptimisticLockHandler.executeWithRetry(
                () -> {
                    attempts.incrementAndGet();
                    throw new OptimisticLockException("Always fails");
                },
                "TestEntity"
            ))
                .isInstanceOf(OptimisticLockException.class);

            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("should not retry on non-optimistic lock exceptions")
        void shouldNotRetryOnNonOptimisticLockExceptions() {
            var attempts = new AtomicInteger(0);

            assertThatThrownBy(() -> OptimisticLockHandler.executeWithRetry(
                () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("Not an optimistic lock");
                },
                "TestEntity"
            ))
                .isInstanceOf(IllegalStateException.class);

            assertThat(attempts.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank entity name")
        void shouldThrowForBlankEntityName() {
            assertThatThrownBy(() -> OptimisticLockHandler.executeWithRetry(
                () -> "result",
                "   "
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entityName must not be blank");
        }

        @Test
        @DisplayName("should handle InterruptedException properly")
        void shouldHandleInterruptedException() {
            var currentThread = Thread.currentThread();

            assertThatThrownBy(() -> OptimisticLockHandler.executeWithRetry(
                () -> {
                    currentThread.interrupt();
                    throw new OptimisticLockException("Trigger retry");
                },
                "TestEntity"
            ))
                .isInstanceOf(InterruptedException.class);

            assertThat(Thread.interrupted()).isTrue();
        }
    }
}
