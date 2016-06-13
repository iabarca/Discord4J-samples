package util;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

public class DateUtil {

    public static String formatDuration(final Duration duration) {
        long absSeconds = Math.abs(duration.getSeconds());
        long seconds = absSeconds % 60;
        long minutes = (absSeconds % 3600) / 60;
        long hours = absSeconds / 3600;

        return (hours == 0 ? "" : hours + ":") +
            (minutes == 0 ? "00" : minutes < 10 ? String.valueOf("0" + minutes) : String.valueOf(minutes)) +
            ":" +
            (seconds == 0 ? "00" : seconds < 10 ? String.valueOf("0" + seconds) : String.valueOf(seconds));
    }

    public static String formatHuman(Duration duration, boolean minimal) {
        Duration abs = duration.abs();
        long totalSeconds = abs.getSeconds();
        if (totalSeconds == 0) {
            return abs.toMillis() + (minimal ? "ms" : " milliseconds");
        }
        long d = totalSeconds / (3600 * 24);
        long h = (totalSeconds % (3600 * 24)) / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        String days = minimal ? compact(d, "d") : inflect(d, "day");
        String hours = minimal ? compact(h, "h") : inflect(h, "hour");
        String minutes = minimal ? compact(m, "m") : inflect(m, "minute");
        String seconds = minimal ? compact(s, "s") : inflect(s, "second");
        return Arrays.asList(days, hours, minutes, seconds).stream()
            .filter(str -> !str.isEmpty()).collect(Collectors.joining(minimal ? "" : ", "));
    }

    private static String compact(long value, String suffix) {
        return (value == 0 ? "" : value + suffix);
    }

    private static String inflect(long value, String singular) {
        return (value == 1 ? "1 " + singular : (value > 1 ? value + " " + singular + "s" : ""));
    }
}
