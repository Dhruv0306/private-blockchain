package com.privatechain.crypto;

import java.io.Serial;

/**
 * Unchecked exception thrown when a cryptographic operation fails.
 *
 * <p>This exception wraps checked JCE exceptions ({@link java.security.NoSuchAlgorithmException},
 * {@link java.security.spec.InvalidKeySpecException}, etc.) to avoid forcing callers
 * to handle checked exceptions for failures that should never occur in a correctly
 * configured environment.
 *
 * <p>If you see this exception at runtime, it most commonly means:
 * <ul>
 *   <li>BouncyCastle ({@code bcprov-jdk18on}) is missing from the classpath.</li>
 *   <li>A key was reconstructed from bytes produced by a different encoding scheme.</li>
 *   <li>The JVM security policy is restricting JCE operations.</li>
 * </ul>
 */
public class CryptoException extends RuntimeException {

    /**
     * Serialization version UID for compatibility with future versions of this class.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Default Constructor for class `CryptoException`.
     */
    public CryptoException() {}

    /**
     * Constructs a new {@code CryptoException} with the given message.
     *
     * @param message human-readable description of the failure
     */
    public CryptoException(final String message) {
        super(message);
    }

    /**
     * Constructs a new {@code CryptoException} wrapping a cause.
     *
     * @param message human-readable description of the failure
     * @param cause   the underlying JCE or security exception
     */
    public CryptoException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
