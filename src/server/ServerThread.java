package server;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A thread to handle a client
 */
public class ServerThread implements Runnable {

    final Socket socket;
    private final Path outputDir;

    ServerThread(Socket socket, Path outputDir) {
        this.socket = socket;
        this.outputDir = outputDir;
    }

    /**
     * reads incoming file data from a single client
     * only handles one client at a time (sequentially)
     */
    @Override
    public void run() {
        try (this.socket; DataInputStream in = new DataInputStream(
                new BufferedInputStream(this.socket.getInputStream()))) {
            while (in.available() != -1) {

                // Get the original checksum
                String originalChecksum = in.readUTF();
                String relPath = in.readUTF();

                // empty string means client is done sending
                if (relPath.isEmpty()) break;

                long size = in.readLong();

                saveFile(in, relPath, size, originalChecksum);
            }

            System.out.println("[Server] Client finished.");
        } catch (EOFException eof) {
            System.out.println("[Server] Client disconnected normally.");
        } catch (SocketException se) {
            System.out.println("[Server] Connection reset by client.");
        } catch (Exception e) {
            System.out.println("[Server] Unexpected error: " + e);
        }
}

    /**
     * Saves one compressed file from the client, verifies checksum,
     * decompresses it, then writes the original file to disk.
     *
     * @param in        input stream from the client
     * @param relPath   relative filename
     * @param size      compressed size (in bytes)
     * @param originalChecksum expected checksum of compressed data
     */
    private void saveFile(DataInputStream in, String relPath, long size, String originalChecksum) throws IOException {

        // Resolve target safely
        Path target = outputDir.resolve(relPath).normalize();

        if (!target.startsWith(outputDir)) {
            throw new IOException("Blocked unsafe path: " + relPath);
        }

        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        // --- 1) Read compressed bytes ---
        byte[] compressedData = new byte[(int) size];

        int totalRead = 0;
        while (totalRead < size) {
            int read = in.read(compressedData, totalRead, (int) (size - totalRead));
            if (read == -1) {
                throw new EOFException("Unexpected end of stream (reading compressed data)");
            }
            totalRead += read;
        }

        // --- 2) Verify checksum of compressed data ---
        String computedChecksum;
        try {
            computedChecksum = calculateChecksum(compressedData);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        if (!computedChecksum.equals(originalChecksum)) {
            System.out.println("❌ Checksum mismatch! Compressed file corrupted.");
            System.out.println("Expected: " + originalChecksum);
            System.out.println("Received: " + computedChecksum);
            return;
        }

        System.out.println("✅ Compressed checksum verified for: " + relPath);

        // --- 3) Decompress ---
        byte[] decompressedData = decompress(compressedData);

        // --- 4) Save decompressed output ---
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            out.write(decompressedData);
        }

        System.out.printf("[Server] Saved %s (%d bytes decompressed)%n", target, decompressedData.length);
    }

    private byte[] decompress(byte[] compressed) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = gzip.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
        }

        return baos.toByteArray();
    }

    private String calculateChecksum(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);

        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

}
