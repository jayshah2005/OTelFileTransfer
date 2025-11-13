package client;

import Configs.Config;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

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
        // make relative path so server can reconstruct directory structure
        String relative = file.getFileName().toString();

        long size = Files.size(file);

        System.out.println("Sending file: " + relative + " (" + size + " bytes)");

        // 1) send name
        out.writeUTF(relative);

        // 2) send size
        out.writeLong(size);

        // 3) send bytes in chunks
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] buffer = new byte[8192];
            long remaining = size;

            while (remaining > 0) {
                int toRead =  (int) Math.min(buffer.length, remaining);
                int read = in.read(buffer, 0, toRead);
                if (read == -1) {
                    break; // EOF reached unexpectedly, but won't hang
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }

        out.flush();
        System.out.println("Finished sending: " + relative);
    }
}