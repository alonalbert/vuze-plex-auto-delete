package com.alon.vuze.plexautodelete;

@SuppressWarnings("WeakerAccess")
public class Directory {
  private final String title;
  private final String type;
  private final String key;

  @SuppressWarnings("unused")
  public Directory(String title, String type, String key) {
    this.title = title;
    this.type = type;
    this.key = key;
  }

  @SuppressWarnings("unused")
  public String getTitle() {
    return title;
  }

  @SuppressWarnings("unused")
  public String getType() {
    return type;
  }

  @SuppressWarnings("unused")
  public String getKey() {
    return key;
  }

  @Override
  public String toString() {
    return "Section{" +
        "title='" + title + '\'' +
        ", type='" + type + '\'' +
        ", key='" + key + '\'' +
        '}';
  }
}
