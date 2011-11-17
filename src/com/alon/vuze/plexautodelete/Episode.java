package com.alon.vuze.plexautodelete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Episode {
    private final String key;
    private final int viewCount;
    private final long lastViewedAt;
    private List<String> files = new ArrayList<String>();

    public Episode(String key, int viewCount, long lastViewedAt, List<String> files) {
        this.key = key;
        this.viewCount = viewCount;
        this.lastViewedAt = lastViewedAt;
        this.files = Collections.unmodifiableList(files);
    }

    public String getKey() {
        return key;
    }

    public int getViewCount() {
        return viewCount;
    }

    public long getLastViewedAt() {
        return lastViewedAt;
    }

    public List<String> getFiles() {
        return files;
    }

    @Override
    public String toString() {
        return "Episode{" +
                "key='" + key + '\'' +
                ", viewCount=" + viewCount +
                ", lastViewedAt=" + lastViewedAt +
                ", files=" + files +
                '}';
    }
}
