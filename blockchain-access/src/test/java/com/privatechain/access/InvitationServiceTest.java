package com.privatechain.access;

import com.privatechain.access.allowlist.AllowlistManager;
import com.privatechain.access.invite.InvitationService;
import com.privatechain.access.invite.InvitationToken;
import com.privatechain.access.rbac.NodeRole;
import com.privatechain.access.rbac.PermissionManager;
import com.privatechain.crypto.KeyPairGenerator;
import com.privatechain.crypto.ECKeyPair;
import com.privatechain.core.spi.BlockchainStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Contract test for {@link InvitationService} and {@link InvitationToken}.
 *
 * <p>Verifies token generation, encoding, verification, expiry checking, and
 * integration with {@link AllowlistManager} and {@link PermissionManager}.</p>
 */
class InvitationServiceTest {

    private InvitationService invitationService;
    private ECKeyPair adminKeyPair;
    private PermissionManager permissionManager;
    private AllowlistManager allowlistManager;
    private BlockchainStorage mockStorage;

    @BeforeEach
    void setUp() {
        adminKeyPair = KeyPairGenerator.generateECKeyPair();
        mockStorage = mock(BlockchainStorage.class);
        permissionManager = new PermissionManager(mockStorage);
        allowlistManager = new AllowlistManager();
        invitationService = new InvitationService(adminKeyPair, permissionManager, allowlistManager);
    }

    // ─── Constructor tests ─────────────────────────────────────────────────────

    @Test
    void constructorRequiresNonNullAdminKeyPair() {
        assertThrows(NullPointerException.class,
            () -> new InvitationService(null, permissionManager, allowlistManager));
    }

    @Test
    void constructorRequiresNonNullPermissionManager() {
        assertThrows(NullPointerException.class,
            () -> new InvitationService(adminKeyPair, null, allowlistManager));
    }

    @Test
    void constructorRequiresNonNullAllowlistManager() {
        assertThrows(NullPointerException.class,
            () -> new InvitationService(adminKeyPair, permissionManager, null));
    }

    // ─── Token generation tests ────────────────────────────────────────────────

    @Test
    void generateTokenCreatesValidToken() {
        String nodeId = "new-node-123";
        Instant expiry = Instant.now().plusSeconds(3600);

        InvitationToken token = invitationService.generateToken(nodeId, expiry);

        assertEquals(nodeId, token.getNodeId());
        assertEquals(expiry.getEpochSecond(), token.getExpiryEpochSeconds());
        assertFalse(token.isExpired());
        assertTrue(token.getSignature().length > 0);
    }

    @Test
    void generateTokenWithValiditySecondsCreatesValidToken() {
        String nodeId = "new-node-456";
        long validitySeconds = 7200;

        InvitationToken token = invitationService.generateToken(nodeId, validitySeconds);

        assertEquals(nodeId, token.getNodeId());
        assertFalse(token.isExpired());
    }

    @Test
    void generateTokenRequiresNonNullNodeId() {
        Instant expiry = Instant.now().plusSeconds(3600);
        assertThrows(NullPointerException.class,
            () -> invitationService.generateToken(null, expiry));
    }

    @Test
    void generateTokenRequiresNonBlankNodeId() {
        Instant expiry = Instant.now().plusSeconds(3600);
        assertThrows(IllegalArgumentException.class,
            () -> invitationService.generateToken("", expiry));
        assertThrows(IllegalArgumentException.class,
            () -> invitationService.generateToken("   ", expiry));
    }

    @Test
    void generateTokenRequiresNonNullExpiry() {
        assertThrows(NullPointerException.class,
            () -> invitationService.generateToken("new-node", null));
    }

    @Test
    void generateTokenRequiresFutureExpiry() {
        String nodeId = "new-node";
        Instant pastExpiry = Instant.now().minusSeconds(3600);
        assertThrows(IllegalArgumentException.class,
            () -> invitationService.generateToken(nodeId, pastExpiry));
    }

    @Test
    void generateTokenWithValidityRequiresPositiveSeconds() {
        assertThrows(IllegalArgumentException.class,
            () -> invitationService.generateToken("new-node", 0));
        assertThrows(IllegalArgumentException.class,
            () -> invitationService.generateToken("new-node", -1));
    }

    // ─── Token verification tests ──────────────────────────────────────────────

    @Test
    void verifyTokenAcceptsValidToken() {
        String nodeId = "new-node-789";
        InvitationToken token = invitationService.generateToken(nodeId, Instant.now().plusSeconds(3600));

        assertTrue(invitationService.verifyToken(token));
        assertTrue(allowlistManager.isAllowed(nodeId));
        assertTrue(permissionManager.hasRole(nodeId, NodeRole.NODE_OBSERVER));
    }

    @Test
    void verifyTokenRejectsExpiredToken() {
        String nodeId = "expired-node";
        Instant pastExpiry = Instant.now().minusSeconds(1);
        // We need to manually create an expired token since generateToken prevents past expiry
        // For this test, we'll use reflection or create a mock token
        InvitationToken token = createExpiredToken(nodeId, pastExpiry);

        assertFalse(invitationService.verifyToken(token));
        assertFalse(allowlistManager.isAllowed(nodeId));
    }

    @Test
    void verifyTokenRejectsTokenFromWrongAdminKey() {
        String nodeId = "new-node";
        ECKeyPair wrongAdminKeyPair = KeyPairGenerator.generateECKeyPair();
        InvitationService wrongService = new InvitationService(wrongAdminKeyPair, permissionManager, allowlistManager);

        // Generate token with original admin
        InvitationToken token = invitationService.generateToken(nodeId, Instant.now().plusSeconds(3600));

        // Verify with wrong admin key (should fail)
        assertFalse(wrongService.verifyToken(token));
        assertFalse(allowlistManager.isAllowed(nodeId));
    }

    @Test
    void verifyTokenAddsNodeToAllowlist() {
        String nodeId = "new-node";
        InvitationToken token = invitationService.generateToken(nodeId, Instant.now().plusSeconds(3600));

        invitationService.verifyToken(token);

        assertTrue(allowlistManager.isAllowed(nodeId));
    }

    @Test
    void verifyTokenAssignsNodeObserverRole() {
        String nodeId = "new-node";
        InvitationToken token = invitationService.generateToken(nodeId, Instant.now().plusSeconds(3600));

        invitationService.verifyToken(token);

        assertTrue(permissionManager.hasRole(nodeId, NodeRole.NODE_OBSERVER));
    }

    @Test
    void verifyTokenRequiresNonNullToken() {
        assertThrows(NullPointerException.class, () -> invitationService.verifyToken((InvitationToken) null));
    }

    @Test
    void verifyEncodedTokenAcceptsValidEncodedToken() {
        String nodeId = "new-node";
        InvitationToken token = invitationService.generateToken(nodeId, Instant.now().plusSeconds(3600));
        String encoded = token.getEncodedToken();

        assertTrue(invitationService.verifyToken(encoded));
        assertTrue(allowlistManager.isAllowed(nodeId));
    }

    @Test
    void verifyEncodedTokenRequiresNonNullToken() {
        assertThrows(NullPointerException.class, () -> invitationService.verifyToken((String) null));
    }

    @Test
    void verifyEncodedTokenRequiresNonBlankToken() {
        assertThrows(IllegalArgumentException.class, () -> invitationService.verifyToken(""));
        assertThrows(IllegalArgumentException.class, () -> invitationService.verifyToken("   "));
    }

    @Test
    void verifyEncodedTokenReturnsFalseForMalformedToken() {
        String malformedToken = "this-is-not-base64-or-is-corrupted!!!";
        assertFalse(invitationService.verifyToken(malformedToken));
    }

    // ─── Token encoding/decoding tests ─────────────────────────────────────────

    @Test
    void tokenEncodingRoundTripsSuccessfully() {
        String nodeId = "new-node";
        InvitationToken token = invitationService.generateToken(nodeId, Instant.now().plusSeconds(3600));

        String encoded = token.getEncodedToken();
        InvitationToken decoded = InvitationToken.parseEncodedToken(encoded);

        assertEquals(token.getNodeId(), decoded.getNodeId());
        assertEquals(token.getExpiryEpochSeconds(), decoded.getExpiryEpochSeconds());
    }

    @Test
    void tokenEncodingIsBase64() {
        String nodeId = "new-node";
        InvitationToken token = invitationService.generateToken(nodeId, Instant.now().plusSeconds(3600));

        String encoded = token.getEncodedToken();

        // Base64 should only contain alphanumeric, +, /, and = characters
        assertTrue(encoded.matches("[A-Za-z0-9+/=]*"));
    }

    // ─── Token expiry tests ────────────────────────────────────────────────────

    @Test
    void tokenIsNotExpiredBeforeExpiry() {
        InvitationToken token = invitationService.generateToken("node", Instant.now().plusSeconds(3600));
        assertFalse(token.isExpired());
    }

    @Test
    void tokenIsExpiredAfterExpiry() {
        InvitationToken token = createExpiredToken("node", Instant.now().minusSeconds(1));
        assertTrue(token.isExpired());
    }

    @Test
    void tokenExpiryInstantMatches() {
        Instant expiry = Instant.now().plusSeconds(1000);
        InvitationToken token = invitationService.generateToken("node", expiry);
        assertEquals(expiry.getEpochSecond(), token.getExpiryInstant().getEpochSecond());
    }

    // ─── Integration tests ────────────────────────────────────────────────────

    @Test
    void multipleNodesCanBeInvitedSequentially() {
        for (int i = 0; i < 5; i++) {
            String nodeId = "node-" + i;
            InvitationToken token = invitationService.generateToken(nodeId, Instant.now().plusSeconds(3600));
            assertTrue(invitationService.verifyToken(token));
        }

        assertEquals(5, allowlistManager.size());
    }

    @Test
    void permissionUpgradeAfterInvitationAcceptance() {
        String nodeId = "new-node";
        InvitationToken token = invitationService.generateToken(nodeId, Instant.now().plusSeconds(3600));

        // Before verification
        assertFalse(permissionManager.hasRole(nodeId, NodeRole.NODE_OBSERVER));

        // After verification
        invitationService.verifyToken(token);
        assertTrue(permissionManager.hasRole(nodeId, NodeRole.NODE_OBSERVER));

        // Can be upgraded to miner
        permissionManager.assignRole(nodeId, NodeRole.NODE_MINER);
        assertTrue(permissionManager.hasRole(nodeId, NodeRole.NODE_MINER));
    }

    // ─── Private helper methods ────────────────────────────────────────────────

    /**
     * Creates a token that is already expired for testing purposes.
     */
    private InvitationToken createExpiredToken(String nodeId, Instant expiry) {
        byte[] nodeIdBytes = nodeId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] payload = new byte[nodeIdBytes.length + 8];
        System.arraycopy(nodeIdBytes, 0, payload, 0, nodeIdBytes.length);
        longToBytes(expiry.getEpochSecond(), payload, nodeIdBytes.length);

        byte[] signature = new byte[71]; // Dummy signature
        return InvitationToken.create(nodeId, expiry.getEpochSecond(), signature);
    }

    private void longToBytes(long value, byte[] bytes, int offset) {
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

