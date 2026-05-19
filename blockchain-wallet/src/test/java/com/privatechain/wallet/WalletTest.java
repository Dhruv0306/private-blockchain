package com.privatechain.wallet;

import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.BlockHeader;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.core.spi.ConsensusEngine;
import com.privatechain.crypto.KeyPairGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Wallet}, covering key management, address derivation,
 * transaction signing, and balance computation.
 */
@DisplayName("Wallet")
class WalletTest {

    /**
     * Test helper: creates a dummy transaction with given sender and receiver.
     */
    private static Transaction createTx(String sender, String receiver, long amount) {
        return new Transaction(
            UUID.randomUUID(),
            sender,
            receiver,
            BigDecimal.valueOf(amount),
            Instant.now(),
            null) {
        };
    }

    @Test
    void testWalletCreationFromKeyPair() {
        // Act
        var keyPair = KeyPairGenerator.generateECKeyPair();
        Wallet wallet = new Wallet(keyPair);

        // Assert
        assertNotNull(wallet.getAddress(), "Address should not be null");
        assertFalse(wallet.getAddress().isBlank(), "Address should not be blank");
    }

    @Test
    void testWalletAddressDerivation() {
        // Arrange
        var keyPair1 = KeyPairGenerator.generateECKeyPair();
        var keyPair2 = KeyPairGenerator.generateECKeyPair();

        // Act
        Wallet wallet1 = new Wallet(keyPair1);
        Wallet wallet2 = new Wallet(keyPair2);

        // Assert
        assertNotEquals(wallet1.getAddress(), wallet2.getAddress(),
            "Different wallets should have different addresses");
    }

    @Test
    void testSignTransaction() {
        // Arrange
        var keyPair = KeyPairGenerator.generateECKeyPair();
        Wallet wallet = new Wallet(keyPair);
        Transaction tx = createTx(wallet.getAddress(), "Bob", 100);

        // Assert precondition
        assertFalse(tx.isSigned(), "Transaction should not be signed initially");

        // Act
        Transaction signed = wallet.sign(tx);

        // Assert
        assertTrue(signed.isSigned(), "Transaction should be signed after calling wallet.sign()");
        assertNotNull(signed.getSignature(), "Signature should not be null");
        assertTrue(signed.getSignature().length > 0, "Signature should not be empty");
        assertSame(signed, tx, "sign() should return the same transaction object");
    }

    @Test
    void testSignTransactionNullCheck() {
        // Arrange
        var keyPair = KeyPairGenerator.generateECKeyPair();
        Wallet wallet = new Wallet(keyPair);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> wallet.sign(null),
            "Should throw NPE on null transaction");
    }

    @Test
    void testGetBalance_EmptyBlockchain() {
        // Arrange
        var keyPair = KeyPairGenerator.generateECKeyPair();
        Wallet wallet = new Wallet(keyPair);

        // Create a minimal blockchain mock (we just need the getChain() method)
        BlockchainStorage storage = new SimpleMemoryStorage();
        BlockchainEventBus eventBus = new BlockchainEventBus();
        ConsensusEngine consensusEngine = new MinimalConsensusEngine();
        Blockchain blockchain = new Blockchain(consensusEngine, storage, eventBus);

        // Act
        BigDecimal balance = wallet.getBalance(blockchain);

        // Assert
        assertEquals(BigDecimal.ZERO, balance, "Balance of empty blockchain should be 0");
    }

    @Test
    void testGetBalance_WithTransactions() {
        // Arrange
        var keyPair1 = KeyPairGenerator.generateECKeyPair();
        Wallet wallet1 = new Wallet(keyPair1);

        var keyPair2 = KeyPairGenerator.generateECKeyPair();
        Wallet wallet2 = new Wallet(keyPair2);

        // Create blockchain with one block
        BlockchainStorage storage = new SimpleMemoryStorage();
        BlockchainEventBus eventBus = new BlockchainEventBus();
        ConsensusEngine consensusEngine = new MinimalConsensusEngine();
        Blockchain blockchain = new Blockchain(consensusEngine, storage, eventBus);

        // Create genesis block with transactions
        Transaction txIncoming = createTx(wallet2.getAddress(), wallet1.getAddress(), 500);
        Transaction txOutgoing = createTx(wallet1.getAddress(), wallet2.getAddress(), 100);

        BlockHeader header = BlockHeader.builder()
            .nonce(0L)
            .merkleRoot("a".repeat(64))
            .build();

        Block genesisBlock = Block.builder()
            .index(0)
            .previousHash(Block.GENESIS_PREVIOUS_HASH)
            .header(header)
            .transactions(List.of(txIncoming, txOutgoing))
            .build();

        blockchain.addBlock(genesisBlock);

        // Act
        BigDecimal balance = wallet1.getBalance(blockchain);

        // Assert
        // wallet1 receives 500 and sends 100, so balance should be 400
        assertEquals(BigDecimal.valueOf(400), balance, "Balance should be incoming - outgoing");
    }

    @Test
    void testWalletEquality() {
        // Arrange
        var keyPair = KeyPairGenerator.generateECKeyPair();
        Wallet wallet1 = new Wallet(keyPair);
        Wallet wallet2 = new Wallet(keyPair); // Same key pair

        // Act & Assert
        assertEquals(wallet1, wallet2, "Wallets with same key pair should be equal");
        assertEquals(wallet1.hashCode(), wallet2.hashCode(),
            "Equal wallets should have the same hash code");
    }

    @Test
    void testWalletInequality() {
        // Arrange
        var keyPair1 = KeyPairGenerator.generateECKeyPair();
        var keyPair2 = KeyPairGenerator.generateECKeyPair();

        Wallet wallet1 = new Wallet(keyPair1);
        Wallet wallet2 = new Wallet(keyPair2);

        // Act & Assert
        assertNotEquals(wallet1, wallet2, "Wallets with different key pairs should not be equal");
    }

    @Test
    void testToString() {
        // Arrange
        var keyPair = KeyPairGenerator.generateECKeyPair();
        Wallet wallet = new Wallet(keyPair);

        // Act
        String str = wallet.toString();

        // Assert
        assertNotNull(str, "toString() should not be null");
        assertTrue(str.contains("Wallet"), "toString() should contain class name");
        assertTrue(str.contains(wallet.getAddress()), "toString() should contain address");
        assertTrue(str.contains("REDACTED"), "Wallet.toString() should delegate to keyPair which redacts the private key");
    }

    /**
     * Minimal consensus engine for testing purposes.
     */
    static class MinimalConsensusEngine implements ConsensusEngine {
        @Override
        public boolean validateBlock(com.privatechain.core.model.Block block,
                                     com.privatechain.core.builder.Blockchain chain) {
            return true;
        }

        @Override
        public com.privatechain.core.model.Block mineBlock(List<Transaction> transactions,
                                                           com.privatechain.core.model.Block previousBlock) {
            throw new UnsupportedOperationException("Not needed for tests");
        }

        @Override
        public String engineName() {
            return "Minimal";
        }
    }

    /**
     * Simple in-memory storage implementation for testing.
     */
    static class SimpleMemoryStorage implements BlockchainStorage {
        private final TreeMap<Integer, Block> blocks = new TreeMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        @Override
        public void saveBlock(Block block) {
            lock.writeLock().lock();
            try {
                blocks.put(block.getIndex(), block);
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public Block loadBlock(int index) {
            lock.readLock().lock();
            try {
                Block block = blocks.get(index);
                if (block == null) {
                    throw new NoSuchElementException("Block not found at index: " + index);
                }
                return block;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public Optional<Block> loadBlockByHash(String hash) {
            lock.readLock().lock();
            try {
                return blocks.values().stream()
                    .filter(b -> b.getHash().equals(hash))
                    .findFirst();
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public List<Block> loadAll() {
            lock.readLock().lock();
            try {
                return new ArrayList<>(blocks.values());
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public boolean exists(String hash) {
            return loadBlockByHash(hash).isPresent();
        }

        @Override
        public int chainHeight() {
            lock.readLock().lock();
            try {
                return blocks.size();
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void deleteAll() {
            lock.writeLock().lock();
            try {
                blocks.clear();
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
}

