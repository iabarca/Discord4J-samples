package bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.util.DiscordException;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Instance bot;
        if (args.length == 0) {
            throw new IllegalArgumentException("Please enter email and password OR a token as arguments");
        } else if (args.length == 1) {
            bot = new Instance(args[0]);
        } else {
            bot = new Instance(args[0], args[1]);
        }
        try {
            bot.login();
            bot.getExitLatch().await();
        } catch (DiscordException e) {
            log.warn("Could not start Discord bot", e);
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for exit", e);
        }
    }
}
