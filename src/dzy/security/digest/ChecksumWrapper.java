package dzy.security.digest;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;

public abstract class ChecksumWrapper extends MessageDigest {
    private final Checksum checksum;

    private ChecksumWrapper(String algorithm, Checksum checksum) {
        super(algorithm);
        this.checksum = checksum;
    }

    @Override
    protected int engineGetDigestLength() {
        return 4;
    }

    @Override
    protected void engineUpdate(byte input) {
        checksum.update(input);
    }

    @Override
    protected void engineUpdate(byte[] input, int offset, int len) {
        checksum.update(input, offset, len);
    }

    @Override
    protected byte[] engineDigest() {
        return ByteBuffer.allocate(4).putInt((int) checksum.getValue()).array();
    }

    @Override
    protected void engineReset() {
        checksum.reset();
    }

    public static final class Adler32Wrapper extends ChecksumWrapper {
        public Adler32Wrapper() {
            super("Adler32", new Adler32());
        }
    }

    public static final class CRC32Wrapper extends ChecksumWrapper {
        public CRC32Wrapper() {
            super("CRC32", new CRC32());
        }
    }

    public static final class CRC32CWrapper extends ChecksumWrapper {
        public CRC32CWrapper() {
            super("CRC32C", new CRC32C());
        }
    }
}
