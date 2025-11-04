package client;

import Configs.Config;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.TreeMap;

/**
 * Class that manages the GUI client
 */
public class Client {

    private final int port;
    private final String host;
    Socket client;

    Client(int port, String host) {
        this.port = port;
        this.host = host;
    }

    void start(){
        try {
            client = new Socket(host, port);
            System.out.println("Connected to host " + host + " at port " + port);
        } catch (IOException e) {
            System.out.println("Client failed to connect: " + e.getMessage());
        }
    }

    /**
     * TODO: Figure out a way to send all files over to the server
     * @return a boolean if files were sent successfully
     */
    Boolean sendFiles(){

        if(!client.isConnected()){
            System.out.println("Client not connected");
            return false;
        }

        DataOutputStream out;
        InputStream in;

        try {
            out = new DataOutputStream();
            in = client.getInputStream();


        } catch (IOException e) {
            System.out.println("Client failed to connect: " + e.getMessage());
        }

        return true;
    }

    public static void main(String[] args) {

        Config config = Config.getInstance();
        int port = config.port;
        String host = config.host;

        Client client = new Client(port, host);
        client.start();
    }
}