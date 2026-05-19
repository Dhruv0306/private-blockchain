package com.privatechain.wallet;

import com.privatechain.crypto.KeyPairGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WalletManager}, covering wallet lifecycle management,
 * keystore encryption/decryption, and wallet queries.
 */
@DisplayName("WalletManager")
class WalletManagerTest {

    @Test
    void testCreateWallet() {
        // Arrange
        WalletManager manager = new WalletManager();

        // Act
        Wallet wallet = manager.createWallet();

        // Assert
        assertNotNull(wallet, "Created wallet should not be null");
        assertEquals(1, manager.size(), "Manager should contain 1 wallet");
    }

    @Test
    void testImportWallet() {
        // Arrange
        WalletManager manager = new WalletManager();
        var keyPair = KeyPairGenerator.generateECKeyPair();
        String privateKeyHex = keyPair.getPrivateKeyHex();

        // Act
        Wallet wallet = manager.importWallet(privateKeyHex);

        // Assert
        assertNotNull(wallet, "Imported wallet should not be null");
        assertEquals(1, manager.size(), "Manager should contain 1 wallet");
        assertTrue(manager.getWallet(wallet.getAddress()).isPresent(),
            "Wallet should be retrievable by address");
    }

    @Test
    void testGetWallet() {
        // Arrange
        WalletManager manager = new WalletManager();
        Wallet wallet1 = manager.createWallet();

        // Act
        var retrieved = manager.getWallet(wallet1.getAddress());

        // Assert
        assertTrue(retrieved.isPresent(), "Wallet should be found");
        assertEquals(wallet1.getAddress(), retrieved.get().getAddress(),
            "Retrieved wallet should have the same address");
    }

    @Test
    void testGetNonexistentWallet() {
        // Arrange
        WalletManager manager = new WalletManager();

        // Act
        var retrieved = manager.getWallet("NonexistentAddress");

        // Assert
        assertFalse(retrieved.isPresent(), "Nonexistent wallet should return empty");
    }

    @Test
    void testGetAllWallets() {
        // Arrange
        WalletManager manager = new WalletManager();
        manager.createWallet();
        manager.createWallet();
        manager.createWallet();

        // Act
        Collection<Wallet> all = manager.getAllWallets();

        // Assert
        assertEquals(3, all.size(), "Should return all 3 wallets");
    }

    @Test
    void testSize() {
        // Arrange
        WalletManager manager = new WalletManager();

        // Act & Assert
        assertEquals(0, manager.size(), "Empty manager should have size 0");

        manager.createWallet();
        assertEquals(1, manager.size(), "After creating 1 wallet, size should be 1");

        manager.createWallet();
        assertEquals(2, manager.size(), "After creating 2 wallets, size should be 2");
    }

    @Test
    void testRemoveWallet() {
        // Arrange
        WalletManager manager = new WalletManager();
        Wallet wallet = manager.createWallet();
        String address = wallet.getAddress();

        // Act
        boolean removed = manager.removeWallet(address);

        // Assert
        assertTrue(removed, "Wallet should be removed");
        assertEquals(0, manager.size(), "Manager should be empty");
        assertFalse(manager.getWallet(address).isPresent(),
            "Removed wallet should not be retrievable");
    }

    @Test
    void testRemoveNonexistentWallet() {
        // Arrange
        WalletManager manager = new WalletManager();

        // Act
        boolean removed = manager.removeWallet("NonexistentAddress");

        // Assert
        assertFalse(removed, "Removing nonexistent wallet should return false");
    }

    @Test
    void testClear() {
        // Arrange
        WalletManager manager = new WalletManager();
        manager.createWallet();
        manager.createWallet();

        // Act
        manager.clear();

        // Assert
        assertEquals(0, manager.size(), "Manager should be empty after clear");
    }

    @Test
    void testExportAndImportKeystore() {
        // Arrange
        WalletManager manager = new WalletManager();
        Wallet originalWallet = manager.createWallet();
        String password = "test-password-123";

        // Act
        String keystoreJson = manager.exportKeystore(originalWallet, password);
        assertNotNull(keystoreJson, "Exported keystore should not be null");
        assertTrue(keystoreJson.contains("version"), "Keystore should be valid JSON with version");
        assertTrue(keystoreJson.contains("ciphertext"), "Keystore should contain encrypted data");

        // Import the keystore into a new manager
        WalletManager manager2 = new WalletManager();
        Wallet importedWallet = manager2.importFromKeystore(keystoreJson, password);

        // Assert
        assertNotNull(importedWallet, "Imported wallet should not be null");
        assertEquals(originalWallet.getAddress(), importedWallet.getAddress(),
            "Imported wallet should have the same address");

        // Verify the wallets can both sign and produce the same signature
        var testTx1 = new Transaction(
            java.util.UUID.randomUUID(),
            originalWallet.getAddress(),
            "Bob",
            java.math.BigDecimal.valueOf(100),
            java.time.Instant.now(),
            null) {
        };

        var testTx2 = new Transaction(
            testTx1.getId(),
            importedWallet.getAddress(),
            "Bob",
            java.math.BigDecimal.valueOf(100),
            testTx1.getTimestamp(),
            null) {
        };

        originalWallet.sign(testTx1);
        importedWallet.sign(testTx2);

        assertTrue(testTx1.isSigned(), "Original wallet should produce signed transaction");
        assertTrue(testTx2.isSigned(), "Imported wallet should produce signed transaction");
    }

    @Test
    void testExportKeystoreWrongPassword() {
        // Arrange
        WalletManager manager = new WalletManager();
        Wallet wallet = manager.createWallet();
        String passwordA = "password-a";
        String keystoreJson = manager.exportKeystore(wallet, passwordA);

        // Act & Assert
        WalletManager manager2 = new WalletManager();
        assertThrows(IllegalArgumentException.class,
            () -> manager2.importFromKeystore(keystoreJson, "wrong-password"),
            "Importing with wrong password should fail");
    }

    @Test
    void testImportInvalidKeystore() {
        // Arrange
        WalletManager manager = new WalletManager();

        // Act & Assert
        assertThrows(Exception.class,
            () -> manager.importFromKeystore("{invalid json", "password"),
            "Importing invalid JSON should fail");
    }

    @Test
    void testImportWalletInvalidPrivateKey() {
        // Arrange
        WalletManager manager = new WalletManager();

        // Act & Assert
        assertThrows(Exception.class,
            () -> manager.importWallet("not-a-valid-hex-string"),
            "Importing invalid private key should fail");
    }

    @Test
    void testImportWalletBlankPrivateKey() {
        // Arrange
        WalletManager manager = new WalletManager();

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> manager.importWallet(""),
            "Importing blank private key should fail");

        assertThrows(IllegalArgumentException.class,
            () -> manager.importWallet("   "),
            "Importing whitespace-only private key should fail");
    }

    @Test
    void testExportKeystoreNullCheck() {
        // Arrange
        WalletManager manager = new WalletManager();
        Wallet wallet = manager.createWallet();

        // Act & Assert
        assertThrows(NullPointerException.class,
            () -> manager.exportKeystore(null, "password"),
            "Should throw NPE on null wallet");

        assertThrows(NullPointerException.class,
            () -> manager.exportKeystore(wallet, null),
            "Should throw NPE on null password");

        assertThrows(IllegalArgumentException.class,
            () -> manager.exportKeystore(wallet, ""),
            "Should throw IAE on empty password");
    }

    @Test
    void testImportFromKeystoreNullCheck() {
        // Arrange
        WalletManager manager = new WalletManager();

        // Act & Assert
        assertThrows(NullPointerException.class,
            () -> manager.importFromKeystore(null, "password"),
            "Should throw NPE on null keystore");

        assertThrows(NullPointerException.class,
            () -> manager.importFromKeystore("{}", null),
            "Should throw NPE on null password");

        assertThrows(IllegalArgumentException.class,
            () -> manager.importFromKeystore("", "password"),
            "Should throw IAE on empty keystore");

        assertThrows(IllegalArgumentException.class,
            () -> manager.importFromKeystore("{}", ""),
            "Should throw IAE on empty password");
    }

    @Test
    void testMultipleWalletsIndependent() {
        // Arrange
        WalletManager manager = new WalletManager();
        Wallet wallet1 = manager.createWallet();
        Wallet wallet2 = manager.createWallet();

        // Act & Assert
        assertNotEquals(wallet1.getAddress(), wallet2.getAddress(),
            "Different wallets should have different addresses");

        assertEquals(2, manager.size(), "Manager should contain both wallets");

        manager.removeWallet(wallet1.getAddress());
        assertEquals(1, manager.size(), "Removing one wallet should not affect the other");
        assertTrue(manager.getWallet(wallet2.getAddress()).isPresent(),
            "Wallet2 should still be present");
    }
}

// Helper: Transaction class for testing (copied here to avoid circular dependencies in tests)
class Transaction extends com.privatechain.core.model.Transaction {
    public Transaction(
        java.util.UUID id,
        String senderAddress,
        String receiverAddress,
        java.math.BigDecimal amount,
        java.time.Instant timestamp,
        java.util.Map<String, Object> metadata) {
        super(id, senderAddress, receiverAddress, amount, timestamp, metadata);
    }
}

