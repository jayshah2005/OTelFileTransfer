package Configs;

public class Config {

    private static Config instance = new Config();

    public int port;
    public String host;

    public Config() {
        port = 5050;
        host = "localhost";
    }

    public static Config getInstance() {
        return instance;
    }


}
