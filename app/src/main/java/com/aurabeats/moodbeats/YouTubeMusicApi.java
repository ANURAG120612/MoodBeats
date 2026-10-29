package com.aurabeats.moodbeats;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class YouTubeMusicApi {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static List<Song> search(String query, int limit) {
        List<Song> songs = new ArrayList<Song>();
        if (query == null || query.trim().isEmpty()) return songs;

        try {
            String encoded = URLEncoder.encode(query.trim() + " official audio song", "UTF-8");
            String endpoint = "https://www.youtube.com/results?search_query=" + encoded;
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("User-Agent", USER_AGENT);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String html = sb.toString();
                int start = html.indexOf("ytInitialData = ");
                if (start != -1) {
                    int jsonStart = html.indexOf("{", start);
                    int jsonEnd = html.indexOf(";</script>", jsonStart);
                    if (jsonEnd != -1) {
                        String jsonStr = html.substring(jsonStart, jsonEnd);
                        JSONObject root = new JSONObject(jsonStr);
                        songs.addAll(parseYouTubeJson(root, limit));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (songs.isEmpty()) {
            songs = getFallbackSongs(query);
        }

        return songs;
    }

    private static List<Song> parseYouTubeJson(JSONObject root, int limit) {
        List<Song> list = new ArrayList<Song>();
        Set<String> seenIds = new HashSet<String>();

        try {
            JSONObject contents = root.optJSONObject("contents");
            if (contents == null) return list;
            JSONObject twoCol = contents.optJSONObject("twoColumnSearchResultsRenderer");
            if (twoCol == null) return list;
            JSONObject primary = twoCol.optJSONObject("primaryContents");
            if (primary == null) return list;
            JSONObject sectionList = primary.optJSONObject("sectionListRenderer");
            if (sectionList == null) return list;
            JSONArray sections = sectionList.optJSONArray("contents");
            if (sections == null) return list;

            for (int s = 0; s < sections.length(); s++) {
                JSONObject section = sections.getJSONObject(s);
                JSONObject itemSection = section.optJSONObject("itemSectionRenderer");
                if (itemSection == null) continue;
                JSONArray items = itemSection.optJSONArray("contents");
                if (items == null) continue;

                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    JSONObject vr = item.optJSONObject("videoRenderer");
                    if (vr == null) continue;

                    String videoId = vr.optString("videoId", "");
                    if (videoId.isEmpty() || seenIds.contains(videoId)) continue;
                    seenIds.add(videoId);

                    String title = "Unknown Title";
                    JSONObject titleObj = vr.optJSONObject("title");
                    if (titleObj != null) {
                        JSONArray runs = titleObj.optJSONArray("runs");
                        if (runs != null && runs.length() > 0) {
                            title = runs.getJSONObject(0).optString("text", "Unknown Title");
                        }
                    }

                    String channel = "YouTube Artist";
                    JSONObject ownerObj = vr.optJSONObject("ownerText");
                    if (ownerObj != null) {
                        JSONArray runs = ownerObj.optJSONArray("runs");
                        if (runs != null && runs.length() > 0) {
                            channel = runs.getJSONObject(0).optString("text", "YouTube Artist");
                        }
                    }

                    String duration = "03:45";
                    JSONObject lengthObj = vr.optJSONObject("lengthText");
                    if (lengthObj != null) {
                        duration = lengthObj.optString("simpleText", "03:45");
                    }

                    String artwork = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
                    String streamUrl = "https://www.youtube.com/watch?v=" + videoId;

                    title = cleanTitle(title);
                    list.add(new Song(
                        "yt_" + videoId,
                        title,
                        channel,
                        "YouTube Music",
                        duration,
                        artwork,
                        videoId,
                        0,
                        "YouTube",
                        false
                    ));

                    if (list.size() >= limit) break;
                }
                if (list.size() >= limit) break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    private static String cleanTitle(String t) {
        if (t == null) return "";
        return t.replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("(?i)\\b(official video|official audio|lyric video|music video|full song|4k|hd)\\b", "")
                .replaceAll("[\\[\\(]\\s*[\\]\\)]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static List<Song> getMoodSongs(String mood, int limit) {
        String query;
        if ("Happy".equalsIgnoreCase(mood)) {
            query = "Happy Bollywood Upbeat Dance Hits";
        } else if ("Energetic".equalsIgnoreCase(mood)) {
            query = "High Energy Punjabi Workout Beats";
        } else if ("Calm".equalsIgnoreCase(mood)) {
            query = "Calm Relaxing Acoustic Hindi Lofi";
        } else if ("Sad".equalsIgnoreCase(mood)) {
            query = "Arijit Singh Sad Emotional Ballads";
        } else if ("Romantic".equalsIgnoreCase(mood)) {
            query = "Top Romantic Hindi Love Songs";
        } else {
            query = "Top Trending Hindi Hits 2026";
        }
        return search(query, limit);
    }

    public static List<Song> getFallbackSongs(String category) {
        List<Song> list = new ArrayList<Song>();
        if (category == null) category = "";

        if (category.contains("Happy")) {
            list.add(new Song("yt_6wZ_a3T8uio", "Ilahi", "Arijit Singh", "Yeh Jawaani Hai Deewani", "03:49", "https://i.ytimg.com/vi/6wZ_a3T8uio/hqdefault.jpg", "6wZ_a3T8uio", 0, "Happy", false));
            list.add(new Song("yt_II2EO3Nw4m0", "Badtameez Dil", "Benny Dayal", "Yeh Jawaani Hai Deewani", "04:12", "https://i.ytimg.com/vi/II2EO3Nw4m0/hqdefault.jpg", "II2EO3Nw4m0", 0, "Happy", false));
            list.add(new Song("yt_w4ClQn0jBZw", "Galla Goriyan", "Kanika Kapoor", "Single", "03:18", "https://i.ytimg.com/vi/w4ClQn0jBZw/hqdefault.jpg", "w4ClQn0jBZw", 0, "Happy", false));
            list.add(new Song("yt_bXf_V_xY7Jg", "Kar Gayi Chull", "Badshah, Neha Kakkar", "Kapoor & Sons", "03:07", "https://i.ytimg.com/vi/bXf_V_xY7Jg/hqdefault.jpg", "bXf_V_xY7Jg", 0, "Happy", false));
            list.add(new Song("yt_DRAm6nIOu_Y", "London Thumakda", "Labh Janjua", "Queen", "03:51", "https://i.ytimg.com/vi/DRAm6nIOu_Y/hqdefault.jpg", "DRAm6nIOu_Y", 0, "Happy", false));
        } else if (category.contains("Energetic") || category.contains("Workout")) {
            list.add(new Song("yt_8n47_m4F8k8", "Zinda Banda", "Anirudh Ravichander", "Jawan", "04:24", "https://i.ytimg.com/vi/8n47_m4F8k8/hqdefault.jpg", "8n47_m4F8k8", 0, "Energetic", false));
            list.add(new Song("yt_OsU0HhybiDA", "Naatu Naatu", "Rahul Sipligunj, Kaala Bhairava", "RRR", "03:35", "https://i.ytimg.com/vi/OsU0HhybiDA/hqdefault.jpg", "OsU0HhybiDA", 0, "Energetic", false));
            list.add(new Song("yt_l_MyUGq7pgs", "Malhari", "Vishal Dadlani", "Bajirao Mastani", "04:05", "https://i.ytimg.com/vi/l_MyUGq7pgs/hqdefault.jpg", "l_MyUGq7pgs", 0, "Energetic", false));
            list.add(new Song("yt_0n_HQw5T6s8", "Jai Jai Shivshankar", "Vishal Dadlani, Benny Dayal", "War", "03:50", "https://i.ytimg.com/vi/0n_HQw5T6s8/hqdefault.jpg", "0n_HQw5T6s8", 0, "Energetic", false));
            list.add(new Song("yt__KhQT-LGb-4", "Aankh Marey", "Neha Kakkar, Mika Singh", "Simmba", "03:33", "https://i.ytimg.com/vi/_KhQT-LGb-4/hqdefault.jpg", "_KhQT-LGb-4", 0, "Energetic", false));
        } else if (category.contains("Calm") || category.contains("Relaxing")) {
            list.add(new Song("yt_w_h9O0y4uEI", "O Mere Dil Ke Chain", "Kishore Kumar", "Mere Jeevan Saathi", "04:32", "https://i.ytimg.com/vi/w_h9O0y4uEI/hqdefault.jpg", "w_h9O0y4uEI", 0, "Calm", false));
            list.add(new Song("yt_fSS_R91Nimw", "Iktara", "Kavita Seth", "Wake Up Sid", "04:14", "https://i.ytimg.com/vi/fSS_R91Nimw/hqdefault.jpg", "fSS_R91Nimw", 0, "Calm", false));
            list.add(new Song("yt_v0gu1N8_w8E", "Pani Da Rang", "Ayushmann Khurrana", "Vicky Donor", "04:00", "https://i.ytimg.com/vi/v0gu1N8_w8E/hqdefault.jpg", "v0gu1N8_w8E", 0, "Calm", false));
            list.add(new Song("yt_T94PHkuydcw", "Kun Faya Kun", "A.R. Rahman, Javed Ali", "Rockstar", "07:51", "https://i.ytimg.com/vi/T94PHkuydcw/hqdefault.jpg", "T94PHkuydcw", 0, "Calm", false));
            list.add(new Song("yt_jHNNMj5bNQw", "Kabira (Encore)", "Harshdeep Kaur, Arijit Singh", "Yeh Jawaani Hai Deewani", "04:29", "https://i.ytimg.com/vi/jHNNMj5bNQw/hqdefault.jpg", "jHNNMj5bNQw", 0, "Calm", false));
        } else if (category.contains("Sad") || category.contains("Emotional")) {
            list.add(new Song("yt_284Ov7ysmfA", "Channa Mereya", "Arijit Singh", "Ae Dil Hai Mushkil", "04:49", "https://i.ytimg.com/vi/284Ov7ysmfA/hqdefault.jpg", "284Ov7ysmfA", 0, "Sad", false));
            list.add(new Song("yt_sK7riqg2mr4", "Agar Tum Saath Ho", "Arijit Singh, Alka Yagnik", "Tamasha", "05:41", "https://i.ytimg.com/vi/sK7riqg2mr4/hqdefault.jpg", "sK7riqg2mr4", 0, "Sad", false));
            list.add(new Song("yt_f3FFOBrMmdg", "Hamari Adhuri Kahani", "Arijit Singh", "Hamari Adhuri Kahani", "06:38", "https://i.ytimg.com/vi/f3FFOBrMmdg/hqdefault.jpg", "f3FFOBrMmdg", 0, "Sad", false));
            list.add(new Song("yt_AFf_QJb8v5Y", "Tujhe Kitna Chahne Lage", "Arijit Singh", "Kabir Singh", "04:44", "https://i.ytimg.com/vi/AFf_QJb8v5Y/hqdefault.jpg", "AFf_QJb8v5Y", 0, "Sad", false));
            list.add(new Song("yt_VOLKJJvfAbg", "Bekhayali", "Sachet Tandon", "Kabir Singh", "06:11", "https://i.ytimg.com/vi/VOLKJJvfAbg/hqdefault.jpg", "VOLKJJvfAbg", 0, "Sad", false));
        } else if (category.contains("Romantic") || category.contains("Love")) {
            list.add(new Song("yt_BddP6PYo2gs", "Kesariya", "Arijit Singh", "Brahmastra", "04:28", "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg", "BddP6PYo2gs", 0, "Romantic", false));
            list.add(new Song("yt_IJq0yyWug1k", "Tum Hi Ho", "Arijit Singh", "Aashiqui 2", "04:22", "https://i.ytimg.com/vi/IJq0yyWug1k/hqdefault.jpg", "IJq0yyWug1k", 0, "Romantic", false));
            list.add(new Song("yt_gvyUuxdRdR4", "Raataan Lambiyan", "Jubin Nautiyal, Asees Kaur", "Shershaah", "03:50", "https://i.ytimg.com/vi/gvyUuxdRdR4/hqdefault.jpg", "gvyUuxdRdR4", 0, "Romantic", false));
            list.add(new Song("yt_TaG8rL_rKx4", "Pehle Bhi Main", "Vishal Mishra", "Animal", "04:10", "https://i.ytimg.com/vi/TaG8rL_rKx4/hqdefault.jpg", "TaG8rL_rKx4", 0, "Romantic", false));
            list.add(new Song("yt_xitd9880A2w", "Mast Magan", "Arijit Singh, Chinmayi", "2 States", "04:40", "https://i.ytimg.com/vi/xitd9880A2w/hqdefault.jpg", "xitd9880A2w", 0, "Romantic", false));
        } else {
            // Default Trending Hits
            list.add(new Song("yt_ElZfdU54Cp8", "Apna Bana Le", "Arijit Singh", "Bhediya", "04:21", "https://i.ytimg.com/vi/ElZfdU54Cp8/hqdefault.jpg", "ElZfdU54Cp8", 0, "Trending", false));
            list.add(new Song("yt_BddP6PYo2gs", "Kesariya", "Arijit Singh", "Brahmastra", "04:28", "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg", "BddP6PYo2gs", 0, "Romantic", false));
            list.add(new Song("yt_RLzC55ai0eo", "Heeriye", "Arijit Singh, Jasleen Royal", "Single", "03:19", "https://i.ytimg.com/vi/RLzC55ai0eo/hqdefault.jpg", "RLzC55ai0eo", 0, "Trending", false));
            list.add(new Song("yt_VAdGW7QDJiU", "Chaleya", "Arijit Singh, Shilpa Rao", "Jawan", "03:20", "https://i.ytimg.com/vi/VAdGW7QDJiU/hqdefault.jpg", "VAdGW7QDJiU", 0, "Trending", false));
            list.add(new Song("yt_iAIBF2ngbWY", "O Maahi", "Arijit Singh", "Dunki", "03:53", "https://i.ytimg.com/vi/iAIBF2ngbWY/hqdefault.jpg", "iAIBF2ngbWY", 0, "Trending", false));
            list.add(new Song("yt_6wZ_a3T8uio", "Ilahi", "Arijit Singh", "Yeh Jawaani Hai Deewani", "03:49", "https://i.ytimg.com/vi/6wZ_a3T8uio/hqdefault.jpg", "6wZ_a3T8uio", 0, "Happy", false));
        }

        return list;
    }
}
