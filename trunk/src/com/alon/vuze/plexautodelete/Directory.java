package com.alon.vuze.plexautodelete;// Copyright 2011 Google Inc. All Rights Reserved.

/**
 * @author aalbert@google.com (Alon Albert)
 */
public class Directory {
    private final String title;
    private final String type;
    private final String key;

    public Directory(String title, String type, String key) {
        this.title = title;
        this.type = type;
        this.key = key;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

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
