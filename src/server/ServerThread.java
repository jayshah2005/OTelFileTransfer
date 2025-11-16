package server;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;

import telemetry.Metrics;

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
        try (
                Socket s = this.socket;   // auto-close socket
                DataInputStream in = new DataInputStream(
                        new BufferedInputStream(s.getInputStream()))
        ) {
            while (true) {
                // --- Read metadata ---
                String checksum = in.readUTF();
                String relPath  = in.readUTF();

                // Empty path = client finished
                if (relPath.isEmpty()) {
                    System.out.println("[Server] Client sent termination signal.");
                    break;
                }

                long size = in.readLong();

                // --- Process file ---
                saveFile(in, relPath, size, checksum);
            }

            System.out.println("[Server] Client finished.");

        } catch (EOFException eof) {
            System.out.println("[Server] Client disconnected normally.");
        } catch (SocketException se) {
            System.out.println("[Server] Connection reset by client.");
        } catch (Exception e) {
            System.out.println("[Server] Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Saves one compressed file from the client, verifies checksum,
     * decompresses it, then writes the original file to disk.
     *
     * @param in        input stream from the client
     * @param relPath   relative filename
     * @param size      compressed size (in bytes)
     * @param expectedChecksum expected checksum of compressed data
     */
    private void saveFile(DataInputStream in, String relPath, long size, String expectedChecksum) throws IOException {

        // 0) Resolve and validate output path
        Path target = outputDir.resolve(relPath).normalize();

        if (!target.startsWith(outputDir)) {
            throw new IOException("Blocked unsafe path: " + relPath);
        }

        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        // 1) Read compressed file data
        byte[] compressedData = new byte[(int) size];
        int totalRead = 0;

        while (totalRead < size) {
            int read = in.read(compressedData, totalRead, (int) (size - totalRead));
            if (read == -1) {
                throw new EOFException("Unexpected end of stream while reading compressed data.");
            }
            totalRead += read;
        }

        // 2) Verify checksum
        String actualChecksum = calculateChecksum(compressedData);

        if (!actualChecksum.equals(expectedChecksum)) {
            System.out.println("❌ Checksum mismatch! Compressed file may be corrupted.");
            System.out.println("   Expected: " + expectedChecksum);
            System.out.println("   Actual:   " + actualChecksum);
            return;
        }

        System.out.println("✅ Checksum verified for: " + relPath);

        // 3) Decompress
        byte[] decompressedData = decompress(compressedData);

        // 4) Write decompressed data to disk
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            out.write(decompressedData);
        }

        System.out.printf("[Server] Saved %s (%d bytes decompressed)%n", relPath, decompressedData.length);

        // Metrics: server successfully received a file
        Metrics.filesReceived.add(1);
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

    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);

            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
