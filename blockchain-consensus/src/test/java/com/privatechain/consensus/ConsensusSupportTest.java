package com.privatechain.consensus;

import com.privatechain.core.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConsensusSupport} helper methods.
 */
@DisplayName("ConsensusSupport")
class ConsensusSupportTest {

    @Test
    @DisplayName("utility constructor is inaccessible and throws when invoked reflectively")
    void utilityConstructorThrowsWhenInvokedReflectively() throws Exception {
        Constructor<ConsensusSupport> constructor = ConsensusSupport.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, constructor::newInstance,
            "Utility constructor should throw when called reflectively");
        assertInstanceOf(UnsupportedOperationException.class, thrown.getCause(),
            "Utility constructor should reject instantiation");
    }

    @Test
    @DisplayName("copyAndValidate sorts values and returns an immutable list when requested")
    void copyAndValidateSortsAndReturnsImmutableList() {
        List<String> copied = ConsensusSupport.copyAndValidate(List.of("node-c", "node-a", "node-b"), "nodes", true);

        assertEquals(List.of("node-a", "node-b", "node-c"), copied,
            "Values should be sorted lexicographically when sort=true");
        assertThrows(UnsupportedOperationException.class, () -> copied.add("node-d"),
            "Returned list should be immutable");
    }

    @Test
    @DisplayName("copyAndValidate rejects blank entries")
    void copyAndValidateRejectsBlankEntries() {
        assertThrows(IllegalArgumentException.class,
            () -> ConsensusSupport.copyAndValidate(List.of("node-a", "   "), "nodes", false),
            "Blank entries should be rejected to avoid invalid peer or validator identifiers");
    }

    @Test
    @DisplayName("merkleRoot rejects null transaction entries")
    void merkleRootRejectsNullTransactionEntries() {
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(null);

        assertThrows(NullPointerException.class, () -> ConsensusSupport.merkleRoot(transactions),
            "Null transactions should fail fast to preserve deterministic Merkle roots");
    }

    @Test
    @DisplayName("hasLeadingZeroBits handles edge conditions and bit-precision checks")
    void hasLeadingZeroBitsHandlesEdgeConditions() {
        assertTrue(ConsensusSupport.hasLeadingZeroBits("abcd", 0),
            "Non-positive difficulty should always pass");
        assertFalse(ConsensusSupport.hasLeadingZeroBits("00", 24),
            "Difficulty larger than the hash length should fail");
        assertFalse(ConsensusSupport.hasLeadingZeroBits("0100", 8),
            "A non-zero byte in the required full-zero-byte range should fail");
        assertTrue(ConsensusSupport.hasLeadingZeroBits("00ff", 8),
            "Exact byte-aligned zero-bit constraints should pass when the prefix is zero");
        assertFalse(ConsensusSupport.hasLeadingZeroBits("10ff", 4),
            "High bits in the partial byte should fail the mask check");
        assertTrue(ConsensusSupport.hasLeadingZeroBits("0fff", 4),
            "A zero high nibble should satisfy a 4-bit leading-zero requirement");
    }
}

