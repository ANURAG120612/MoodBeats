package com.aurabeats.moodbeats;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FavoritesManager {

    private static final String PREF_NAME = "moodbeats_spotify_prefs";
    private static final String KEY_LIKED_IDS = "liked_song_ids";
    private static final String KEY_LIKED_SONGS_JSON = "liked_songs_data";

    private final SharedPreferences prefs;
    private final Set<String> likedIds = new HashSet<String>();
    private final List<Song> likedSongs = new ArrayList<Song>();

    public FavoritesManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        likedIds.clear();
        likedSongs.clear();
        Set<String> savedIds = prefs.getStringSet(KEY_LIKED_IDS, null);
        if (savedIds != null) {
            likedIds.addAll(savedIds);
        }

        String json = prefs.getString(KEY_LIKED_SONGS_JSON, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Song s = new Song(
                    obj.optString("id"),
                    obj.optString("title"),
                    obj.optString("artist"),
                    obj.optString("album"),
                    obj.optString("duration"),
                    obj.optString("artworkUrl"),
                    obj.optString("streamUrl"),
                    obj.optInt("rawResId", 0),
                    obj.optString("mood", "Liked"),
                    obj.optBoolean("isOffline", false)
                );
                likedSongs.add(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized boolean isLiked(String songId) {
        return songId != null && likedIds.contains(songId);
    }

    public synchronized boolean toggleLike(Song song) {
        if (song == null || song.id == null) return false;
        boolean currentlyLiked = likedIds.contains(song.id);
        if (currentlyLiked) {
            likedIds.remove(song.id);
            for (int i = likedSongs.size() - 1; i >= 0; i--) {
                if (song.id.equals(likedSongs.get(i).id)) {
                    likedSongs.remove(i);
                }
            }
        } else {
            likedIds.add(song.id);
            likedSongs.add(0, song);
        }
        save();
        return !currentlyLiked;
    }

    private synchronized void save() {
        JSONArray arr = new JSONArray();
        for (Song s : likedSongs) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", s.id);
                obj.put("title", s.title);
                obj.put("artist", s.artist);
                obj.put("album", s.album);
                obj.put("duration", s.duration);
                obj.put("artworkUrl", s.artworkUrl);
                obj.put("streamUrl", s.streamUrl);
                obj.put("rawResId", s.rawResId);
                obj.put("mood", s.mood);
                obj.put("isOffline", s.isOffline);
                arr.put(obj);
            } catch (Exception ignored) {}
        }

        prefs.edit()
            .putStringSet(KEY_LIKED_IDS, new HashSet<String>(likedIds))
            .putString(KEY_LIKED_SONGS_JSON, arr.toString())
            .apply();
    }

    public synchronized List<Song> getLikedSongs() {
        return new ArrayList<Song>(likedSongs);
    }

    // --- Offline Download Helpers ---
    public static File getDownloadFile(Context ctx, Song song) {
        File dir = new File(ctx.getFilesDir(), "downloads");
        if (!dir.exists()) dir.mkdirs();
        String safeName = song.id.replaceAll("[^a-zA-Z0-9_-]", "_") + ".mp4";
        return new File(dir, safeName);
    }

    public static boolean isSongDownloaded(Context ctx, Song song) {
        if (song == null) return false;
        if (song.rawResId > 0) return true;
        File f = getDownloadFile(ctx, song);
        return f.exists() && f.length() > 50000;
    }

    public interface DownloadCallback {
        void onSuccess(File file);
        void onError(String msg);
    }

    public static void downloadTrack(final Context ctx, final Song song, final DownloadCallback callback) {
        if (song == null || song.streamUrl == null || song.streamUrl.isEmpty()) {
            if (callback != null) callback.onError("No stream URL available");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File dest = getDownloadFile(ctx, song);
                    if (dest.exists() && dest.length() > 50000) {
                        if (callback != null) callback.onSuccess(dest);
                        return;
                    }

                    URL url = new URL(song.streamUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);

                    if (conn.getResponseCode() != 200) {
                        if (callback != null) callback.onError("HTTP " + conn.getResponseCode());
                        return;
                    }

                    InputStream in = conn.getInputStream();
                    FileOutputStream out = new FileOutputStream(dest);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    out.flush();
                    out.close();
                    in.close();

                    if (callback != null) callback.onSuccess(dest);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e.getMessage());
                }
            }
        }).start();
    }
}
