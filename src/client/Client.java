package client;

import Configs.Config;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Class that manages the GUI client
 */
public class Client implements Runnable {

    private final int port;
    private final String host;
    private final Path file;

    Client(int port, String host, Path file) {
        this.port = port;
        this.host = host;
        this.file = file;
    }

    /**
     * entry point for ruuning the client logic
     * - collect all files
     * - open a socket to the server
     * - send each file (path + size + bytes)
     * - send empty path "" to signal completion
     */
    public void run(){
        System.out.println("Client starting...");
        System.out.println("Connecting to " + host + ":" + port);

        try (Socket socket = new Socket(host, port);
        DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(socket.getOutputStream()))) {
            sendSingleFile(file, out);

            // send empty path to tell the server we are done
            out.writeUTF("");
            out.flush();

            System.out.println(file.getFileName() + " sent. Closing connection.");
        } catch (IOException e){
            System.out.println("Client error. Failed to send file: " + file.getFileName() + "\n" + e.getMessage());
        }
    }

    /**
     *  sends a single file to the server following the protocol:
     *  1) UTF-8 path (relative to inputDir, using '/'
     *  2) long size
     *  3) file bytes
     */
    private void sendSingleFile(Path file, DataOutputStream out) throws IOException {
        String filename = file.getFileName().toString();

        System.out.println("Compressing & sending file: " + filename);

        // 1. Read + compress file into byte[]
        byte[] compressedBytes = compressFile(file);
        long compressedSize = compressedBytes.length;

        // 2. Calculate checksum **after compression**
        String checksum;
        try {
            checksum = calculateChecksum(compressedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        // --- SEND METADATA ---
        out.writeUTF(checksum);          // compressed checksum
        out.writeUTF(filename);          // original filename
        out.writeLong(compressedSize);   // compressed size

        // --- SEND COMPRESSED BYTES IN CHUNKS ---
        long remaining = compressedSize;
        byte[] buffer = new byte[8192];
        int offset = 0;

        while (remaining > 0) {
            int chunk = (int) Math.min(buffer.length, remaining);
            System.arraycopy(compressedBytes, offset, buffer, 0, chunk);

            out.write(buffer, 0, chunk);

            offset += chunk;
            remaining -= chunk;
        }

        out.flush();
        System.out.println("Finished sending compressed: " + filename +
                " (" + compressedSize + " bytes)");
    }

    /**
     * Compress a given file
     * @param path the path where the file is located
     */
    private byte[] compressFile(Path path) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (GZIPOutputStream gzip = new GZIPOutputStream(baos);
             InputStream in = new BufferedInputStream(Files.newInputStream(path))) {

            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1) {
                gzip.write(buffer, 0, read);
            }
        }

        return baos.toByteArray();
    }

    /**
     * Calculate checksum for a given array of bytes
     */
    private String calculateChecksum(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);

        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}