/**
 * Role-Based Access Control (RBAC) — assigns and enforces capability roles for
 * each connected node address (FR-AC-02, FR-AC-03).
 *
 * <p>Three roles are defined:</p>
 * <table border="1" summary="Role capability matrix">
 *   <tr><th>Role</th><th>Submit Block</th><th>Submit Tx</th><th>Validate</th><th>Read</th></tr>
 *   <tr><td>NODE_ADMIN</td>  <td>Yes</td><td>Yes</td><td>Yes</td><td>Yes</td></tr>
 *   <tr><td>NODE_MINER</td>  <td>Yes</td><td>Yes</td><td>No</td> <td>Yes</td></tr>
 *   <tr><td>NODE_OBSERVER</td><td>No</td> <td>No</td> <td>No</td> <td>Yes</td></tr>
 * </table>
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.access.rbac.NodeRole} — enumeration of the three
 *       available roles.</li>
 *   <li>{@link com.privatechain.access.rbac.PermissionManager} — maps node addresses
 *       to roles and evaluates capability checks via {@code hasRole()}.</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.privatechain.access.rbac;
