package com.privatechain.core;

import com.privatechain.core.builder.BlockchainConfig;
import com.privatechain.core.builder.BlockchainNode;
import com.privatechain.core.model.Block;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance benchmarks for the blockchain core module (T-078).
 *
 * <p>Verifies two key performance targets from the NFR table:</p>
 * <ul>
 *   <li>{@link #chainValidationOf10000BlocksUnder2Seconds()} — validates a 10,000-block chain
 *       ({@code isChainValid()}) in under 2,000 ms (NFR-PERF-01 proxy).</li>
 *   <li>{@link #sha256ThroughputViaBlockHashComputation()} — computes 50,000 block hashes
 *       (each involving an SHA-256 call) in under 1,000 ms, verifying SHA-256 throughput
 *       ≥ 50,000 hashes/sec (NFR-PERF-04).</li>
 * </ul>
 *
 * <h2>Design choices</h2>
 * <p>All tests use {@link BlockchainConfig#builder()} with no-argument defaults, which
 * provides a {@code NoOpConsensusEngine} (accepts all blocks instantly, no hash search)
 * and an in-memory storage backend. This isolates the measurement to chain management
 * and hash computation, not I/O or consensus latency.</p>
 *
 * <p>Tests are tagged {@code @Tag("performance")} so they can be excluded from fast
 * local builds with {@code -Dgroups='!performance'} while still running in CI.</p>
 *
 * <h2>Note on timing</h2>
 * <p>These tests use wall-clock time via {@link System#nanoTime()} rather than a JMH
 * harness. This is appropriate for CI regression detection but not for micro-benchmark
 * publication. A single-pass warm-up (the setup loop) avoids JIT cold-start bias.</p>
 *
 * @since 1.0.0
 */
@Tag("performance")
@DisplayName("Performance benchmarks")
class PerformanceTest {

    // ─── Constants ────────────────────────────────────────────────────────────

    /**
     * Target number of blocks for the chain-validation benchmark.
     */
    private static final int CHAIN_BENCHMARK_SIZE = 10_000;

    /**
     * Maximum allowed milliseconds for isChainValid() on CHAIN_BENCHMARK_SIZE blocks.
     */
    private static final long MAX_VALIDATION_MS = 2_000L;

    /**
     * Number of block hashes to compute in the SHA-256 throughput test.
     */
    private static final int HASH_ITERATIONS = 50_000;

    /**
     * Maximum allowed milliseconds for HASH_ITERATIONS hashes.
     */
    private static final long MAX_HASH_MS = 1_000L;

    // ─── Test fixtures ────────────────────────────────────────────────────────

    /**
     * Blockchain node backed by the default no-op consensus and in-memory storage.
     * Re-created for each test to avoid state bleed.
     */
    private BlockchainNode node;

    @BeforeEach
    void setUp() {
        // BlockchainConfig.builder() with no arguments applies these defaults:
        //   consensusEngine = NoOpConsensusEngine (accepts all blocks instantly)
        //   storage         = InMemoryBlockchainStorage (LinkedHashMap, no I/O)
        //   chainId         = "private-blockchain"
        node = BlockchainConfig.builder().build();
        node.start(); // creates genesis block
    }

    @AfterEach
    void tearDown() {
        node.stop();
    }

    // ─── Benchmark 1 — isChainValid() on 10,000 blocks ───────────────────────

    /**
     * Verifies that {@link com.privatechain.core.builder.Blockchain#isChainValid()} on a
     * 10,000-block chain completes in under {@value #MAX_VALIDATION_MS} ms (T-078).
     *
     * <p><strong>Setup phase (untimed):</strong> mines 9,999 blocks on top of the genesis
     * block using the no-op engine. Block production is instant (no PoW hash search),
     * so setup completes in milliseconds. The timing measurement covers ONLY the
     * {@code isChainValid()} call.</p>
     *
     * <p><strong>What is measured:</strong> the hash recomputation and chain-linkage
     * check for every block pair in the chain. This exercises the critical path through
     * {@code Block.isHashValid()} (SHA-256) and {@code String.equals()} for each pair.</p>
     */
    @Test
    @DisplayName("isChainValid() on 10,000 blocks completes in under 2 seconds")
    void chainValidationOf10000BlocksUnder2Seconds() {

        // ── Pre-warm: build the chain (not timed) ────────────────────────────
        // Use the NoOpConsensusEngine (default) so mining is instant.
        // Each mineBlock() call creates a valid, hash-linked block with no difficulty search.
        for (int i = 1; i < CHAIN_BENCHMARK_SIZE; i++) {
            Block mined = node.getChain()
                .getConsensusEngine()
                .mineBlock(List.of(), node.getChain().getLatestBlock());
            node.getChain().addBlock(mined);
        }

        int actualHeight = node.getChain().size();
        assertTrue(actualHeight >= CHAIN_BENCHMARK_SIZE,
            "Setup did not produce the expected number of blocks. Got: " + actualHeight);

        // ── Timed measurement ─────────────────────────────────────────────────
        long start = System.nanoTime();
        boolean valid = node.getChain().isChainValid();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        // ── Assertions ────────────────────────────────────────────────────────
        assertTrue(valid, "isChainValid() must return true on an unmodified chain");

        System.out.printf("[PERF] isChainValid(%,d blocks): %d ms (limit: %d ms)%n",
            actualHeight, elapsedMs, MAX_VALIDATION_MS);

        assertTrue(elapsedMs < MAX_VALIDATION_MS,
            String.format("isChainValid() on %,d blocks took %d ms — exceeds %d ms limit. "
                    + "Review Block.isHashValid() or the storage loadAll() implementation.",
                actualHeight, elapsedMs, MAX_VALIDATION_MS));
    }

    // ─── Benchmark 2 — SHA-256 throughput via block hash computation ──────────

    /**
     * Verifies SHA-256 hash throughput by computing {@value #HASH_ITERATIONS} block hashes
     * in under {@value #MAX_HASH_MS} ms, corresponding to ≥ 50,000 hashes/second
     * on a single thread (NFR-PERF-04).
     *
     * <p>Each iteration calls {@code Block.computeHash()} which internally invokes
     * SHA-256 over the block fields. This exercises the same code path used by
     * {@code Block.isHashValid()} and {@code Blockchain.isChainValid()}, keeping
     * the benchmark grounded in the library's real workload.</p>
     *
     * <p>{@code blockchain-core} has zero external dependencies, so this test uses
     * only JDK SHA-256 (via the {@code Block} class) without importing
     * {@code blockchain-crypto}. This avoids introducing a circular test dependency.</p>
     */
    @Test
    @DisplayName("SHA-256 throughput: 50,000 block hashes in under 1 second")
    void sha256ThroughputViaBlockHashComputation() {

        // Pre-build one valid block whose fields we'll hash repeatedly.
        // We use addBlock+mineBlock once so the block is a well-formed chain block.
        Block previous = node.getChain().getLatestBlock(); // genesis
        Block sample = node.getChain()
            .getConsensusEngine()
            .mineBlock(List.of(), previous);

        // ── Timed measurement ─────────────────────────────────────────────────
        // isHashValid() calls Block.computeHash() which runs SHA-256 internally.
        // We exercise it on the sample block in a tight loop.
        long start = System.nanoTime();
        for (int i = 0; i < HASH_ITERATIONS; i++) {
            // isHashValid() recomputes the SHA-256 hash of the block and compares.
            // This is the canonical hot path for both mining and chain validation.
            boolean ok = sample.isHashValid();
            // Prevent the JIT from optimizing away the call by using the result
            if (!ok) {
                throw new AssertionError("Sample block hash must be valid");
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        // ── Assertions ────────────────────────────────────────────────────────
        long throughput = (long) HASH_ITERATIONS * 1_000L / Math.max(1L, elapsedMs);

        System.out.printf("[PERF] SHA-256 throughput: %,d hashes in %d ms (%,d hashes/sec)%n",
            HASH_ITERATIONS, elapsedMs, throughput);

        assertTrue(elapsedMs < MAX_HASH_MS,
            String.format("%,d SHA-256 hashes took %d ms — throughput %,d hashes/sec, "
                    + "below the 50,000/sec target. Review HashUtil or Block.computeHash().",
                HASH_ITERATIONS, elapsedMs, throughput));
    }
}
