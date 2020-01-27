package dzy.javafx.app.hash.fxml;

import javafx.concurrent.Task;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;

class FileTask extends Task<String> {
    private final List<MessageDigest> digests = new ArrayList<>();
    private final String filePath;
    private final FileChannel channel;

    FileTask(File file, List<String> algorithms) throws IOException, NoSuchAlgorithmException {
        this.filePath = file.getPath();
        this.channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);

        for (String algorithm : algorithms) {
            digests.add(MessageDigest.getInstance(algorithm));
        }
    }

    @Override
    protected String call() throws Exception {
        long size = channel.size();

        try {
            if (digests.size() == 0) return filePath + "\n";

            updateProgress(0, size);
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 64);
            while (channel.read(buffer) != -1) {
                buffer.flip();
                for (MessageDigest digest : digests) digest.update(buffer.rewind());
                buffer.clear();
                updateProgress(channel.position(), size);
            }

            return isCancelled() ? "" : toString(digests);
        } finally {
            updateProgress(size, size);
            channel.close();
        }
    }

    private String toString(List<MessageDigest> digests) {
        return digests.stream().map(this::toString).collect(joining("\n", filePath + "\n", "\n\n"));
    }

    private String toString(MessageDigest digest) {
        return digest.getAlgorithm() + ": " + toHex(digest.digest());
    }

    private String toHex(byte[] bs) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bs) sb.append(String.format("%02X", b & 0xFF));
        return sb.toString();
    }
}
