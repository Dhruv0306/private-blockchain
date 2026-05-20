package com.privatechain.access.invite;

import com.privatechain.access.allowlist.AllowlistManager;
import com.privatechain.access.rbac.NodeRole;
import com.privatechain.access.rbac.PermissionManager;
import com.privatechain.crypto.ECDSASignatureUtil;
import com.privatechain.crypto.ECKeyPair;

import java.time.Instant;
import java.util.Objects;

/**
 * Service for generating and verifying time-limited invitation tokens for new node onboarding.
 *
 * <p>An admin node uses this service to generate a signed invitation token for a new node
 * that wishes to join a private blockchain network. The token contains the new node's ID,
 * an expiry time, and an ECDSA signature over both, signed by the admin's private key.
 *
 * <p>When the new node presents its token, this service verifies the signature and checks
 * the expiry. If both checks pass, the new node is added to the allowlist and assigned
 * a default role (FR-AC-04, FR-AC-05).</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // On the admin node:
 * invitationService.generateToken("new-node-123", Instant.now().plusSeconds(3600));
 *   // → sends token out-of-band to new node operator
 *
 * // On the joining node:
 * if (invitationService.verifyToken(token)) {
 *     // Token is valid and node is now allowlisted
 * } else {
 *     // Token is invalid or expired
 * }
 * }</pre>
 *
 * @see InvitationToken
 * @see com.privatechain.access.rbac.PermissionManager
 * @see com.privatechain.access.allowlist.AllowlistManager
 * @since 1.0.0
 */
public class InvitationService {

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * The admin node's key pair, used to sign invitation tokens.
     * The private key is used for signing; the public key is distributed to
     * all nodes for verification.
     */
    private final ECKeyPair adminKeyPair;

    /**
     * Reference to the permission manager; updated when a token is accepted.
     * The joining node is assigned {@link NodeRole#NODE_OBSERVER} by default.
     */
    private final PermissionManager permissionManager;

    /**
     * Reference to the allowlist manager; updated when a token is accepted.
     */
    private final AllowlistManager allowlistManager;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs an {@code InvitationService} with the given admin credentials and manager references.
     *
     * <p>Only instances with the admin's key pair should be created; consumer nodes
     * would have read-only access to the service or verify tokens using just the
     * admin's public key.</p>
     *
     * @param adminKeyPair       the admin node's key pair (non-null)
     * @param permissionManager  reference to the permission manager (non-null)
     * @param allowlistManager   reference to the allowlist manager (non-null)
     * @throws NullPointerException if any parameter is null
     */
    public InvitationService(ECKeyPair adminKeyPair, PermissionManager permissionManager,
                             AllowlistManager allowlistManager) {
        this.adminKeyPair = Objects.requireNonNull(adminKeyPair, "adminKeyPair must not be null");
        this.permissionManager = Objects.requireNonNull(permissionManager, "permissionManager must not be null");
        this.allowlistManager = Objects.requireNonNull(allowlistManager, "allowlistManager must not be null");
    }

    // ─── Token generation ─────────────────────────────────────────────────────

    /**
     * Generates a signed invitation token for a new node.
     *
     * <p>The token contains the node ID, the expiry timestamp, and an ECDSA signature
     * over both, computed using the admin's private key. The token is suitable for
     * transmission to the new node out-of-band (e.g., email, secure chat).
     *
     * <p>The token can be encoded via {@link InvitationToken#getEncodedToken()} for
     * transport as a Base64 string.</p>
     *
     * @param nodeId     the node ID requesting to join (non-null, non-blank)
     * @param expiryTime the token expiry instant (non-null, must be in the future)
     * @return an {@link InvitationToken} signed by the admin
     * @throws NullPointerException     if nodeId or expiryTime is null
     * @throws IllegalArgumentException if nodeId is blank or expiryTime is in the past
     */
    public InvitationToken generateToken(String nodeId, Instant expiryTime) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(expiryTime, "expiryTime must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }

        Instant now = Instant.now();
        if (expiryTime.isBefore(now) || expiryTime.equals(now)) {
            throw new IllegalArgumentException("expiryTime must be in the future");
        }

        // Create token with expiry and sign
        long expiryEpochSeconds = expiryTime.getEpochSecond();
        byte[] payload = buildPayload(nodeId, expiryEpochSeconds);
        byte[] signature = ECDSASignatureUtil.sign(payload, adminKeyPair);

        return InvitationToken.create(nodeId, expiryEpochSeconds, signature);
    }

    /**
     * Convenience overload that generates a token valid for a specified duration from now.
     *
     * @param nodeId           the node ID requesting to join (non-null, non-blank)
     * @param validitySeconds  how many seconds from now the token should expire (positive)
     * @return an {@link InvitationToken} signed by the admin
     * @throws NullPointerException     if nodeId is null
     * @throws IllegalArgumentException if nodeId is blank or validitySeconds is non-positive
     */
    public InvitationToken generateToken(String nodeId, long validitySeconds) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (validitySeconds <= 0) {
            throw new IllegalArgumentException("validitySeconds must be positive");
        }
        Instant expiryTime = Instant.now().plusSeconds(validitySeconds);
        return generateToken(nodeId, expiryTime);
    }

    // ─── Token verification ───────────────────────────────────────────────────

    /**
     * Verifies an invitation token: checks the signature and expiry.
     *
     * <p>On success, the node is added to the allowlist and assigned the
     * {@link NodeRole#NODE_OBSERVER} role. On failure, the node is not modified
     * and this method returns {@code false}.</p>
     *
     * @param token the token to verify (non-null)
     * @return {@code true} if the token is valid and accepted; {@code false} if
     *         the signature is invalid, the token is expired, or verification fails
     * @throws NullPointerException if token is null
     */
    public boolean verifyToken(InvitationToken token) {
        Objects.requireNonNull(token, "token must not be null");

        // Check expiry first (cheaper than cryptographic verification)
        if (token.isExpired()) {
            return false;
        }

        // Verify the ECDSA signature over the payload
        byte[] payload = token.getSignableBytes();
        byte[] signature = token.getSignature();
        if (!ECDSASignatureUtil.verify(payload, signature, adminKeyPair.getPublicKey())) {
            return false;
        }

        // Signature and expiry both valid; add node to allowlist and assign default role
        String nodeId = token.getNodeId();
        allowlistManager.addFromInvitation(nodeId);
        permissionManager.assignRole(nodeId, NodeRole.NODE_OBSERVER);

        return true;
    }

    /**
     * Verifies an invitation token from an encoded (Base64) string.
     *
     * <p>This is a convenience overload that parses the token before verification.</p>
     *
     * @param encodedToken the Base64-encoded token (non-null, non-blank)
     * @return {@code true} if the token is valid and accepted; {@code false} otherwise
     * @throws NullPointerException     if encodedToken is null
     * @throws IllegalArgumentException if encodedToken is blank or malformed
     */
    public boolean verifyToken(String encodedToken) {
        Objects.requireNonNull(encodedToken, "encodedToken must not be null");
        if (encodedToken.isBlank()) {
            throw new IllegalArgumentException("encodedToken must not be blank");
        }

        try {
            InvitationToken token = InvitationToken.parseEncodedToken(encodedToken);
            return verifyToken(token);
        } catch (IllegalArgumentException e) {
            // Token parsing failed
            return false;
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Builds the payload that is signed: {@code nodeId || expiryEpochSeconds}.
     *
     * @param nodeId             the node ID
     * @param expiryEpochSeconds the expiry timestamp in Unix seconds
     * @return the signing payload
     */
    private byte[] buildPayload(String nodeId, long expiryEpochSeconds) {
        byte[] nodeIdBytes = nodeId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] payload = new byte[nodeIdBytes.length + 8];
        System.arraycopy(nodeIdBytes, 0, payload, 0, nodeIdBytes.length);
        longToBytes(expiryEpochSeconds, payload, nodeIdBytes.length);
        return payload;
    }

    /**
     * Writes a long to an 8-byte big-endian sequence.
     */
    private static void longToBytes(long value, byte[] bytes, int offset) {
        bytes[offset] = (byte) ((value >> 56) & 0xFF);
        bytes[offset + 1] = (byte) ((value >> 48) & 0xFF);
        bytes[offset + 2] = (byte) ((value >> 40) & 0xFF);
        bytes[offset + 3] = (byte) ((value >> 32) & 0xFF);
        bytes[offset + 4] = (byte) ((value >> 24) & 0xFF);
        bytes[offset + 5] = (byte) ((value >> 16) & 0xFF);
        bytes[offset + 6] = (byte) ((value >> 8) & 0xFF);
        bytes[offset + 7] = (byte) (value & 0xFF);
    }
}

