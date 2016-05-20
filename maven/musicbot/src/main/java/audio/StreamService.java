package audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.EventSubscriber;
import sx.blah.discord.handle.AudioChannel;
import sx.blah.discord.handle.impl.events.*;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.IVoiceChannel;
import sx.blah.discord.util.DiscordException;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static util.DiscordUtil.*;

public class StreamService {

    private static final Logger log = LoggerFactory.getLogger(StreamService.class);
    private static final Pattern YOUTUBE_URL = Pattern.compile("(?:https?://)?(?:(?:(?:www\\.?)?youtube\\.com(?:/(?:(?:watch\\?.*?(v=[^&\\s]+).*)|(?:v(/.*))|(channel/.+)|(?:user/(.+))|(?:results\\?(search_query=.+))))?)|(?:youtu\\.be(/.*)?))");

    @EventSubscriber
    public void onMessage(MessageReceivedEvent e) {
        IMessage message = e.getMessage();
        String content = message.getContent();
        if (content.startsWith("!queue ")) {
            processCommand(() -> queueCommand(e));
        } else if (content.equals("!skip")) {
            processCommand(() -> skipCommand(e));
        } else if (content.startsWith("!volume ")) {
            processCommand(() -> volumeCommand(e));
        }
    }

    private void volumeCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        String content = message.getContent();
        IChannel channel = message.getChannel();
        if (message.getChannel().isPrivate()) {
            return;
        }
        try {
            int volume = Math.max(0, Math.min(100, parseOrDefault(content.split(" ", 2)[1], 20)));
            AudioChannel audioChannel = message.getGuild().getAudioChannel();
            log.debug("Setting volume to {}% ({})", volume, volume / 100f);
            audioChannel.setVolume(volume / 100f);
            sendMessage(channel, ":ok_hand:");
        } catch (DiscordException e) {
            log.warn("Could not get audio channel", e);
            sendMessage(channel, "Could not get audio channel for this server");
        }
    }

    private void skipCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        if (!message.getChannel().isPrivate()) {
            try {
                message.getGuild().getAudioChannel().skip();
            } catch (DiscordException e) {
                log.warn("Could not get audio channel", e);
            }
        }
    }

    private void queueCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        String content = message.getContent();
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            sendMessage(channel, "This command does not work with private messages");
            return;
        }
        String url = content.split(" ", 2)[1];
        if (url.isEmpty()) {
            sendMessage(channel, "You have to enter a YouTube URL");
            return;
        }
        Optional<IVoiceChannel> voiceChannel = message.getAuthor().getVoiceChannel();
        if (voiceChannel.isPresent() && !voiceChannel.get().isConnected()) {
            voiceChannel.get().join();
        }
        try {
            AudioChannel audioChannel = message.getGuild().getAudioChannel();
            Optional<String> id = extractVideoId(url);
            if (id.isPresent()) {
                log.debug("Preparing to queue video ID: {}", id.get());
                if (queueFromYouTube(audioChannel, id.get())) {
                    IUser user = message.getAuthor();
                    sendMessage(channel, user.getName() + "#" + user.getDiscriminator() + " added " + id.get() + " to the playlist");
                    deleteMessage(message, 1, TimeUnit.SECONDS);
                }
            } else {
                log.debug("Could not extract valid ID from URL: {}", url);
                sendMessage(channel, "Nothing to queue");
                deleteMessage(message, 1, TimeUnit.SECONDS);
            }
        } catch (DiscordException e) {
            log.warn("Could not get audio channel", e);
            sendMessage(channel, "Could not get the audio channel for this server");
        }
    }

    private Optional<String> extractVideoId(String url) {
        Matcher matcher = YOUTUBE_URL.matcher(url);
        if (matcher.find()) {
            String group1 = matcher.group(1);
            String group2 = matcher.group(2);
            String group6 = matcher.group(6);
            if (group1 != null && !group1.isEmpty()) {
                return Optional.of(group1.substring(2)); // strip "v="
            } else if (group2 != null && !group2.isEmpty() && !group2.substring(1).isEmpty()) {
                return Optional.of(group2.substring(1)); // strip "/"
            } else if (group6 != null && !group6.isEmpty() && !group6.substring(1).isEmpty()) {
                return Optional.of(group6.substring(1)); // strip "/"
            }
        }
        return Optional.empty();
    }

    private boolean queueFromYouTube(final AudioChannel audioChannel, final String id) {
        String name = System.getProperty("os.name").contains("Windows") ? "youtube-dl.exe" : "youtube-dl";
        ProcessBuilder builder = new ProcessBuilder(name, "-q", "-f", "worstaudio",
            "--exec", "ffmpeg -hide_banner -nostats -loglevel panic -y -i {} -vn -q:a 5 -f mp3 pipe:1", "-o",
            "%(id)s", id);
        try {
            Process process = builder.start();
            try {
                audioChannel.queue(AudioSystem.getAudioInputStream(process.getInputStream()));
                return true;
            } catch (UnsupportedAudioFileException e) {
                log.warn("Could not queue audio", e);
                process.destroyForcibly();
            }
        } catch (IOException e) {
            log.warn("Could not start process", e);
        }
        return false;
    }

    @EventSubscriber
    public void onAudioPlay(AudioPlayEvent event) {
        String source = event.getFileSource().map(File::toString)
            .orElseGet(() -> event.getUrlSource().map(URL::toString).orElse(""));
        AudioInputStream stream = event.getStream();
        log.debug("[Play] ({}) {}", hex(stream.hashCode()), source);
    }

    @EventSubscriber
    public void onAudioStop(AudioStopEvent event) {
        String source = event.getFileSource().map(File::toString)
            .orElseGet(() -> event.getUrlSource().map(URL::toString).orElse(""));
        AudioInputStream stream = event.getStream();
        log.debug("[Stop] ({}) {}", hex(stream.hashCode()), source);
    }

    @EventSubscriber
    public void onAudioEnqueue(AudioQueuedEvent event) {
        String source = event.getFileSource().map(File::toString)
            .orElseGet(() -> event.getUrlSource().map(URL::toString).orElse(""));
        AudioInputStream stream = event.getStream();
        log.debug("[Enqueue] ({}) {}", hex(stream.hashCode()), source);
    }

    @EventSubscriber
    public void onAudioDequeue(AudioUnqueuedEvent event) {
        String source = event.getFileSource().map(File::toString)
            .orElseGet(() -> event.getUrlSource().map(URL::toString).orElse(""));
        AudioInputStream stream = event.getStream();
        log.debug("[Dequeue] ({}) {}", hex(stream.hashCode()), source);
        try {
            stream.close();
            log.debug("Stream {} was closed", hex(stream.hashCode()));
        } catch (IOException e) {
            log.warn("Could not close audio stream", e);
        }
    }

    private String hex(int number) {
        return Integer.toHexString(number);
    }

    private int parseOrDefault(String input, int defaultValue) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            log.warn("Attempted to parse non-numeric value: {}", e.toString());
            return defaultValue;
        }
    }
}