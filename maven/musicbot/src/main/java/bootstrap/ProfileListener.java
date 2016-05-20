package bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.EventSubscriber;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.HTTP429Exception;
import sx.blah.discord.util.Image;

import static util.DiscordUtil.processCommand;

public class ProfileListener {

    private static final Logger log = LoggerFactory.getLogger(ProfileListener.class);

    @EventSubscriber
    public void onMessage(MessageReceivedEvent e) {
        IMessage message = e.getMessage();
        String content = message.getContent();
        if (content.startsWith("!avatar")) {
            processCommand(() -> avatarCommand(e));
        }
    }

    private void avatarCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        if (message.getContent().split(" ").length > 1) {
            String url = message.getContent().split(" ")[1];
            changeAvatar(event.getClient(), Image.forUrl(url.substring(url.lastIndexOf('.')), url));
        } else {
            changeAvatar(event.getClient(), Image.defaultAvatar());
        }
    }

    private void changeAvatar(IDiscordClient client, Image image) {
        try {
            client.changeAvatar(image);
        } catch (DiscordException | HTTP429Exception e) {
            log.warn("Could not change avatar", e);
        }
    }
}
