/**
 * Invitation-token issuance and verification for new-node onboarding (FR-AC-04, FR-AC-05).
 *
 * <p>A node administrator calls {@link com.privatechain.access.invite.InvitationService}
 * to produce a signed token, delivers it out-of-band to the new node operator, and the
 * new node presents it during its first connection attempt. The token is cryptographically
 * bound to a specific node ID and carries an expiry timestamp — expired tokens are always
 * rejected even if their ECDSA signature is valid.</p>
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.access.invite.InvitationService} — signs tokens using
 *       the admin node's {@link com.privatechain.crypto.ECKeyPair} and verifies
 *       incoming tokens against the admin public key.</li>
 *   <li>{@link com.privatechain.access.invite.InvitationToken} — immutable value object
 *       carrying {@code nodeId}, {@code expiryEpochSeconds}, and the raw ECDSA signature
 *       bytes.</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.privatechain.access.invite;
