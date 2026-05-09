package com.privatechain.core.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Abstract base class for all blockchain transactions.
 *
 * <p>Consumers <em>must</em> subclass this class to define their own transaction
 * types. The six fields declared here are present on every transaction regardless
 * of business logic. Subclasses add domain-specific fields (e.g., asset ID,
 * contract parameters) and may carry arbitrary structured data via the protected
 * {@link #metadata} map without further subclassing (FR-CORE-02).</p>
 *
 * <h2>Why abstract, not interface?</h2>
 * <p>Interfaces cannot hold fields, yet library code must safely access
 * {@code senderAddress}, {@code signature}, etc. on any {@code Transaction}
 * without casting. An abstract class guarantees these fields exist at the cost of
 * requiring single-inheritance — an acceptable trade-off for a value-object hierarchy
 * (see design.md §7.2).</p>
 *
 * <h2>Polymorphic serialization</h2>
 * <p>{@code @JsonTypeInfo} writes the concrete class name into every JSON payload
 * under the {@code _type} field. This ensures that a {@code Transaction} subclass
 * survives a full JSON round-trip on any node that has the subclass on its classpath
 * (FR-SER-01, AC-09).</p>
 *
 * <pre>{@code
 * // Example subclass:
 * public class PaymentTransaction extends Transaction {
 *     private final String currency;
 *     // ...
 * }
 * }</pre>
 *
 * @see Block
 * @since 1.0.0
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "_type")
public abstract class Transaction {

    // ─── Core fields (present on every transaction) ────────────────────────

    /**
     * Flexible key-value map for lightweight extension.
     *
     * <p>Subclasses that only need to attach a few extra string/number values
     * can use this map rather than creating a new subclass. However, for complex
     * or type-safe extensions, a dedicated subclass is preferred.</p>
     *
     * <p>The map is stored as an unmodifiable view once the transaction is built,
     * but the underlying map may be mutated before {@link #sign} is called.</p>
     */
    protected final Map<String, Object> metadata;
    /**
     * Globally unique identifier for this transaction.
     */
    private final UUID id;
    /**
     * Blockchain address of the sender (hex-encoded public-key hash).
     */
    private final String senderAddress;
    /**
     * Blockchain address of the receiver (hex-encoded public-key hash).
     */
    private final String receiverAddress;
    /**
     * Token/coin amount transferred. Use {@link BigDecimal} to avoid floating-point
     * rounding errors when dealing with financial values.
     */
    private final BigDecimal amount;
    /**
     * UTC instant at which the transaction was created by the client.
     */
    private final Instant timestamp;
    /**
     * ECDSA secp256k1 signature bytes produced by the sender's private key over
     * {@link #toSignableBytes()}.  {@code null} until {@link #sign} is called.
     */
    private byte[] signature;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a new unsigned transaction with all required fields.
     *
     * <p>The signature is initialized to {@code null}; call {@link #sign(byte[])}
     * after construction to attach a valid signature before submitting to the network.</p>
     *
     * @param id              unique transaction identifier (non-null)
     * @param senderAddress   hex-encoded sender address (non-null, non-blank)
     * @param receiverAddress hex-encoded receiver address (non-null, non-blank)
     * @param amount          token amount (&ge; 0; non-null)
     * @param timestamp       creation time in UTC (non-null)
     * @param metadata        arbitrary key-value metadata; may be null (treated as empty)
     * @throws NullPointerException     if id, senderAddress, receiverAddress, amount, or
     *                                  timestamp is null
     * @throws IllegalArgumentException if senderAddress or receiverAddress is blank, or
     *                                  if amount is negative
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "Transaction must be abstract to allow consumer-defined subtypes "
            + "(FR-CORE-02 / design.md §7.2), so it cannot be made final. "
            + "Argument validation must occur in the constructor to guarantee "
            + "field invariants before any subclass constructor runs. "
            + "The finalizer-attack risk is mitigated by the fact that this library "
            + "is used in trusted private-network environments, not exposed to "
            + "untrusted serialised input without prior authentication.")
    protected Transaction(
        UUID id,
        String senderAddress,
        String receiverAddress,
        BigDecimal amount,
        Instant timestamp,
        Map<String, Object> metadata) {

        this.id = Objects.requireNonNull(id, "id must not be null");
        this.senderAddress = requireNonBlank(senderAddress, "senderAddress");
        this.receiverAddress = requireNonBlank(receiverAddress, "receiverAddress");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must be >= 0, got: " + amount);
        }
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.metadata = metadata != null
            ? Collections.unmodifiableMap(new HashMap<>(metadata))
            : Collections.emptyMap();
        this.signature = null; // unsigned until sign() is called
    }

    // ─── Signing ──────────────────────────────────────────────────────────────

    /**
     * Validates that a string field is neither null nor blank.
     *
     * @param value     the value to check
     * @param fieldName used in the exception message
     * @return the validated value
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if value is blank
     */
    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    /**
     * Attaches the ECDSA signature to this transaction.
     *
     * <p>This method is intentionally not {@code final} so that subclasses can
     * override it to sign additional domain-specific fields. Callers must invoke
     * the super implementation to set the base signature bytes.</p>
     *
     * <p><strong>Security note:</strong> The raw {@code signatureBytes} array is
     * defensively copied to prevent external mutation after signing.</p>
     *
     * @param signatureBytes DER-encoded ECDSA signature produced by the sender's
     *                       private key over {@link #toSignableBytes()} (non-null,
     *                       non-empty)
     * @throws NullPointerException     if signatureBytes is null
     * @throws IllegalArgumentException if signatureBytes is empty
     */
    public void sign(byte[] signatureBytes) {
        Objects.requireNonNull(signatureBytes, "signatureBytes must not be null");
        if (signatureBytes.length == 0) {
            throw new IllegalArgumentException("signatureBytes must not be empty");
        }
        // Defensive copy — caller cannot mutate signature after signing
        this.signature = Arrays.copyOf(signatureBytes, signatureBytes.length);
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns the byte array that the sender MUST sign and verifiers MUST verify.
     *
     * <p>The default implementation concatenates the canonical string representations
     * of the core fields. Subclasses that add signable fields <em>must</em> override
     * this method and append their own field bytes after calling {@code super.toSignableBytes()}.</p>
     *
     * @return deterministic byte array over which the signature was (or will be) computed
     */
    public byte[] toSignableBytes() {
        // Canonical format: id|sender|receiver|amount|timestamp
        String canonical = id.toString()
            + "|" + senderAddress
            + "|" + receiverAddress
            + "|" + amount.toPlainString()
            + "|" + timestamp.toString();
        return canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Returns the unique identifier of this transaction.
     *
     * @return non-null UUID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the sender's blockchain address.
     *
     * @return non-null, non-blank hex-encoded address
     */
    public String getSenderAddress() {
        return senderAddress;
    }

    /**
     * Returns the receiver's blockchain address.
     *
     * @return non-null, non-blank hex-encoded address
     */
    public String getReceiverAddress() {
        return receiverAddress;
    }

    /**
     * Returns the token amount transferred by this transaction.
     *
     * @return non-null, non-negative {@link BigDecimal}
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Returns the UTC creation timestamp of this transaction.
     *
     * @return non-null {@link Instant}
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Returns a defensive copy of the ECDSA signature bytes.
     *
     * <p>Returns {@code null} if the transaction has not yet been signed.</p>
     *
     * @return copy of signature bytes, or {@code null} if unsigned
     */
    public byte[] getSignature() {
        return signature == null ? null : Arrays.copyOf(signature, signature.length);
    }

    /**
     * Returns {@code true} if a signature has been attached to this transaction.
     *
     * @return {@code true} if signed, {@code false} otherwise
     */
    public boolean isSigned() {
        return signature != null && signature.length > 0;
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable view of the transaction metadata map.
     *
     * <p>SpotBugs flags this as {@code EI_EXPOSE_REP} because a {@link Map} is
     * a mutable type. In practice the field is always assigned a
     * {@link Collections#unmodifiableMap} wrapper in the constructor, so the
     * returned reference cannot be used to mutate the stored data. The
     * {@code @SuppressFBWarnings} documents this deliberate design.</p>
     *
     * @return non-null, unmodifiable metadata map (maybe empty)
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "The metadata field is always wrapped with "
            + "Collections.unmodifiableMap() in the constructor. "
            + "The returned reference is safe to share.")
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Two transactions are equal if and only if their IDs are equal.
     *
     * @param obj object to compare
     * @return {@code true} if {@code obj} is a {@code Transaction} with the same ID
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Transaction other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    /**
     * Hash code based solely on the transaction ID.
     *
     * @return hash code consistent with {@link #equals(Object)}
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns a human-readable summary of the transaction (safe for logging).
     *
     * <p>The signature bytes are intentionally omitted from this output.</p>
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return getClass().getSimpleName()
            + "{id=" + id
            + ", sender=" + senderAddress
            + ", receiver=" + receiverAddress
            + ", amount=" + amount
            + ", timestamp=" + timestamp
            + ", signed=" + isSigned()
            + '}';
    }
}
