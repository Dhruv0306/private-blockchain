/**
 * Private-network access control — allowlist enforcement, role-based permissions,
 * and invitation-token issuance for the private-blockchain library.
 *
 * <p>Three sub-packages make up the access layer:</p>
 * <ul>
 *   <li>{@link com.privatechain.access.allowlist} — enforces the set of permitted
 *       node identifiers before any inbound message is processed (FR-AC-01).</li>
 *   <li>{@link com.privatechain.access.rbac} — assigns and evaluates roles
 *       ({@code NODE_ADMIN}, {@code NODE_MINER}, {@code NODE_OBSERVER}) for each
 *       node address (FR-AC-02).</li>
 *   <li>{@link com.privatechain.access.invite} — issues and verifies ECDSA-signed,
 *       time-limited invitation tokens so new nodes can join a private chain
 *       (FR-AC-04, FR-AC-05).</li>
 * </ul>
 *
 * <p>The access layer sits between the TCP transport and the message handler:</p>
 * <pre>
 *   Inbound TCP message
 *          │
 *          ▼
 *   AllowlistManager ──[DENY]──► drop + log
 *          │ [ALLOW]
 *          ▼
 *   PermissionManager ──[INSUFFICIENT ROLE]──► return error
 *          │ [AUTHORIZED]
 *          ▼
 *   Message handler
 * </pre>
 *
 * @since 1.0.0
 */
package com.privatechain.access;
