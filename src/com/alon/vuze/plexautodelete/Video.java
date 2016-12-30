package com.alon.vuze.plexautodelete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class Video implements Comparable<Video> {
  private final String key;
  private final int viewCount;
  private final long lastViewedAt;
  private List<String> files = new ArrayList<>();

  @SuppressWarnings("unused")
  public Video(String key, int viewCount, long lastViewedAt, List<String> files) {
    this.key = key;
    this.viewCount = viewCount;
    this.lastViewedAt = lastViewedAt;
    this.files = Collections.unmodifiableList(files);
  }

  @SuppressWarnings("unused")
  public String getKey() {
    return key;
  }

  @SuppressWarnings("unused")
  public int getViewCount() {
    return viewCount;
  }

  @SuppressWarnings("unused")
  public long getLastViewedAt() {
    return lastViewedAt;
  }

  @SuppressWarnings("unused")
  public List<String> getFiles() {
    return files;
  }

  @Override
  public String toString() {
    return "Video{" +
        "key='" + key + '\'' +
        ", viewCount=" + viewCount +
        ", lastViewedAt=" + lastViewedAt +
        ", files=" + files +
        '}';
  }

  @Override
  public int compareTo(Video another) {
    return ((Long) lastViewedAt).compareTo(another.lastViewedAt);
  }
}
