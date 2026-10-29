package com.aurabeats.moodbeats;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * CloudMusicApi - High-performance music search and direct audio stream provider.
 * Delivers direct 160kbps AAC/MP4 CDN streams natively playable via Android MediaPlayer.
 */
public class CloudMusicApi {

    private static final String DES_KEY = "38346591";
    private static final Map<String, String> URL_CACHE = new ConcurrentHashMap<String, String>();

    public static List<Song> search(String query, int limit) {
        List<Song> songs = new ArrayList<Song>();
        if (query == null || query.trim().isEmpty()) return songs;

        try {
            String encoded = URLEncoder.encode(query.trim(), "UTF-8");
            String endpoint = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&p=1&n=" + limit + "&q=" + encoded;
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONArray results = root.optJSONArray("results");
                if (results != null) {
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject item = results.getJSONObject(i);
                        String title = cleanHtml(item.optString("song", "Unknown Track"));
                        String artist = cleanHtml(item.optString("primary_artists", "Unknown Artist"));
                        String album = cleanHtml(item.optString("album", "Single"));

                        int durSec = 210;
                        try {
                            String durStr = item.optString("duration", "210");
                            durSec = Integer.parseInt(durStr);
                        } catch (Exception ignored) {}
                        String duration = formatSeconds(durSec);

                        String artwork = item.optString("image", "");
                        if (artwork.contains("150x150.jpg")) {
                            artwork = artwork.replace("150x150.jpg", "500x500.jpg");
                        } else if (artwork.contains("50x50.jpg")) {
                            artwork = artwork.replace("50x50.jpg", "500x500.jpg");
                        }

                        String encUrl = item.optString("encrypted_media_url", "");
                        String streamUrl = decryptMediaUrl(encUrl);

                        if (streamUrl != null && !streamUrl.isEmpty()) {
                            String songId = "saavn_" + item.optString("id", String.valueOf(i));
                            songs.add(new Song(songId, title, artist, album, duration, artwork, streamUrl, 0, "Online", false));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return songs;
    }

    public static List<Song> getMoodSongs(String mood, int limit) {
        String query;
        if ("Happy".equalsIgnoreCase(mood)) {
            query = "Happy Bollywood Upbeat Hits";
        } else if ("Energetic".equalsIgnoreCase(mood)) {
            query = "Party Punjabi High Energy Dance";
        } else if ("Calm".equalsIgnoreCase(mood)) {
            query = "Kishore Kumar Relaxing Calm Lo-Fi";
        } else if ("Sad".equalsIgnoreCase(mood)) {
            query = "Arijit Singh Sad Emotional Ballads";
        } else if ("Romantic".equalsIgnoreCase(mood)) {
            query = "Arijit Singh Romantic Love Hits";
        } else if ("Trending".equalsIgnoreCase(mood)) {
            query = "Top Bollywood Trending Songs";
        } else {
            query = "Top Bollywood Trending Songs";
        }

        List<Song> results = search(query, limit);
        for (Song s : results) {
            s.mood = mood;
        }
        return results;
    }

    public static String decryptMediaUrl(String enc) {
        if (enc == null || enc.trim().isEmpty()) return "";
        if (URL_CACHE.containsKey(enc)) {
            return URL_CACHE.get(enc);
        }

        try {
            Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(DES_KEY.getBytes("UTF-8"), "DES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] encBytes = Base64.decode(enc.trim(), Base64.DEFAULT);
            byte[] decBytes = cipher.doFinal(encBytes);
            String url = new String(decBytes, "UTF-8");
            if (url.contains("_96.mp4")) {
                url = url.replace("_96.mp4", "_160.mp4");
            } else if (url.contains("_96.m4a")) {
                url = url.replace("_96.m4a", "_160.m4a");
            }
            URL_CACHE.put(enc, url);
            return url;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String formatSeconds(int totalSec) {
        if (totalSec <= 0) return "03:30";
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format("%02d:%02d", m, s);
    }

    private static String cleanHtml(String text) {
        if (text == null) return "";
        return text.replace("&quot;", "\"")
                   .replace("&amp;", "&")
                   .replace("&#039;", "'")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&nbsp;", " ")
                   .trim();
    }
}
