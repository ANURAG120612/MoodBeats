package com.aurabeats.moodbeats;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SongAdapter extends BaseAdapter {

    private final Context context;
    private final List<Song> songList;
    private final LayoutInflater inflater;
    private final FavoritesManager favoritesManager;
    private final Map<String, Bitmap> imageCache = new HashMap<String, Bitmap>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int playingIndex = -1;

    public interface OnFavoriteChangeListener {
        void onFavoriteChanged();
    }
    private OnFavoriteChangeListener favoriteChangeListener;

    public interface OnSongClickListener {
        void onSongClick(Song song, int position);
    }
    private OnSongClickListener songClickListener;

    public SongAdapter(Context context, List<Song> songList, FavoritesManager favoritesManager) {
        this.context = context;
        this.songList = songList;
        this.favoritesManager = favoritesManager;
        this.inflater = LayoutInflater.from(context);
    }

    public void setOnFavoriteChangeListener(OnFavoriteChangeListener listener) {
        this.favoriteChangeListener = listener;
    }

    public void setOnSongClickListener(OnSongClickListener listener) {
        this.songClickListener = listener;
    }

    public void setPlayingIndex(int index) {
        this.playingIndex = index;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return songList.size();
    }

    @Override
    public Song getItem(int position) {
        return songList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_song, parent, false);
            holder = new ViewHolder();
            holder.imgArt = convertView.findViewById(R.id.img_song_art);
            holder.tvTitle = convertView.findViewById(R.id.tv_song_title);
            holder.tvArtist = convertView.findViewById(R.id.tv_song_artist);
            holder.btnLike = convertView.findViewById(R.id.btn_song_like);
            holder.tvDuration = convertView.findViewById(R.id.tv_song_duration);
            holder.tvTag = convertView.findViewById(R.id.tv_song_tag);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Song song = getItem(position);
        holder.tvTitle.setText(song.title);
        holder.tvArtist.setText(song.artist + (song.album != null && !song.album.isEmpty() ? " • " + song.album : ""));
        holder.tvDuration.setText(song.duration);

        if (position == playingIndex) {
            holder.tvTitle.setTextColor(0xFF00F5D4);
            convertView.setBackgroundColor(0xFF141422);
        } else {
            holder.tvTitle.setTextColor(0xFFFFFFFF);
            convertView.setBackgroundColor(0xFF0A0A0F);
        }

        // Heart Like state
        boolean isLiked = favoritesManager != null && favoritesManager.isLiked(song.id);
        holder.btnLike.setImageResource(isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        holder.btnLike.setFocusable(false);
        holder.btnLike.setFocusableInTouchMode(false);
        holder.btnLike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (favoritesManager != null) {
                    boolean likedNow = favoritesManager.toggleLike(song);
                    holder.btnLike.setImageResource(likedNow ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
                    Toast.makeText(context, likedNow ? "Added to Liked Songs 💚" : "Removed from Liked Songs", Toast.LENGTH_SHORT).show();
                    if (favoriteChangeListener != null) {
                        favoriteChangeListener.onFavoriteChanged();
                    }
                }
            }
        });

        // Click on the song item to play
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (songClickListener != null) {
                    songClickListener.onSongClick(song, position);
                }
            }
        });

        // Offline / Downloaded / YouTube Tag
        if (song.isOffline) {
            holder.tvTag.setText("OFFLINE");
            holder.tvTag.setTextColor(0xFF00E676);
            holder.imgArt.setImageResource(R.drawable.ic_music_note);
        } else if (FavoritesManager.isSongDownloaded(context, song)) {
            holder.tvTag.setText("SAVED 📴");
            holder.tvTag.setTextColor(0xFF00E676);
            loadArtwork(song.artworkUrl, holder.imgArt);
        } else {
            holder.tvTag.setText(song.mood != null ? song.mood.toUpperCase() : "ONLINE");
            holder.tvTag.setTextColor(0xFF00F5D4);
            loadArtwork(song.artworkUrl, holder.imgArt);
        }

        return convertView;
    }

    public void loadArtwork(final String urlStr, final ImageView imageView) {
        if (urlStr == null || urlStr.isEmpty()) {
            imageView.setImageResource(R.drawable.ic_music_note);
            return;
        }

        if (imageCache.containsKey(urlStr)) {
            imageView.setImageBitmap(imageCache.get(urlStr));
            return;
        }

        imageView.setImageResource(R.drawable.ic_music_note);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(4000);
                    InputStream is = conn.getInputStream();
                    final Bitmap bmp = BitmapFactory.decodeStream(is);
                    is.close();
                    if (bmp != null) {
                        imageCache.put(urlStr, bmp);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setImageBitmap(bmp);
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static class ViewHolder {
        ImageView imgArt;
        TextView tvTitle;
        TextView tvArtist;
        ImageButton btnLike;
        TextView tvDuration;
        TextView tvTag;
    }
}
