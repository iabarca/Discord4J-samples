package audio;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.IVoiceChannel;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.audio.AudioPlayer;
import sx.blah.discord.util.audio.events.*;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static util.DateUtil.formatDuration;
import static util.DateUtil.formatHuman;
import static util.DiscordUtil.*;

public class StreamService {

    private static final Logger log = LoggerFactory.getLogger(StreamService.class);
    private static final Pattern YOUTUBE_URL = Pattern.compile("(?:https?://)?(?:(?:(?:www\\.?)?youtube\\.com(?:/(?:(?:watch\\?.*?(v=[^&\\s]+).*)|(?:v(/.*))|(channel/.+)|(?:user/(.+))|(?:results\\?(search_query=.+))))?)|(?:youtu\\.be(/.*)?))");
    private static final Gson GSON = new Gson();

    @EventSubscriber
    public void onMessage(MessageReceivedEvent e) {
        IMessage message = e.getMessage();
        String content = message.getContent().toLowerCase();
        if (content.startsWith("!q ") || content.startsWith("!queue ")) {
            processCommand(() -> queueCommand(e));
        } else if (content.startsWith("!qx ")) {
            processCommand(() -> queueUrlCommand(e));
        } else if (content.equals("!s") || content.equals("!skip")) {
            processCommand(() -> skipCommand(e));
        } else if (content.startsWith("!v ") || content.startsWith("!volume ")) {
            processCommand(() -> volumeCommand(e));
        } else if (content.equals("!status")) {
            processCommand(() -> statusCommand(e));
        } else if (content.equals("!stop")) {
            processCommand(() -> stopCommand(e));
        } else if (content.equals("!pause")) {
            processCommand(() -> pauseCommand(e, true));
        } else if (content.equals("!resume")) {
            processCommand(() -> pauseCommand(e, false));
        } else if (content.startsWith("!rewindto ")) {
            processCommand(() -> rewindToCommand(e));
        } else if (content.startsWith("!forwardto ")) {
            processCommand(() -> fastForwardToCommand(e));
        } else if (content.startsWith("!rewind ")) {
            processCommand(() -> rewindCommand(e));
        } else if (content.startsWith("!forward ")) {
            processCommand(() -> fastForwardCommand(e));
        } else if (content.equals("!shuffle")) {
            processCommand(() -> shuffleCommand(e));
        } else if (content.equals("!loop")) {
            processCommand(() -> toggleLoopCommand(e));
        }
    }

    private void statusCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            return;
        }
        AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
        AudioPlayer.Track track = player.getCurrentTrack();
        if (track != null) {
            String source = getSource(track);
            long total = track.getTotalTrackTime();
            int volume = (int) (player.getVolume() * 100);
            StringBuilder response = new StringBuilder();
            response.append("Status: ").append(player.isPaused() ? "**Paused**" : "**Playing**").append("\n");
            if (player.isLooping()) {
                response.append("Looping: ");
            } else {
                response.append("Current: ");
            }
            response.append(source).append(" ")
                .append(prettyDuration(total)).append("\n")
                .append("Playlist: ").append(playlistToString(player)).append("\n")
                .append("Volume: ").append(volume);
            sendMessage(channel, response.toString());
        } else {
            sendMessage(channel, "Player is " + (player.isReady() ? "" : "NOT") + " ready.");
        }
    }

    private String prettyDuration(long millis) {
        if (millis >= 0) {
            return "[" + formatDuration(Duration.ofMillis(millis)) + "]";
        } else {
            return "";
        }
    }

    private void stopCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            return;
        }
        event.getClient().getConnectedVoiceChannels().stream()
            .filter(ch -> ch.getGuild().equals(message.getGuild()))
            .findFirst().ifPresent(IVoiceChannel::leave);
    }

    private void pauseCommand(MessageReceivedEvent event, boolean pause) {
        IMessage message = event.getMessage();
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            return;
        }
        AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
        player.setPaused(pause);
        sendMessage(channel, ":ok_hand:");
    }

    private void rewindToCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            return;
        }
        String content = message.getContent();
        long ms = Math.max(0, parseLong(content.split(" ")[1], 5000));
        String duration = formatHuman(Duration.ofMillis(ms), true);
        AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
        player.getCurrentTrack().rewindTo(ms);
        sendMessage(channel, ":rewind: to " + duration);
    }

    private void fastForwardToCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            return;
        }
        String content = message.getContent();
        long ms = Math.max(0, parseLong(content.split(" ")[1], 5000));
        String duration = formatHuman(Duration.ofMillis(ms), true);
        AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
        player.getCurrentTrack().fastForwardTo(ms);
        sendMessage(channel, ":fast_forward: to " + duration);
    }

    private void rewindCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            return;
        }
        String content = message.getContent();
        long ms = Math.max(0, parseLong(content.split(" ")[1], 5000));
        String duration = formatHuman(Duration.ofMillis(ms), true);
        AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
        player.getCurrentTrack().rewind(ms);
        sendMessage(channel, ":rewind: by " + duration);
    }

    private void fastForwardCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            return;
        }
        String content = message.getContent();
        long ms = Math.max(0, parseLong(content.split(" ")[1], 5000));
        String duration = formatHuman(Duration.ofMillis(ms), true);
        AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
        player.getCurrentTrack().fastForward(ms);
        sendMessage(channel, ":fast_forward: by " + duration);
    }

    private void shuffleCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            return;
        }
        AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
        player.shuffle();
        sendMessage(channel, ":ok_hand:");
    }

    private void toggleLoopCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            return;
        }
        AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
        player.setLoop(!player.isLooping());
        sendMessage(channel, "Loop mode: " + (player.isLooping() ? "ON" : "OFF"));
    }

    private void volumeCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        String content = message.getContent();
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            return;
        }
        int volume = Math.max(0, Math.min(100, parseInteger(content.split(" ", 2)[1], 20)));
        AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
        log.debug("Setting volume to {}% ({})", volume, volume / 100f);
        player.setVolume(volume / 100f);
        sendMessage(channel, ":ok_hand:");
    }

    private void skipCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        if (!message.getChannel().isPrivate()) {
            AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
            player.skip();
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
        String url = content.trim().split(" ", 2)[1].trim();
        if (url.isEmpty()) {
            sendMessage(channel, "You have to enter a YouTube URL");
            return;
        }
        Optional<IVoiceChannel> voiceChannel = message.getAuthor().getConnectedVoiceChannels()
            .stream().filter(v -> message.getGuild().equals(v.getGuild()))
            .findAny();
        if (tryJoin(voiceChannel, message)) {
            AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
            Optional<String> id = extractVideoId(url);
            if (id.isPresent()) {
                log.debug("Preparing to queue video ID: {}", id.get());
                if (queueFromYouTube(player, id.get(), null)) {
                    IUser user = message.getAuthor();
                    Optional<Metadata> metadata = getMetadataFromId(url);
                    if (metadata.isPresent()) {
                        Metadata m = metadata.get();
                        String title = m.getTitle();
                        String duration = formatDuration(Duration.ofSeconds(m.getDuration()));
                        sendMessage(channel, user.getName() + "#" + user.getDiscriminator() + " added **" + title + "** [" + duration + "]");
                    } else {
                        sendMessage(channel, user.getName() + "#" + user.getDiscriminator() + " added `" + id.get() + "` to the playlist");
                    }
                    deleteMessage(message);
                }
            } else {
                log.debug("Could not extract valid ID from URL: {}", url);
                sendMessage(channel, "Nothing to queue, something happened");
                deleteMessage(message);
            }
        }
    }

    private void queueUrlCommand(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        String content = message.getContent();
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            sendMessage(channel, "This command does not work with private messages");
            return;
        }
        String[] args = content.trim().split(" ");
        String url = args[1].trim();
        if (url.isEmpty()) {
            sendMessage(channel, "You have to enter a URL");
            return;
        }
        Map<String, String> variables = new LinkedHashMap<>();
        if (args.length > 2) {
            variables.put("--playlist-start", args[2]);
        }
        if (args.length > 3) {
            variables.put("--playlist-end", args[3]);
        }
        Optional<IVoiceChannel> voiceChannel = message.getAuthor().getConnectedVoiceChannels()
            .stream().filter(v -> !v.isConnected() && message.getGuild().equals(v.getGuild()))
            .findAny();
        if (tryJoin(voiceChannel, message)) {
            AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
            log.debug("Preparing to process URL into queue: {}", url);
            if (queueFromYouTube(player, url, variables)) {
                IUser user = message.getAuthor();
                Optional<Metadata> metadata = getMetadataFromId(url);
                if (metadata.isPresent()) {
                    Metadata m = metadata.get();
                    String title = m.getTitle();
                    String duration = formatDuration(Duration.ofSeconds(m.getDuration()));
                    sendMessage(channel, user.getName() + "#" + user.getDiscriminator() + " added **" + title + "** [" + duration + "]");
                } else {
                    sendMessage(channel, user.getName() + "#" + user.getDiscriminator() + " added <" + url + ">");
                }
                deleteMessage(message);
            }
        }
    }

    private boolean tryJoin(Optional<IVoiceChannel> voiceChannel, IMessage message) {
        if (voiceChannel.isPresent() && !voiceChannel.get().isConnected()) {
            try {
                voiceChannel.get().join();
                return true;
            } catch (MissingPermissionsException e) {
                log.debug("Missing permissions to join voice channel");
                sendMessage(message.getChannel(), "Unable to join voice channel");
                deleteMessage(message);
                return false;
            }
        } else {
            return true;
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

    private boolean queueFromYouTube(AudioPlayer audioPlayer, String id, Map<String, String> variables) {
        String name = System.getProperty("os.name").contains("Windows") ? "youtube-dl.exe" : "youtube-dl";
        ProcessBuilder builder = new ProcessBuilder(name, "--write-info-json", "-f", "worstaudio",
            "--exec", "ffmpeg -hide_banner -nostats -loglevel panic -y -i {} -vn -q:a 5 -f mp3 pipe:1", "-o",
            "%(id)s.%(ext)s");
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                builder.command().add(entry.getKey());
                builder.command().add(entry.getValue());
            }
        }
        builder.command().add("--");
        builder.command().add(id);
        try {
            Process process = builder.start();
            try {
                CompletableFuture.runAsync(() -> logStream(process.getErrorStream()));
                AudioPlayer.Track track = audioPlayer.queue(AudioSystem.getAudioInputStream(process.getInputStream()));
                track.getMetadata().put("url", id);
                Optional<Metadata> metadata = getMetadataFromId(id);
                metadata.ifPresent(m -> {
                    String title = m.getTitle();
                    String duration = formatDuration(Duration.ofSeconds(m.getDuration()));
                    track.getMetadata().put("title", title);
                    track.getMetadata().put("duration", duration);
                    track.getMetadata().put("webpage_url", m.getWebpageUrl());
                    log.info("Queued [{}] \"{}\" ({})", id, title, duration);
                });
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

    private Optional<Metadata> getMetadataFromId(String id) {
        try (Reader reader = Files.newBufferedReader(Paths.get(id + ".info.json"))) {
            Metadata metadata = GSON.fromJson(reader, Metadata.class);
            return Optional.of(metadata);
        } catch (IOException e) {
            log.warn("Could not get video metadata: {}", e.toString());
        }
        return Optional.empty();
    }

    private BufferedReader newProcessReader(InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")));
    }

    private void logStream(InputStream stream) {
        try (BufferedReader input = newProcessReader(stream)) {
            String line;
            while ((line = input.readLine()) != null) {
                log.info("[yt-dl] " + line);
            }
        } catch (IOException e) {
            log.warn("Could not read from stream", e);
        }
    }

    @EventSubscriber
    public void onPlayerInit(AudioPlayerInitEvent event) {
        // set the default volume - can be changed later
        log.debug("Audio player initialized");
        event.getPlayer().setVolume(0.2f);
    }

    private String getSource(AudioPlayer.Track track) {
        if (track == null) {
            return "";
        }
        Map<String, Object> metadata = track.getMetadata();
        if (metadata.containsKey("file")) {
            return ((File) metadata.get("file")).getName();
        } else if (metadata.containsKey("url")) {
            if (metadata.containsKey("title")) {
                return String.format("`%s` %s [%s]", metadata.get("url").toString(),
                    metadata.get("title").toString(), metadata.get("duration").toString());
            }
            return metadata.get("url").toString();
        } else {
            return hex(track.hashCode());
        }
    }

    @EventSubscriber
    public void onTrackStart(TrackStartEvent event) {
        log.debug("[Started] {}", getSource(event.getTrack()));
    }

    @EventSubscriber
    public void onTrackEnqueue(TrackQueueEvent event) {
        log.debug("[Enqueued] {}", getSource(event.getTrack()));
    }

    @EventSubscriber
    public void onTrackFinish(TrackFinishEvent event) {
        log.debug("[Finished] {}", getSource(event.getOldTrack()));
    }

    @EventSubscriber
    public void onSkip(TrackSkipEvent event) {
        log.debug("[Skipped] {}", getSource(event.getTrack()));
    }

    @EventSubscriber
    public void onVolumeChange(VolumeChangeEvent event) {
        log.debug("[Volume] {} -> {}", (int) (event.getOldValue() * 100), (int) (event.getNewValue() * 100));
    }

    @EventSubscriber
    public void onPause(PauseStateChangeEvent event) {
        if (event.getNewPauseState()) {
            log.debug("[Pausing]", getSource(event.getPlayer().getCurrentTrack()));
        } else {
            log.debug("[Resuming]", getSource(event.getPlayer().getCurrentTrack()));
        }
    }

    @EventSubscriber
    public void onShuffle(ShuffleEvent event) {
        log.debug("Shuffling {} tracks. Current playlist: {}", event.getPlayer().getPlaylistSize(),
            playlistToString(event.getPlayer()));
    }

    @EventSubscriber
    public void onLoop(LoopStateChangeEvent event) {
        if (event.getNewLoopState()) {
            log.debug("[Loop Enabled]", getSource(event.getPlayer().getCurrentTrack()));
        } else {
            log.debug("[Loop Disabled]", getSource(event.getPlayer().getCurrentTrack()));
        }
    }

    private String hex(int number) {
        return Integer.toHexString(number);
    }

    private String playlistToString(AudioPlayer player) {
        return player.getPlaylist().stream().map(this::getSource).collect(Collectors.joining(", "));
    }

    private int parseInteger(String input, int defaultValue) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            log.warn("Attempted to parse non-numeric value: {}", e.toString());
            return defaultValue;
        }
    }

    private long parseLong(String input, int defaultValue) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            log.warn("Attempted to parse non-numeric value: {}", e.toString());
            return defaultValue;
        }
    }
}
