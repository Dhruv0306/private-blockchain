---
name: blockchain-spring-audit
description: Comprehensive audit of BlockchainProperties autoconfiguration
metadata:
  type: feedback
---

**Why:** This file is an audit report documenting the analysis of BlockchainProperties.java for completeness, edge
cases, security, and consistency with the project's PoW architecture and Spring Boot standards.

**How to apply:** Use as reference for future changes to configuration handling. Key findings include:

1. **Validation Coverage (8/9 fields validated)**
    - Missing validation on `blockTimeSeconds`: default is 10 but no minimum constraint exists. Per comment (line 79),
      it should be ≥1.
    - Missing validation on `networkPort`: default 8545 is valid but no range check [1024, 65535].
    - Missing validation on `difficulty`: default 4 is valid but no minimum constraint exists.
    - Missing validation on `maxPeers`: default 25 is valid but no minimum constraint exists.
    - Missing validation on `Mempool.maxSize`: default `Integer.MAX_VALUE` is unbounded. Per comment (line 258-259),
      should be ≥1.
    - `chainId` is validated (non-null), but no length check or whitelist for allowed characters.

2. **Edge Cases Not Covered**
    - Zero `blockTimeSeconds` would allow block mining to fail or behave unpredictably.
    - `networkPort = 0` or `networkPort < 1024` could cause bind failures or security issues.
    - `difficulty = 0` or negative values would cause logic errors in PoW validation.
    - Empty or null `chainId` is allowed by setter but should be rejected per Javadoc.
    - `maxSize = 0` would cause immediate mempool exhaustion.

3. **Security Concerns**
    - No DoS protection on `maxSize` — a malicious actor could configure near `Integer.MAX_VALUE` to exhaust memory.
    - No rate limiting hints in configuration.
    - `networkPort` should reject privileged ports unless explicitly authorized.

4. **Documentation Consistency**
    - Javadoc mentions constraints (e.g., "Must be &ge; 1") but no enforcement exists.
    - YAML examples show practical values but don't warn about edge cases.

5. **Testing Gaps**
    - No unit tests for setter validation.
    - No integration test for boundary values (min/max port, min/max difficulty).
    - No test for invalid YAML that triggers Spring's binding errors.

**Recommendation:** Add validation constraints to all numeric setters, length checks on strings, and a security audit of
the mempool size configuration. Consider adding a `maxMempoolSizeInBytes` to prevent memory exhaustion attacks.
