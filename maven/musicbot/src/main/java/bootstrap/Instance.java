package bootstrap;

import audio.StreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.DiscordDisconnectedEvent;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.RateLimitException;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static util.PropertiesUtil.getBoolean;
import static util.PropertiesUtil.getInteger;
import static util.PropertiesUtil.getLong;

public class Instance {

    private static final Logger log = LoggerFactory.getLogger(Instance.class);

    private volatile IDiscordClient client;
    private final Properties properties;
    private final AtomicBoolean reconnect = new AtomicBoolean(true);
    private final CountDownLatch exitLatch = new CountDownLatch(1);

    public Instance(Properties properties) {
        this.properties = properties;
    }

    private ClientBuilder newClientBuilder() {
        ClientBuilder builder = new ClientBuilder()
            .withToken(properties.getProperty("token"))
            .withPingTimeout(getInteger(properties, "max-missed-pings", 50))
            .withTimeout(getLong(properties, "timeout", 30000));
        if (getBoolean(properties, "reconnect", true)) {
            builder.withReconnects();
        }
        return builder;
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
            client.getDispatcher().registerListener(new ProfileListener());
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
        } catch (RateLimitException | DiscordException e) {
            log.warn("Logout failed", e);
        }
        exitLatch.countDown();
    }

    public Properties getProperties() {
        return properties;
    }

    public CountDownLatch getExitLatch() {
        return exitLatch;
    }
}
