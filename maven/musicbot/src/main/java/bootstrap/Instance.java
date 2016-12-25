package bootstrap;

import audio.StreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.*;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.RateLimitException;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import static util.PropertiesUtil.getInteger;

public class Instance {

    private static final Logger log = LoggerFactory.getLogger(Instance.class);

    private volatile IDiscordClient client;
    private final Properties properties;
    private final CountDownLatch exitLatch = new CountDownLatch(1);

    public Instance(Properties properties) {
        this.properties = properties;
    }

    private ClientBuilder newClientBuilder() {
        ClientBuilder builder = new ClientBuilder()
            .withToken(properties.getProperty("token"))
            .withPingTimeout(getInteger(properties, "max-missed-pings", 50))
            .setMaxReconnectAttempts(getInteger(properties, "max-reconnect-attempts", 10));
        return builder;
    }

    public void login() throws DiscordException, InterruptedException, RateLimitException {
        log.debug("Logging in to Discord");
        if (client != null) {
            if (client.getShards().isEmpty()) {
                client.login();
            }
        } else {
            client = newClientBuilder().login();
            log.debug("Registering Discord event listeners");
            client.getDispatcher().registerListener(this);
            client.getDispatcher().registerListener(new StreamService());
            client.getDispatcher().registerListener(new ProfileListener());
        }
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        log.info("*** Discord bot armed ***");
    }

    @EventSubscriber
    public void onReconnectSuccess(ReconnectSuccessEvent event) {
        log.info("*** Discord bot reconnect succeeded ***");
    }

    @EventSubscriber
    public void onReconnectFailure(ReconnectFailureEvent event) {
        log.warn("*** Discord bot reconnect failed after {} attempt{} ***", event.getCurAttempt() + 1, event.getCurAttempt() + 1 == 1 ? "" : "s");
    }

    @EventSubscriber
    public void onDisconnect(DisconnectedEvent event) {
        log.warn("*** Discord bot disconnected due to {} ***", event.getReason());
    }
    @EventSubscriber
    public void onMessage(MessageReceivedEvent e) {
        IMessage message = e.getMessage();
        String content = message.getContent().toLowerCase();
        if (content.equals("!exit")) {
            terminate();
        }
    }

    public void terminate() {
        try {
            client.logout();
        } catch (DiscordException e) {
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
