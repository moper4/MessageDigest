package dzy.security;

import java.security.Provider;

public class ChecksumProvider extends Provider {
    public ChecksumProvider() {
        super("DZY", "0.1", "crc wrapper");
        put("MessageDigest.Adler32", "dzy.security.digest.ChecksumWrapper$Adler32Wrapper");
        put("MessageDigest.CRC32", "dzy.security.digest.ChecksumWrapper$CRC32Wrapper");
        put("MessageDigest.CRC32C", "dzy.security.digest.ChecksumWrapper$CRC32CWrapper");
    }
}
