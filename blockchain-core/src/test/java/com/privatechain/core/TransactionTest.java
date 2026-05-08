package com.privatechain.core;

import com.privatechain.core.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Transaction} covering construction, signing,
 * serialization, equality, and validation guards.
 */
@DisplayName("Transaction")
class TransactionTest {

    // ─── Concrete subclass for testing ────────────────────────────────────────

    private TestTransaction sampleTx() {
        return new TestTransaction(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "sender-address-1",
            "receiver-address-1",
            new BigDecimal("10.50"),
            Instant.parse("2025-06-01T00:00:00Z"),
            null);
    }

    @Test
    @DisplayName("null id throws NullPointerException")
    void nullIdThrows() {
        assertThrows(NullPointerException.class, () ->
            new TestTransaction(null, "s", "r", BigDecimal.ONE, Instant.now(), null));
    }

    // ─── Construction validation ──────────────────────────────────────────────

    @Test
    @DisplayName("null senderAddress throws NullPointerException")
    void nullSenderThrows() {
        assertThrows(NullPointerException.class, () ->
            new TestTransaction(UUID.randomUUID(), null, "r", BigDecimal.ONE, Instant.now(), null));
    }

    @Test
    @DisplayName("blank senderAddress throws IllegalArgumentException")
    void blankSenderThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new TestTransaction(UUID.randomUUID(), "  ", "r", BigDecimal.ONE, Instant.now(), null));
    }

    @Test
    @DisplayName("null receiverAddress throws NullPointerException")
    void nullReceiverThrows() {
        assertThrows(NullPointerException.class, () ->
            new TestTransaction(UUID.randomUUID(), "s", null, BigDecimal.ONE, Instant.now(), null));
    }

    @Test
    @DisplayName("blank receiverAddress throws IllegalArgumentException")
    void blankReceiverThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new TestTransaction(UUID.randomUUID(), "s", "  ", BigDecimal.ONE, Instant.now(), null));
    }

    @Test
    @DisplayName("negative amount throws IllegalArgumentException")
    void negativeAmountThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new TestTransaction(UUID.randomUUID(), "s", "r",
                new BigDecimal("-1"), Instant.now(), null));
    }

    @Test
    @DisplayName("null amount throws NullPointerException")
    void nullAmountThrows() {
        assertThrows(NullPointerException.class, () ->
            new TestTransaction(UUID.randomUUID(), "s", "r", null, Instant.now(), null));
    }

    @Test
    @DisplayName("null timestamp throws NullPointerException")
    void nullTimestampThrows() {
        assertThrows(NullPointerException.class, () ->
            new TestTransaction(UUID.randomUUID(), "s", "r", BigDecimal.ONE, null, null));
    }

    @Test
    @DisplayName("zero amount is valid")
    void zeroAmountIsValid() {
        TestTransaction tx = new TestTransaction(
            UUID.randomUUID(), "s", "r", BigDecimal.ZERO, Instant.now(), null);
        assertEquals(BigDecimal.ZERO, tx.getAmount());
    }

    @Test
    @DisplayName("getters return values set in constructor")
    void gettersReturnCorrectValues() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Instant ts = Instant.parse("2025-01-01T00:00:00Z");
        TestTransaction tx = new TestTransaction(
            id, "alice", "bob", new BigDecimal("5.00"), ts, null);

        assertEquals(id, tx.getId());
        assertEquals("alice", tx.getSenderAddress());
        assertEquals("bob", tx.getReceiverAddress());
        assertEquals(new BigDecimal("5.00"), tx.getAmount());
        assertEquals(ts, tx.getTimestamp());
        assertTrue(tx.getMetadata().isEmpty());
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("metadata map is stored and returned unmodifiable")
    void metadataStoredAndUnmodifiable() {
        Map<String, Object> meta = Map.of("key1", "value1", "fee", 100);
        TestTransaction tx = new TestTransaction(
            UUID.randomUUID(), "s", "r", BigDecimal.ONE, Instant.now(), meta);

        assertEquals("value1", tx.getMetadata().get("key1"));
        assertThrows(UnsupportedOperationException.class,
            () -> tx.getMetadata().put("newKey", "x"),
            "metadata must be unmodifiable");
    }

    @Test
    @DisplayName("null metadata is treated as empty map")
    void nullMetadataBecomesEmptyMap() {
        TestTransaction tx = new TestTransaction(
            UUID.randomUUID(), "s", "r", BigDecimal.ONE, Instant.now(), null);
        assertNotNull(tx.getMetadata());
        assertTrue(tx.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("unsigned transaction has null signature and isSigned() == false")
    void unsignedTransactionHasNoSignature() {
        TestTransaction tx = sampleTx();
        assertNull(tx.getSignature());
        assertFalse(tx.isSigned());
    }

    // ─── Signing ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sign() attaches signature bytes and isSigned() returns true")
    void signAttachesSignature() {
        TestTransaction tx = sampleTx();
        byte[] sigBytes = new byte[]{1, 2, 3, 4, 5};
        tx.sign(sigBytes);

        assertTrue(tx.isSigned());
        assertNotNull(tx.getSignature());
    }

    @Test
    @DisplayName("getSignature() returns a defensive copy — mutation does not affect stored value")
    void signatureIsDefensivelyCopied() {
        TestTransaction tx = sampleTx();
        byte[] sigBytes = new byte[]{10, 20, 30};
        tx.sign(sigBytes);

        // Mutate the returned copy
        byte[] returned = tx.getSignature();
        returned[0] = 99;

        // Original stored value must be unchanged
        assertArrayEquals(new byte[]{10, 20, 30}, tx.getSignature(),
            "signature must be a defensive copy — external mutation must not affect stored bytes");
    }

    @Test
    @DisplayName("sign() with null throws NullPointerException")
    void signNullThrows() {
        assertThrows(NullPointerException.class, () -> sampleTx().sign(null));
    }

    @Test
    @DisplayName("sign() with empty byte array throws IllegalArgumentException")
    void signEmptyBytesThrows() {
        assertThrows(IllegalArgumentException.class, () -> sampleTx().sign(new byte[0]));
    }

    @Test
    @DisplayName("toSignableBytes() returns non-empty deterministic bytes")
    void signableBytesAreDeterministic() {
        TestTransaction tx1 = sampleTx();
        TestTransaction tx2 = sampleTx();

        assertNotNull(tx1.toSignableBytes());
        assertTrue(tx1.toSignableBytes().length > 0);
        assertArrayEquals(tx1.toSignableBytes(), tx2.toSignableBytes(),
            "same fields must produce identical signable bytes");
    }

    // ─── toSignableBytes ──────────────────────────────────────────────────────

    @Test
    @DisplayName("different sender produces different signable bytes")
    void differentSenderProducesDifferentBytes() {
        TestTransaction tx1 = new TestTransaction(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "alice", "bob", BigDecimal.ONE,
            Instant.parse("2025-01-01T00:00:00Z"), null);
        TestTransaction tx2 = new TestTransaction(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "charlie", "bob", BigDecimal.ONE,
            Instant.parse("2025-01-01T00:00:00Z"), null);

        assertFalse(Arrays.equals(tx1.toSignableBytes(), tx2.toSignableBytes()));
    }

    @Test
    @DisplayName("two transactions with same UUID are equal")
    void equalityById() {
        UUID id = UUID.randomUUID();
        TestTransaction tx1 = new TestTransaction(id, "a", "b", BigDecimal.ONE, Instant.now(), null);
        TestTransaction tx2 = new TestTransaction(id, "c", "d", BigDecimal.TEN, Instant.now(), null);

        assertEquals(tx1, tx2, "transactions with same UUID must be equal");
        assertEquals(tx1.hashCode(), tx2.hashCode());
    }

    // ─── Equality and hash ────────────────────────────────────────────────────

    @Test
    @DisplayName("two transactions with different UUIDs are not equal")
    void inequalityByDifferentId() {
        TestTransaction tx1 = sampleTx();
        TestTransaction tx2 = new TestTransaction(
            UUID.randomUUID(), "sender-address-1", "receiver-address-1",
            new BigDecimal("10.50"), Instant.parse("2025-06-01T00:00:00Z"), null);

        assertNotEquals(tx1, tx2);
    }

    @Test
    @DisplayName("equals returns false for null and non-Transaction objects")
    void equalsHandlesNullAndOtherTypes() {
        TestTransaction tx = sampleTx();
        assertNotEquals(tx, null);
        assertNotEquals(tx, "not a transaction");
    }

    @Test
    @DisplayName("equals is reflexive — a transaction equals itself")
    void equalsReflexive() {
        TestTransaction tx = sampleTx();
        assertEquals(tx, tx);
    }

    @Test
    @DisplayName("toString contains class name, sender, receiver, and signed status")
    void toStringIsInformative() {
        TestTransaction tx = sampleTx();
        String str = tx.toString();

        assertTrue(str.contains("sender-address-1"), "toString must include sender");
        assertTrue(str.contains("receiver-address-1"), "toString must include receiver");
        assertTrue(str.contains("false"), "toString must include signed=false for unsigned tx");
        assertFalse(str.toLowerCase().contains("private"),
            "toString must NOT expose private key information");
    }

    // ─── toString ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString shows signed=true after signing")
    void toStringShowsSignedStatus() {
        TestTransaction tx = sampleTx();
        tx.sign(new byte[]{1, 2, 3});
        assertTrue(tx.toString().contains("true"), "toString must show signed=true");
    }

    /**
     * Minimal concrete subclass used exclusively in these tests.
     */
    static final class TestTransaction extends Transaction {
        TestTransaction(UUID id, String sender, String receiver,
                        BigDecimal amount, Instant timestamp,
                        java.util.Map<String, Object> metadata) {
            super(id, sender, receiver, amount, timestamp, metadata);
        }
    }
}
