package server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.api.GlobalOpenTelemetry;

import telemetry.Metrics;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;

public class ServerThread implements Runnable {

    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("file-server-instrumentation", "1.0.0");

    final Socket socket;
    private final Path outputDir;

    ServerThread(Socket socket, Path outputDir) {
        this.socket = socket;
        this.outputDir = outputDir;
    }

    @Override
    public void run() {
        Span serverRootSpan = tracer.spanBuilder("ServerThread").startSpan();

        try (Scope rootScope = serverRootSpan.makeCurrent();
             Socket s = this.socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()))) {

            while (true) {
                Span receiveSpan = tracer.spanBuilder("receiveAndSaveFile").startSpan();

                try (Scope receiveScope = receiveSpan.makeCurrent()) {
                    String checksum = in.readUTF();
                    String relPath = in.readUTF();

                    if (relPath.isEmpty()) {
                        receiveSpan.addEvent("termination_signal_received");
                        System.out.println("[Server] Client sent termination signal.");
                        break;
                    }

                    long size = in.readLong();
                    receiveSpan.setAttribute("file.name", relPath);
                    receiveSpan.setAttribute("file.compressed.size", size);

                    saveFile(in, relPath, size, checksum);
                } catch (EOFException eof) {
                    receiveSpan.addEvent("client_disconnected_eof");
                    System.out.println("[Server] Client disconnected normally (EOF).");
                    break;
                } catch (Exception e) {
                    receiveSpan.recordException(e);
                    receiveSpan.setStatus(StatusCode.ERROR, "Failed to process file");
                    throw e;
                } finally {
                    receiveSpan.end();
                }
            }

            System.out.println("[Server] Client finished.");

        } catch (SocketException se) {
            System.out.println("[Server] Connection reset by client.");
        } catch (Exception e) {
            System.err.println("[Server] Unexpected error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            serverRootSpan.end();
        }
    }

    private void saveFile(DataInputStream in, String relPath, long size, String expectedChecksum) throws IOException {
        Path target = outputDir.resolve(relPath).normalize();

        if (!target.startsWith(outputDir)) {
            throw new IOException("Blocked unsafe path: " + relPath);
        }

        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        // --- Read compressed bytes ---
        Span readSpan = tracer.spanBuilder("readCompressedBytes").startSpan();
        byte[] compressedData;
        try (Scope scope = readSpan.makeCurrent()) {
            compressedData = new byte[(int) size];
            int totalRead = 0;

            while (totalRead < size) {
                int read = in.read(compressedData, totalRead, (int) (size - totalRead));
                if (read == -1) {
                    throw new EOFException("Unexpected end of stream while reading compressed data.");
                }
                totalRead += read;
            }
        } finally {
            readSpan.end();
        }

        // --- Verify checksum ---
        Span verifySpan = tracer.spanBuilder("verifyChecksum").startSpan();
        try (Scope scope = verifySpan.makeCurrent()) {
            String actualChecksum = calculateChecksum(compressedData);
            verifySpan.setAttribute("expected", expectedChecksum);
            verifySpan.setAttribute("actual", actualChecksum);

            if (!actualChecksum.equals(expectedChecksum)) {
                System.out.println("❌ Checksum mismatch! Compressed file may be corrupted.");
                System.out.println("   Expected: " + expectedChecksum);
                System.out.println("   Actual:   " + actualChecksum);
                return;
            }
        } finally {
            verifySpan.end();
        }

        System.out.println("✅ Checksum verified for: " + relPath);

        // --- Decompress ---
        byte[] decompressedData;
        Span decompressSpan = tracer.spanBuilder("decompress").startSpan();
        try (Scope scope = decompressSpan.makeCurrent()) {
            decompressedData = decompress(compressedData);
            decompressSpan.setAttribute("file.decompressed.size", decompressedData.length);
        } finally {
            decompressSpan.end();
        }

        // --- Write to disk ---
        Span writeSpan = tracer.spanBuilder("writeToDisk").startSpan();
        try (Scope scope = writeSpan.makeCurrent();
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {

            out.write(decompressedData);
        } finally {
            writeSpan.end();
        }

        System.out.printf("[Server] Saved %s (%d bytes decompressed)%n", relPath, decompressedData.length);
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
