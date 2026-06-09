package com.privatechain.examples;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.privatechain.core.model.Transaction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A currency-aware money transfer transaction — the canonical example of a
 * consumer-defined {@link Transaction} subtype.
 *
 * <p>Demonstrates the following library extension points (design.md §7.2 / FR-TX-05):</p>
 * <ul>
 *   <li>Subclassing {@link Transaction} to add domain-specific fields ({@code currency},
 *       {@code reference}).</li>
 *   <li>Extending {@link #toSignableBytes()} to bind the {@code currency} field into
 *       the ECDSA signature so that the currency code cannot be altered post-signing.</li>
 *   <li>Using {@link JsonCreator} and {@link JsonProperty} so that Jackson can
 *       deserialize the subtype without any registration step — the {@code _type} field
 *       written by {@code @JsonTypeInfo} on {@code Transaction} carries the fully-qualified
 *       class name and is sufficient for reconstruction (FR-SER-01, AC-09).</li>
 * </ul>
 *
 * <h2>Full JSON round-trip (AC-09)</h2>
 * <p>A {@code MoneyTransferTransaction} placed inside a {@link com.privatechain.core.model.Block}
 * serialised via {@code BlockSerializer} and deserialize back will yield an object of
 * this type (not the abstract base) because the {@code _type} field is present in the
 * JSON payload. No registration step is required.</p>
 *
 * <h2>Signing</h2>
 * <p>Call {@code wallet.sign(tx)} after construction. The signature covers
 * {@code id|senderAddress|receiverAddress|amount|timestamp|currency} — the vertical-bar
 * delimiter is part of the canonical format inherited from the base class.</p>
 *
 * <h2>Thread safety</h2>
 * <p>Instances are effectively immutable after {@code sign()} has been called.
 * {@code sign()} itself is not thread-safe (inherits the base class behavior).</p>
 *
 * @see Transaction
 * @since 1.0.0
 */
public final class MoneyTransferTransaction extends Transaction {

    // ─── Domain-specific fields ────────────────────────────────────────────────

    /**
     * ISO-4217 currency code identifying the denomination of the transfer (e.g. "USD", "EUR").
     * Included in the ECDSA signature via {@link #toSignableBytes()} to prevent post-signing
     * substitution of the currency code.
     */
    private final String currency;

    /**
     * Optional free-text payment reference or memo (e.g. "invoice-001").
     * NOT included in the signature — this field is informational only.
     * May be {@code null}.
     */
    private final String reference;

    // ─── Constructor (Jackson @JsonCreator) ────────────────────────────────────

    /**
     * Constructs a {@code MoneyTransferTransaction}.
     *
     * <p>This constructor is annotated with {@link JsonCreator} so Jackson uses it
     * during deserialization. Every parameter is bound by its {@link JsonProperty}
     * name — the exact field names that were written to JSON by the base class and
     * this subclass respectively.</p>
     *
     * @param id              unique transaction identifier (non-null)
     * @param senderAddress   hex-encoded sender address (non-null, non-blank)
     * @param receiverAddress hex-encoded receiver address (non-null, non-blank)
     * @param amount          transfer amount (&ge; 0; non-null)
     * @param timestamp       UTC creation time (non-null)
     * @param metadata        arbitrary key-value metadata; may be {@code null}
     * @param currency        ISO-4217 currency code (non-null, non-blank)
     * @param reference       optional payment reference; may be {@code null}
     * @throws NullPointerException     if any non-optional argument is null
     * @throws IllegalArgumentException if {@code currency} is blank
     */
    @JsonCreator
    public MoneyTransferTransaction(
        @JsonProperty("id") UUID id,
        @JsonProperty("senderAddress") String senderAddress,
        @JsonProperty("receiverAddress") String receiverAddress,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("currency") String currency,
        @JsonProperty("reference") String reference) {

        super(id, senderAddress, receiverAddress, amount, timestamp, metadata);
        Objects.requireNonNull(currency, "currency must not be null");
        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        this.currency = currency;
        this.reference = reference; // nullable — informational field
    }

    // ─── Factory method ───────────────────────────────────────────────────────

    /**
     * Creates a new, unsigned {@code MoneyTransferTransaction} with a random UUID
     * and the current UTC timestamp.
     *
     * <p>Convenience factory for demos and tests that avoids explicitly constructing
     * the Jackson-annotated all-args constructor. Call {@code wallet.sign(tx)} after
     * this factory to attach an ECDSA signature.</p>
     *
     * @param senderAddress   hex-encoded sender address (non-null, non-blank)
     * @param receiverAddress hex-encoded receiver address (non-null, non-blank)
     * @param amount          transfer amount (&ge; 0; non-null)
     * @param currency        ISO-4217 currency code (non-null, non-blank)
     * @return a new unsigned {@code MoneyTransferTransaction}
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if any string argument is blank or amount is negative
     */
    public static MoneyTransferTransaction of(
        String senderAddress,
        String receiverAddress,
        BigDecimal amount,
        String currency) {

        return new MoneyTransferTransaction(
            UUID.randomUUID(),
            senderAddress,
            receiverAddress,
            amount,
            Instant.now(),
            null,       // no metadata
            currency,
            null);      // no reference
    }

    /**
     * Creates a new, unsigned {@code MoneyTransferTransaction} with a payment reference.
     *
     * @param senderAddress   hex-encoded sender address (non-null, non-blank)
     * @param receiverAddress hex-encoded receiver address (non-null, non-blank)
     * @param amount          transfer amount (&ge; 0; non-null)
     * @param currency        ISO-4217 currency code (non-null, non-blank)
     * @param reference       optional payment reference; may be {@code null}
     * @return a new unsigned {@code MoneyTransferTransaction}
     */
    public static MoneyTransferTransaction of(
        String senderAddress,
        String receiverAddress,
        BigDecimal amount,
        String currency,
        String reference) {

        return new MoneyTransferTransaction(
            UUID.randomUUID(),
            senderAddress,
            receiverAddress,
            amount,
            Instant.now(),
            null,
            currency,
            reference);
    }

    // ─── Signing extension ────────────────────────────────────────────────────

    /**
     * Returns the canonical byte sequence over which the ECDSA signature is computed.
     *
     * <p>Extends the base implementation by appending {@code |currency} so that
     * the currency code is cryptographically bound to the signature. An attacker
     * cannot change {@code "USD"} to {@code "BTC"} without invalidating the
     * signature because the currency field is part of the signed payload.</p>
     *
     * <p>Format: {@code id|sender|receiver|amount|timestamp|currency}
     * (each segment separated by {@code |}).</p>
     *
     * @return deterministic byte array covering all signable fields including currency
     */
    @Override
    public byte[] toSignableBytes() {
        byte[] base = super.toSignableBytes();
        // Append "|currency" to the parent canonical string
        byte[] extension = ("|" + currency).getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[base.length + extension.length];
        System.arraycopy(base, 0, combined, 0, base.length);
        System.arraycopy(extension, 0, combined, base.length, extension.length);
        return combined;
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns the ISO-4217 currency code of this transfer.
     *
     * @return non-null, non-blank currency code (e.g. {@code "USD"})
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Returns the optional payment reference attached to this transfer, or
     * {@code null} if none was specified.
     *
     * @return payment reference string, or {@code null}
     */
    public String getReference() {
        return reference;
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    /**
     * Returns a human-readable summary including the currency and reference fields.
     *
     * <p>The ECDSA signature bytes are intentionally omitted (NFR-SEC-01 applies
     * to keys; omitting the signature is a convention to keep log lines concise).</p>
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "MoneyTransferTransaction{"
            + "id=" + getId()
            + ", sender=" + getSenderAddress()
            + ", receiver=" + getReceiverAddress()
            + ", amount=" + getAmount()
            + ", currency=" + currency
            + ", reference=" + reference
            + ", timestamp=" + getTimestamp()
            + ", signed=" + isSigned()
            + '}';
    }
}
