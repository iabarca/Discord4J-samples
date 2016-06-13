package util;

import java.util.ArrayList;
import java.util.List;

public class SplitMessage {

    private final String message;

    public SplitMessage(String message) {
        this.message = message;
    }

    public List<String> split(int maxLength) {
        List<String> splits = new ArrayList<>();
        String str = message;
        int end;
        while (!str.isEmpty()) {
            int codeBlockTags = countOccurrencesOf(str, "```");
            if (str.length() <= Math.max(1, maxLength - (codeBlockTags > 0 ? 4 : 0))) {
                splits.add(str);
                str = "";
            } else {
                end = Math.min(str.length(), str.lastIndexOf("\n", maxLength));
                if (end <= 0) {
                    end = Math.min(str.length(), maxLength);
                }
                String split = str.substring(0, end);
                str = str.substring(end);
                int tagsAfterSplit = countOccurrencesOf(split, "```");
                if (codeBlockTags > 0 && tagsAfterSplit < codeBlockTags && tagsAfterSplit % 2 != 0) {
                    split = split + "\n```";
                    str = "```\n" + str;
                }
                splits.add(split);
            }
        }
        return splits;
    }

    private static int countOccurrencesOf(String str, String sub) {
        if (str == null || sub == null || str.length() == 0 || sub.length() == 0) {
            return 0;
        }
        int count = 0;
        int pos = 0;
        int idx;
        while ((idx = str.indexOf(sub, pos)) != -1) {
            ++count;
            pos = idx + sub.length();
        }
        return count;
    }
}
