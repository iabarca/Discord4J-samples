package bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.EventSubscriber;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.DiscordDisconnectedEvent;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.HTTP429Exception;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class Instance {

    private static final Logger log = LoggerFactory.getLogger(Instance.class);

    private volatile IDiscordClient client;
    private String email;
    private String password;
    private String token;
    private final AtomicBoolean reconnect = new AtomicBoolean(true);

    public Instance(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public Instance(String token) {
        this.token = token;
    }

    public void login() throws DiscordException {
        if (token == null) {
            client = new ClientBuilder().withLogin(email, password).login();
        } else {
            client = new ClientBuilder().withToken(token).login();
        }
        client.getDispatcher().registerListener(this);
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        log.info("*** Discord bot armed ***");
    }

    @EventSubscriber
    public void onDisconnect(DiscordDisconnectedEvent event) {
        CompletableFuture.runAsync(() -> {
            if (reconnect.get()) {
                log.info("Reconnecting bot");
                try {
                    login();
                } catch (DiscordException e) {
                    log.warn("Failed to reconnect bot", e);
                }
            }
        });
    }

    @EventSubscriber
    public void onMessage(MessageReceivedEvent e) {
        log.debug("Got message");
    }

    public void terminate() {
        reconnect.set(false);
        try {
            client.logout();
        } catch (HTTP429Exception | DiscordException e) {
            log.warn("Logout failed", e);
        }
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
