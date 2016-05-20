package bootstrap;

import audio.StreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.EventSubscriber;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.DiscordDisconnectedEvent;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.HTTP429Exception;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Instance {

    private static final Logger log = LoggerFactory.getLogger(Instance.class);

    private volatile IDiscordClient client;
    private String email;
    private String password;
    private String token;
    private final AtomicBoolean reconnect = new AtomicBoolean(true);
    private final CountDownLatch exitLatch = new CountDownLatch(1);

    public Instance(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public Instance(String token) {
        this.token = token;
    }

    private ClientBuilder newClientBuilder() {
        if (token == null) {
            return new ClientBuilder().withLogin(email, password);
        } else {
            return new ClientBuilder().withToken(token);
        }
    }

    public void login() throws DiscordException, InterruptedException {
        log.debug("Logging in to Discord");
        if (client != null) {
            client.login();
        } else {
            client = newClientBuilder().login();
            log.debug("Registering Discord event listeners");
            client.getDispatcher().registerListener(this);
            client.getDispatcher().registerListener(new StreamService());
        }
    }

    /**
     * Attempt to login and retry if it fails
     */
    public void tryLogin() {
        log.info("Attempting to connect...");
        CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(5); // 1/5 API rate limit for logging in
                login();
            } catch (DiscordException | InterruptedException e) {
                throw new RuntimeException(e); // rethrow to handle retry
            }
        }).exceptionally(e -> {
            log.error("Could not connect discord bot", e);
            tryLogin();
            return null;
        });
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        log.info("*** Discord bot armed ***");
    }

    @EventSubscriber
    public void onDisconnect(DiscordDisconnectedEvent event) {
        if (reconnect.get() && event.getReason() != DiscordDisconnectedEvent.Reason.RECONNECTING) {
            log.info("Reconnecting bot due to {}", event.getReason().toString());
            tryLogin();
        }
    }

    public void terminate() {
        reconnect.set(false);
        try {
            client.logout();
        } catch (HTTP429Exception | DiscordException e) {
            log.warn("Logout failed", e);
        }
        exitLatch.countDown();
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

    public CountDownLatch getExitLatch() {
        return exitLatch;
    }
}
