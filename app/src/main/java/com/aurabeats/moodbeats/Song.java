package com.aurabeats.moodbeats;

public class Song {
    public String id;
    public String title;
    public String artist;
    public String album;
    public String duration;
    public String artworkUrl;
    public String streamUrl;
    public int rawResId;
    public String mood;
    public boolean isOffline;

    public Song(String id, String title, String artist, String album, String duration, String artworkUrl, String streamUrl, int rawResId, String mood, boolean isOffline) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.artworkUrl = artworkUrl;
        this.streamUrl = streamUrl;
        this.rawResId = rawResId;
        this.mood = mood;
        this.isOffline = isOffline;
    }
}
