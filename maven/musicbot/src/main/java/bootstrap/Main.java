package bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.util.DiscordException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String CONFIG_FILE = "bot.properties";

    public static void main(String[] args) {
        Properties properties = new Properties();
        try {
            properties.load(Files.newInputStream(Paths.get(CONFIG_FILE)));
        } catch (IOException e) {
            log.warn("Could not load properties from file: {}", e.toString());
        }
        if (args.length == 0 && !properties.containsKey("token")) {
            throw new IllegalArgumentException("Please enter token as argument");
        } else {
            properties.setProperty("token", args[0]);
        }
        try {
            Instance bot = new Instance(properties);
            bot.login();
            bot.getExitLatch().await();
        } catch (DiscordException e) {
            log.warn("Could not start Discord bot", e);
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for exit", e);
        }
    }
}
