package com.privatechain.network.rpc;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Encodes and decodes wire-protocol messages exchanged between blockchain nodes.
 *
 * <p>{@code MessageCodec} is responsible for the serialization layer of the P2P
 * protocol. All messages are framed as UTF-8 JSON with a {@code type} discriminator
 * field. This makes the protocol human-readable and debuggable while meeting the
 * portability requirements of the private-blockchain library.</p>
 *
 * <h2>Message format</h2>
 * <p>Every message follows a simple envelope:</p>
 * <pre>{@code
 * {
 *   "type": "BLOCK",          // one of the MessageType enum values
 *   "senderId": "nodeId...",  // originating node ID
 *   "payload": { ... }        // type-specific JSON object
 * }
 * }</pre>
 *
 * <h2>Supported message types</h2>
 * <ul>
 *   <li>{@link MessageType#BLOCK} — a newly mined or propagated block</li>
 *   <li>{@link MessageType#TRANSACTION} — a submitted transaction</li>
 *   <li>{@link MessageType#GET_STATUS} — request for a peer's chain height</li>
 *   <li>{@link MessageType#STATUS} — response carrying a peer's chain height</li>
 *   <li>{@link MessageType#GET_BLOCKS} — request for blocks in a height range</li>
 *   <li>{@link MessageType#PING} — heartbeat request</li>
 *   <li>{@link MessageType#PONG} — heartbeat response</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>Jackson's {@link ObjectMapper} is thread-safe after configuration; this class
 * is safe for concurrent use by multiple threads.</p>
 *
 * @since 1.0.0
 */
public final class MessageCodec {

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * Shared, fully-configured Jackson mapper.
     * Immutable after construction — safe for concurrent use.
     */
    private final ObjectMapper mapper;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code MessageCodec} with a pre-configured {@link ObjectMapper}.
     *
     * <p>The mapper is configured to:</p>
     * <ul>
     *   <li>Serialize {@link java.time.Instant} via the {@code JavaTimeModule}</li>
     *   <li>Disable {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} so
     *       timestamps appear as ISO-8601 strings</li>
     * </ul>
     */
    public MessageCodec() {
        // This mapper configuration MUST match BlockSerializer.MAPPER exactly.
        // Block deserialization uses @JsonCreator with named parameters; BlockHeader
        // is a Java record whose components must be read from fields, not getters.
        // Missing any of these settings causes Block.isHashValid() to fail after
        // round-trip because the Instant timestamp or other fields deserialize incorrectly.
        ObjectMapper m = new ObjectMapper();

        // Serialize Instant as ISO-8601 string (e.g. "2026-04-22T10:15:30Z"), not array
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Block has derived getters (getMerkleRoot, getTimestamp) that appear as extra
        // JSON fields on serialization but have no matching @JsonProperty on the
        // @JsonCreator constructor — without this, deserialization throws
        // "Unrecognized field" and the block is never reconstructed
        m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Do NOT drive serialization from public getters (avoids writing derived fields)
        m.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        m.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);

        // Read private fields so Transaction subtype fields are accessible without
        // requiring public getters on every custom subclass
        m.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        this.mapper = m;
    }

    // ─── Encoding ─────────────────────────────────────────────────────────────

    /**
     * Asserts that a message is of the expected type.
     *
     * @param message      the message to check (non-null)
     * @param expectedType the required type
     * @throws IllegalArgumentException if the message type does not match
     */
    private static void requireType(NetworkMessage message, MessageType expectedType) {
        Objects.requireNonNull(message, "message must not be null");
        if (message.getType() != expectedType) {
            throw new IllegalArgumentException(
                "Expected message type " + expectedType + " but got " + message.getType());
        }
    }

    /**
     * Encodes a {@link NetworkMessage} to a UTF-8 JSON byte array.
     *
     * @param message the message to encode (non-null)
     * @return UTF-8 JSON bytes
     * @throws IllegalArgumentException if encoding fails
     * @throws NullPointerException     if message is null
     */
    public byte[] encode(NetworkMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        try {
            return mapper.writeValueAsBytes(message);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to encode message: " + e.getMessage(), e);
        }
    }

    // ─── Decoding ─────────────────────────────────────────────────────────────

    /**
     * Encodes a {@link NetworkMessage} to a UTF-8 JSON string.
     *
     * @param message the message to encode (non-null)
     * @return JSON string
     * @throws IllegalArgumentException if encoding fails
     */
    public String encodeToString(NetworkMessage message) {
        return new String(encode(message), StandardCharsets.UTF_8);
    }

    /**
     * Decodes a UTF-8 JSON byte array into a {@link NetworkMessage}.
     *
     * @param bytes the raw bytes to decode (non-null, non-empty)
     * @return decoded {@link NetworkMessage}
     * @throws IllegalArgumentException if decoding fails or bytes are empty
     * @throws NullPointerException     if bytes is null
     */
    public NetworkMessage decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Cannot decode an empty byte array");
        }
        try {
            return mapper.readValue(bytes, NetworkMessage.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to decode message: " + e.getMessage(), e);
        }
    }

    // ─── Message factory helpers ──────────────────────────────────────────────

    /**
     * Decodes a JSON string into a {@link NetworkMessage}.
     *
     * @param json the JSON string to decode (non-null, non-blank)
     * @return decoded {@link NetworkMessage}
     * @throws IllegalArgumentException if decoding fails or JSON is blank
     */
    public NetworkMessage decodeFromString(String json) {
        Objects.requireNonNull(json, "json must not be null");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Cannot decode a blank JSON string");
        }
        return decode(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a {@code BLOCK} message carrying the given block.
     *
     * @param senderId the originating node ID (non-null, non-blank)
     * @param block    the block to propagate (non-null)
     * @return a {@link NetworkMessage} of type {@link MessageType#BLOCK}
     */
    public NetworkMessage blockMessage(String senderId, Block block) {
        Objects.requireNonNull(block, "block must not be null");
        try {
            String payload = mapper.writeValueAsString(block);
            return new NetworkMessage(MessageType.BLOCK, senderId, payload);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to serialize block: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a {@code TRANSACTION} message carrying the given transaction.
     *
     * @param senderId    the originating node ID (non-null, non-blank)
     * @param transaction the transaction to gossip (non-null)
     * @return a {@link NetworkMessage} of type {@link MessageType#TRANSACTION}
     */
    public NetworkMessage transactionMessage(String senderId, Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        try {
            String payload = mapper.writeValueAsString(transaction);
            return new NetworkMessage(MessageType.TRANSACTION, senderId, payload);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to serialize transaction: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a {@code GET_STATUS} message requesting a peer's current chain height.
     *
     * @param senderId the originating node ID (non-null, non-blank)
     * @return a {@link NetworkMessage} of type {@link MessageType#GET_STATUS}
     */
    public NetworkMessage getStatusMessage(String senderId) {
        return new NetworkMessage(MessageType.GET_STATUS, senderId, "{}");
    }

    /**
     * Creates a {@code STATUS} message reporting this node's chain height.
     *
     * @param senderId    the originating node ID (non-null, non-blank)
     * @param chainHeight the current chain height
     * @return a {@link NetworkMessage} of type {@link MessageType#STATUS}
     */
    public NetworkMessage statusMessage(String senderId, int chainHeight) {
        String payload = "{\"chainHeight\":" + chainHeight + "}";
        return new NetworkMessage(MessageType.STATUS, senderId, payload);
    }

    /**
     * Creates a {@code GET_BLOCKS} message requesting blocks in a height range.
     *
     * @param senderId   the originating node ID
     * @param fromHeight inclusive start height
     * @param toHeight   inclusive end height
     * @return a {@link NetworkMessage} of type {@link MessageType#GET_BLOCKS}
     */
    public NetworkMessage getBlocksMessage(String senderId, int fromHeight, int toHeight) {
        String payload = "{\"from\":" + fromHeight + ",\"to\":" + toHeight + "}";
        return new NetworkMessage(MessageType.GET_BLOCKS, senderId, payload);
    }

    /**
     * Creates a {@code PING} heartbeat request message.
     *
     * @param senderId the originating node ID (non-null, non-blank)
     * @return a {@link NetworkMessage} of type {@link MessageType#PING}
     */
    public NetworkMessage pingMessage(String senderId) {
        return new NetworkMessage(MessageType.PING, senderId, "{}");
    }

    // ─── Payload extraction helpers ───────────────────────────────────────────

    /**
     * Creates a {@code PONG} heartbeat response message.
     *
     * @param senderId the originating node ID (non-null, non-blank)
     * @return a {@link NetworkMessage} of type {@link MessageType#PONG}
     */
    public NetworkMessage pongMessage(String senderId) {
        return new NetworkMessage(MessageType.PONG, senderId, "{}");
    }

    /**
     * Extracts and deserializes a {@link Block} from the payload of a {@code BLOCK} message.
     *
     * @param message a message of type {@link MessageType#BLOCK} (non-null)
     * @return the deserialized block
     * @throws IllegalArgumentException if the message type is wrong or parsing fails
     */
    public Block extractBlock(NetworkMessage message) {
        requireType(message, MessageType.BLOCK);
        try {
            return mapper.readValue(message.getPayload(), Block.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to extract block from message: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts and deserializes a {@link Transaction} from the payload of a
     * {@code TRANSACTION} message.
     *
     * @param message a message of type {@link MessageType#TRANSACTION} (non-null)
     * @return the deserialized transaction
     * @throws IllegalArgumentException if the message type is wrong or parsing fails
     */
    public Transaction extractTransaction(NetworkMessage message) {
        requireType(message, MessageType.TRANSACTION);
        try {
            return mapper.readValue(message.getPayload(), Transaction.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to extract transaction from message: " + e.getMessage(), e);
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Extracts the chain height from the payload of a {@code STATUS} message.
     *
     * @param message a message of type {@link MessageType#STATUS} (non-null)
     * @return the reported chain height
     * @throws IllegalArgumentException if parsing fails
     */
    public int extractChainHeight(NetworkMessage message) {
        requireType(message, MessageType.STATUS);
        try {
            return mapper.readTree(message.getPayload()).get("chainHeight").asInt();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to extract chain height: " + e.getMessage(), e);
        }
    }

    // ─── Nested types ─────────────────────────────────────────────────────────

    /**
     * Discriminator enum identifying the kind of P2P message.
     *
     * @since 1.0.0
     */
    public enum MessageType {
        /**
         * A fully formed block to be added to the chain.
         */
        BLOCK,
        /**
         * A signed transaction to be validated and added to the mempool.
         */
        TRANSACTION,
        /**
         * Request for a peer's current chain height.
         */
        GET_STATUS,
        /**
         * Response reporting this node's current chain height.
         */
        STATUS,
        /**
         * Request for a range of blocks by height.
         */
        GET_BLOCKS,
        /**
         * Heartbeat ping.
         */
        PING,
        /**
         * Heartbeat pong response.
         */
        PONG
    }

    /**
     * Immutable envelope wrapping all P2P messages.
     *
     * <p>The {@code payload} field holds a raw JSON string whose schema is determined
     * by {@link MessageType}. Storing it as a pre-serialized string avoids double-encoding during envelope serialization and simplifies re-forwarding in the
     * gossip layer.</p>
     *
     * @since 1.0.0
     */
    public static final class NetworkMessage {

        /**
         * Message type discriminator.
         */
        private final MessageType type;

        /**
         * Node ID of the sender. Used for allowlist checks and loop prevention.
         */
        private final String senderId;

        /**
         * Pre-serialized JSON payload (type-specific content).
         */
        private final String payload;

        /**
         * Constructs a {@code NetworkMessage}.
         *
         * @param type     message type (non-null)
         * @param senderId originating node ID (non-null, non-blank)
         * @param payload  JSON payload string (non-null)
         * @throws NullPointerException     if type, senderId, or payload is null
         * @throws IllegalArgumentException if senderId is blank
         */
        @JsonCreator
        public NetworkMessage(
            @JsonProperty("type") MessageType type,
            @JsonProperty("senderId") String senderId,
            @JsonProperty("payload") String payload) {
            this.type = Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(senderId, "senderId must not be null");
            if (senderId.isBlank()) {
                throw new IllegalArgumentException("senderId must not be blank");
            }
            this.senderId = senderId;
            this.payload = Objects.requireNonNull(payload, "payload must not be null");
        }

        /**
         * Returns the message type discriminator.
         *
         * @return non-null {@link MessageType}
         */
        public MessageType getType() {
            return type;
        }

        /**
         * Returns the node ID of the originating sender.
         *
         * @return non-null, non-blank sender ID
         */
        public String getSenderId() {
            return senderId;
        }

        /**
         * Returns the raw JSON payload string.
         *
         * @return non-null JSON string
         */
        public String getPayload() {
            return payload;
        }

        /**
         * Returns a log-safe summary of this message.
         *
         * @return string representation
         */
        @Override
        public String toString() {
            return "NetworkMessage{type=" + type
                + ", sender=" + senderId
                + ", payloadLen=" + payload.length()
                + '}';
        }
    }
}
