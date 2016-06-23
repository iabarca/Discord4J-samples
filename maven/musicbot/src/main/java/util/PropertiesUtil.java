package util;

import java.util.Properties;

public class PropertiesUtil {

    public static int getInteger(Properties properties, String key, int defaultValue) {
        if (!properties.containsKey(key)) {
            return defaultValue;
        } else {
            try {
                return Integer.parseInt(properties.getProperty(key));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    public static long getLong(Properties properties, String key, long defaultValue) {
        if (!properties.containsKey(key)) {
            return defaultValue;
        } else {
            try {
                return Long.parseLong(properties.getProperty(key));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    public static boolean getBoolean(Properties properties, String key, boolean defaultValue) {
        if (!properties.containsKey(key)) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(properties.getProperty(key));
        }
    }

    private PropertiesUtil() {

    }
}
