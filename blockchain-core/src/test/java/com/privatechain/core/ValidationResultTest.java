package com.privatechain.core;

import com.privatechain.core.spi.ValidationResult;
import com.privatechain.core.spi.ValidationResult.ValidationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ValidationResult} and {@link ValidationStatus}
 * covering all factory methods, enum values, and edge cases.
 */
@DisplayName("ValidationResult")
class ValidationResultTest {

    // ─── success() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("success() returns isSuccess=true and status=VALID")
    void successIsValid() {
        ValidationResult r = ValidationResult.success();
        assertTrue(r.isSuccess());
        assertFalse(r.isFailure());
        assertEquals(ValidationStatus.VALID, r.getStatus());
        assertTrue(r.getErrors().isEmpty());
    }

    @Test
    @DisplayName("success() is a singleton — same instance returned each call")
    void successIsSingleton() {
        assertTrue(ValidationResult.success() == ValidationResult.success(),
            "success() should return the same singleton instance");
    }

    // ─── failure(status, message) ─────────────────────────────────────────────

    @Test
    @DisplayName("failure with INVALID_SIGNATURE sets correct fields")
    void failureInvalidSignature() {
        ValidationResult r = ValidationResult.failure(
            ValidationStatus.INVALID_SIGNATURE, "Bad sig");
        assertFalse(r.isSuccess());
        assertTrue(r.isFailure());
        assertEquals(ValidationStatus.INVALID_SIGNATURE, r.getStatus());
        assertEquals(1, r.getErrors().size());
        assertEquals("Bad sig", r.getErrors().get(0));
    }

    @Test
    @DisplayName("failure with INSUFFICIENT_FUNDS sets correct fields")
    void failureInsufficientFunds() {
        ValidationResult r = ValidationResult.failure(
            ValidationStatus.INSUFFICIENT_FUNDS, "Not enough balance");
        assertEquals(ValidationStatus.INSUFFICIENT_FUNDS, r.getStatus());
        assertEquals("Not enough balance", r.getErrors().get(0));
    }

    @Test
    @DisplayName("failure with DUPLICATE sets correct fields")
    void failureDuplicate() {
        ValidationResult r = ValidationResult.failure(
            ValidationStatus.DUPLICATE, "Tx already seen");
        assertEquals(ValidationStatus.DUPLICATE, r.getStatus());
    }

    @Test
    @DisplayName("failure with CUSTOM_REJECTION sets correct fields")
    void failureCustomRejection() {
        ValidationResult r = ValidationResult.failure(
            ValidationStatus.CUSTOM_REJECTION, "KYC not completed");
        assertEquals(ValidationStatus.CUSTOM_REJECTION, r.getStatus());
    }

    @Test
    @DisplayName("failure with VALID status throws IllegalArgumentException")
    void failureWithValidStatusThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> ValidationResult.failure(ValidationStatus.VALID, "msg"));
    }

    @Test
    @DisplayName("failure with null status throws NullPointerException")
    void failureWithNullStatusThrows() {
        assertThrows(NullPointerException.class,
            () -> ValidationResult.failure(null, "msg"));
    }

    @Test
    @DisplayName("failure with null message throws NullPointerException")
    void failureWithNullMessageThrows() {
        assertThrows(NullPointerException.class,
            () -> ValidationResult.failure(ValidationStatus.DUPLICATE, (String) null));
    }

    @Test
    @DisplayName("failure with blank message throws IllegalArgumentException")
    void failureWithBlankMessageThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> ValidationResult.failure(ValidationStatus.DUPLICATE, "  "));
    }

    // ─── failure(status, list) ────────────────────────────────────────────────

    @Test
    @DisplayName("failure with list of errors carries all messages")
    void failureWithMultipleErrors() {
        List<String> errors = List.of("Error A", "Error B", "Error C");
        ValidationResult r = ValidationResult.failure(ValidationStatus.CUSTOM_REJECTION, errors);

        assertFalse(r.isSuccess());
        assertEquals(3, r.getErrors().size());
        assertEquals("Error A", r.getErrors().get(0));
        assertEquals("Error C", r.getErrors().get(2));
    }

    @Test
    @DisplayName("failure with empty list throws IllegalArgumentException")
    void failureWithEmptyListThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> ValidationResult.failure(ValidationStatus.DUPLICATE, List.of()));
    }

    @Test
    @DisplayName("failure with null list throws NullPointerException")
    void failureWithNullListThrows() {
        assertThrows(NullPointerException.class,
            () -> ValidationResult.failure(ValidationStatus.DUPLICATE, (List<String>) null));
    }

    @Test
    @DisplayName("failure with VALID status and list throws IllegalArgumentException")
    void failureListWithValidStatusThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> ValidationResult.failure(ValidationStatus.VALID, List.of("msg")));
    }

    // ─── Errors list is unmodifiable ──────────────────────────────────────────

    @Test
    @DisplayName("errors list returned from failure is unmodifiable")
    void errorsListIsUnmodifiable() {
        ValidationResult r = ValidationResult.failure(
            ValidationStatus.DUPLICATE, List.of("err1", "err2"));
        assertThrows(UnsupportedOperationException.class,
            () -> r.getErrors().add("new error"));
    }

    // ─── equals and hashCode ──────────────────────────────────────────────────

    @Test
    @DisplayName("two success results are equal")
    void successResultsAreEqual() {
        assertEquals(ValidationResult.success(), ValidationResult.success());
        assertEquals(ValidationResult.success().hashCode(),
            ValidationResult.success().hashCode());
    }

    @Test
    @DisplayName("two failures with same status and message are equal")
    void failureResultEquality() {
        ValidationResult r1 = ValidationResult.failure(ValidationStatus.DUPLICATE, "dup");
        ValidationResult r2 = ValidationResult.failure(ValidationStatus.DUPLICATE, "dup");
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    @DisplayName("success and failure are not equal")
    void successAndFailureNotEqual() {
        ValidationResult success = ValidationResult.success();
        ValidationResult failure = ValidationResult.failure(
            ValidationStatus.INVALID_SIGNATURE, "bad");
        assertFalse(success.equals(failure));
    }

    // ─── toString ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("success toString contains VALID")
    void successToString() {
        assertTrue(ValidationResult.success().toString().contains("VALID"));
    }

    @Test
    @DisplayName("failure toString contains status and errors")
    void failureToString() {
        ValidationResult r = ValidationResult.failure(
            ValidationStatus.INSUFFICIENT_FUNDS, "Not enough");
        String str = r.toString();
        assertTrue(str.contains("INSUFFICIENT_FUNDS"));
        assertTrue(str.contains("Not enough"));
    }

    // ─── ValidationStatus enum ────────────────────────────────────────────────

    @Test
    @DisplayName("ValidationStatus has exactly 5 values")
    void validationStatusValues() {
        assertEquals(5, ValidationStatus.values().length);
    }

    @Test
    @DisplayName("ValidationStatus.valueOf works for all enum constants")
    void validationStatusValueOf() {
        assertEquals(ValidationStatus.VALID,
            ValidationStatus.valueOf("VALID"));
        assertEquals(ValidationStatus.INVALID_SIGNATURE,
            ValidationStatus.valueOf("INVALID_SIGNATURE"));
        assertEquals(ValidationStatus.INSUFFICIENT_FUNDS,
            ValidationStatus.valueOf("INSUFFICIENT_FUNDS"));
        assertEquals(ValidationStatus.DUPLICATE,
            ValidationStatus.valueOf("DUPLICATE"));
        assertEquals(ValidationStatus.CUSTOM_REJECTION,
            ValidationStatus.valueOf("CUSTOM_REJECTION"));
    }
}
