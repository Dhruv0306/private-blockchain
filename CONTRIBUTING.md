# Contributing to private-blockchain

Thank you for contributing! Follow these guidelines:

## Prerequisites

- **Java 17+** (tested on JDK 17 and JDK 21)
- **Maven 3.8.1+**
- Git

## Setup

```bash
git clone https://github.com/your-org/private-blockchain.git
cd private-blockchain
mvn clean install
```

## Build & Test
```bash
# Full build with all checks
mvn clean verify

# Run tests only
mvn test

# Run security scan (on-demand)
mvn -Psecurity verify
```

## Requirements Before Submitting a PR
1. All tests pass: mvn clean verify
2. Checkstyle clean: No warnings from Google Java Style (120 char limit)
3. SpotBugs clean: No HIGH or CRITICAL findings
4. Coverage >= 80%: On blockchain-core module
5. Javadoc complete: All public APIs must have full Javadoc
6. No plaintext secrets: Verify no API keys, passwords, or private keys in code

## Code Style
- Follow Google [Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Max line length: 120 characters
- Use 4-space indentation 
- Write Javadoc for all public methods and classes

## Commit Message Format
Format: `[TASK-ID] Brief description`
Example:
```text
[T-025] Implement SHA-256 hash utility
[T-052] Add allowlist manager with unit tests
```

## Security
- Never commit private keys, certificates, or API tokens 
- All crypto utilities must undergo peer review 
- Report security vulnerabilities privately (see SECURITY.md)

## Questions?
Open an issue or start a discussion on GitHub.
