package com.aurabeats.moodbeats;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // Views - Navigation
    private LinearLayout navHome, navSearch, navLibrary;
    private ImageView navIconHome, navIconSearch, navIconLibrary;
    private TextView navTextHome, navTextSearch, navTextLibrary;

    // Views - Main Search & Carousel
    private LinearLayout searchBarLayout;
    private EditText etSearch;
    private Button btnSearchGo;
    private Button pillLiked, pillAll, pillHappy, pillEnergetic, pillCalm, pillSad, pillRomantic, pillOffline;
    private TextView tvSectionTitle, tvTrackCount, tvOnlineBadge;
    private ProgressBar loadingSpinner;
    private ListView lvSongs;

    // Views - Mini Player
    private LinearLayout miniPlayerLayout;
    private TextView tvPlayerTitle, tvPlayerArtist;
    private ImageView playerArt;
    private ImageButton btnMiniLike, btnPlay, btnNext;

    // Views - Full Now Playing Overlay (Spotify Style)
    private LinearLayout nowPlayingOverlay;
    private ImageButton btnCloseNowPlaying, btnSleepTimer;
    private TextView tvFullPlaylistTitle;
    private ImageView ivFullArt;
    private TextView tvFullTitle, tvFullArtist;
    private ImageButton btnFullLike, btnFullDownload;
    private SeekBar fullSeekBar;
    private TextView tvFullCurrentTime, tvFullTotalTime;
    private ImageButton btnFullShuffle, btnFullPrev, btnFullPlay, btnFullNext, btnFullRepeat;
    private TextView tvTimerStatus;

    // Media & State
    private MediaPlayer mediaPlayer;
    private final List<Song> currentQueue = new ArrayList<Song>();
    private final List<Song> offlineSongs = new ArrayList<Song>();
    private SongAdapter songAdapter;
    private FavoritesManager favoritesManager;
    private int currentSongIndex = -1;
    private boolean isUserSeeking = false;

    // Spotify Playback Modes
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0 = Off, 1 = Repeat All, 2 = Repeat One
    private final Random random = new Random();

    // Sleep Timer State
    private int sleepTimerMinutes = 0;
    private long sleepTimerEndTime = 0;
    private boolean sleepAtSongEnd = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler seekHandler = new Handler(Looper.getMainLooper());

    private final Runnable seekUpdater = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying() && !isUserSeeking) {
                int pos = mediaPlayer.getCurrentPosition();
                int total = mediaPlayer.getDuration();
                if (total > 0) {
                    fullSeekBar.setMax(total);
                    fullSeekBar.setProgress(pos);
                    String curStr = formatDuration(pos);
                    String totStr = formatDuration(total);
                    tvFullCurrentTime.setText(curStr);
                    tvFullTotalTime.setText(totStr);
                }
            }
            updateSleepTimerCountdown();
            seekHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#0a0a0f"));
            window.setNavigationBarColor(Color.parseColor("#0c0c14"));
        }

        favoritesManager = new FavoritesManager(this);

        initOfflineLibrary();
        initViews();
        setupPlayerControls();
        setupMoodPills();
        setupBottomNav();
        setupNowPlayingOverlay();

        // Start with Trending Hits immediately
        fetchTrendingHits();
    }

    private void initOfflineLibrary() {
        offlineSongs.clear();
        offlineSongs.add(new Song("off_1", "Ilahi", "Arijit Singh", "Yeh Jawaani Hai Deewani", "03:49", "", null, R.raw.ilahi, "Happy", true));
        offlineSongs.add(new Song("off_2", "Kesariya", "Arijit Singh", "Brahmastra", "04:28", "", null, R.raw.kesariya, "Romantic", true));
        offlineSongs.add(new Song("off_3", "Channa Mereya", "Arijit Singh", "Ae Dil Hai Mushkil", "04:49", "", null, R.raw.channa_mereya, "Sad", true));
        offlineSongs.add(new Song("off_4", "O Mere Dil Ke Chain", "Kishore Kumar", "Mere Jeevan Saathi", "04:32", "", null, R.raw.dil_ke_chain, "Calm", true));
    }

    private void initViews() {
        searchBarLayout = findViewById(R.id.search_bar_layout);
        etSearch = findViewById(R.id.et_search);
        btnSearchGo = findViewById(R.id.btn_search_go);
        tvSectionTitle = findViewById(R.id.tv_section_title);
        tvTrackCount = findViewById(R.id.tv_track_count);
        tvOnlineBadge = findViewById(R.id.tv_online_badge);
        loadingSpinner = findViewById(R.id.loading_spinner);
        lvSongs = findViewById(R.id.lv_songs);

        // Pills
        pillLiked = findViewById(R.id.pill_liked);
        pillAll = findViewById(R.id.pill_all);
        pillHappy = findViewById(R.id.pill_happy);
        pillEnergetic = findViewById(R.id.pill_energetic);
        pillCalm = findViewById(R.id.pill_calm);
        pillSad = findViewById(R.id.pill_sad);
        pillRomantic = findViewById(R.id.pill_romantic);
        pillOffline = findViewById(R.id.pill_offline);

        // Mini-Player Views
        miniPlayerLayout = findViewById(R.id.mini_player_layout);
        playerArt = findViewById(R.id.player_art);
        tvPlayerTitle = findViewById(R.id.tv_player_title);
        tvPlayerArtist = findViewById(R.id.tv_player_artist);
        btnMiniLike = findViewById(R.id.btn_mini_like);
        btnPlay = findViewById(R.id.btn_player_play);
        btnNext = findViewById(R.id.btn_player_next);

        // Navigation Views
        navHome = findViewById(R.id.nav_home);
        navSearch = findViewById(R.id.nav_search);
        navLibrary = findViewById(R.id.nav_library);
        navIconHome = findViewById(R.id.nav_icon_home);
        navIconSearch = findViewById(R.id.nav_icon_search);
        navIconLibrary = findViewById(R.id.nav_icon_library);
        navTextHome = findViewById(R.id.nav_text_home);
        navTextSearch = findViewById(R.id.nav_text_search);
        navTextLibrary = findViewById(R.id.nav_text_library);

        // Full Screen Overlay Views
        nowPlayingOverlay = findViewById(R.id.now_playing_overlay);
        btnCloseNowPlaying = findViewById(R.id.btn_close_now_playing);
        tvFullPlaylistTitle = findViewById(R.id.tv_full_playlist_title);
        btnSleepTimer = findViewById(R.id.btn_sleep_timer);
        ivFullArt = findViewById(R.id.iv_full_art);
        tvFullTitle = findViewById(R.id.tv_full_title);
        tvFullArtist = findViewById(R.id.tv_full_artist);
        btnFullLike = findViewById(R.id.btn_full_like);
        btnFullDownload = findViewById(R.id.btn_full_download);
        fullSeekBar = findViewById(R.id.full_seek_bar);
        tvFullCurrentTime = findViewById(R.id.tv_full_current_time);
        tvFullTotalTime = findViewById(R.id.tv_full_total_time);
        btnFullShuffle = findViewById(R.id.btn_full_shuffle);
        btnFullPrev = findViewById(R.id.btn_full_prev);
        btnFullPlay = findViewById(R.id.btn_full_play);
        btnFullNext = findViewById(R.id.btn_full_next);
        btnFullRepeat = findViewById(R.id.btn_full_repeat);
        tvTimerStatus = findViewById(R.id.tv_timer_status);

        songAdapter = new SongAdapter(this, currentQueue, favoritesManager);
        songAdapter.setOnFavoriteChangeListener(new SongAdapter.OnFavoriteChangeListener() {
            @Override
            public void onFavoriteChanged() {
                updateLikeButtonState();
            }
        });
        lvSongs.setAdapter(songAdapter);

        lvSongs.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                playTrackAtIndex(position);
            }
        });

        btnSearchGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearch();
            }
        });

        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    performSearch();
                    return true;
                }
                return false;
            }
        });
    }

    private void setupBottomNav() {
        navHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                highlightNavTab(0);
                fetchTrendingHits();
            }
        });

        navSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                highlightNavTab(1);
                etSearch.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        navLibrary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                highlightNavTab(2);
                loadLikedSongsQueue();
            }
        });
    }

    private void highlightNavTab(int index) {
        int activeColor = Color.parseColor("#00f5d4");
        int inactiveColor = Color.parseColor("#a0a0b8");

        navTextHome.setTextColor(index == 0 ? activeColor : inactiveColor);
        navTextSearch.setTextColor(index == 1 ? activeColor : inactiveColor);
        navTextLibrary.setTextColor(index == 2 ? activeColor : inactiveColor);

        navIconHome.setColorFilter(index == 0 ? activeColor : inactiveColor);
        navIconSearch.setColorFilter(index == 1 ? activeColor : inactiveColor);
        navIconLibrary.setColorFilter(index == 2 ? activeColor : inactiveColor);
    }

    private void setupPlayerControls() {
        // Mini player click opens full Now Playing screen
        miniPlayerLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentSongIndex >= 0 && currentSongIndex < currentQueue.size()) {
                    nowPlayingOverlay.setVisibility(View.VISIBLE);
                }
            }
        });

        // Mini Like button
        btnMiniLike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCurrentSongLike();
            }
        });

        // Mini Play/Pause button
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayPause();
            }
        });

        // Mini Next button
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNext();
            }
        });
    }

    private void setupNowPlayingOverlay() {
        btnCloseNowPlaying.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nowPlayingOverlay.setVisibility(View.GONE);
            }
        });

        btnFullPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayPause();
            }
        });

        btnFullNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNext();
            }
        });

        btnFullPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPrevious();
            }
        });

        btnFullLike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCurrentSongLike();
            }
        });

        btnFullDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadCurrentSong();
            }
        });

        btnFullShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isShuffle = !isShuffle;
                btnFullShuffle.setColorFilter(isShuffle ? Color.parseColor("#00f5d4") : Color.parseColor("#a0a0b8"));
                Toast.makeText(MainActivity.this, isShuffle ? "Shuffle On 🔀" : "Shuffle Off", Toast.LENGTH_SHORT).show();
            }
        });

        btnFullRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                repeatMode = (repeatMode + 1) % 3;
                if (repeatMode == 0) {
                    btnFullRepeat.setImageResource(R.drawable.ic_repeat);
                    btnFullRepeat.setColorFilter(Color.parseColor("#a0a0b8"));
                    Toast.makeText(MainActivity.this, "Repeat Off", Toast.LENGTH_SHORT).show();
                } else if (repeatMode == 1) {
                    btnFullRepeat.setImageResource(R.drawable.ic_repeat);
                    btnFullRepeat.setColorFilter(Color.parseColor("#00f5d4"));
                    Toast.makeText(MainActivity.this, "Repeat All 🔁", Toast.LENGTH_SHORT).show();
                } else {
                    btnFullRepeat.setImageResource(R.drawable.ic_repeat_one);
                    btnFullRepeat.setColorFilter(Color.parseColor("#00f5d4"));
                    Toast.makeText(MainActivity.this, "Repeat One 🔂", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnSleepTimer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSleepTimerDialog();
            }
        });

        fullSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvFullCurrentTime.setText(formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(seekBar.getProgress());
                }
            }
        });
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) {
            if (!currentQueue.isEmpty()) {
                playTrackAtIndex(0);
            }
            return;
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlay.setImageResource(R.drawable.ic_play_arrow);
            btnFullPlay.setImageResource(R.drawable.ic_play_arrow);
        } else {
            mediaPlayer.start();
            btnPlay.setImageResource(R.drawable.ic_pause_black);
            btnFullPlay.setImageResource(R.drawable.ic_pause_black);
        }
    }

    private void toggleCurrentSongLike() {
        if (currentSongIndex < 0 || currentSongIndex >= currentQueue.size()) return;
        Song song = currentQueue.get(currentSongIndex);
        boolean likedNow = favoritesManager.toggleLike(song);
        updateLikeButtonState();
        songAdapter.notifyDataSetChanged();
        Toast.makeText(this, likedNow ? "Saved to Liked Songs 💚" : "Removed from Liked Songs", Toast.LENGTH_SHORT).show();
    }

    private void updateLikeButtonState() {
        if (currentSongIndex < 0 || currentSongIndex >= currentQueue.size()) {
            btnMiniLike.setImageResource(R.drawable.ic_heart_outline);
            btnFullLike.setImageResource(R.drawable.ic_heart_outline);
            return;
        }
        Song song = currentQueue.get(currentSongIndex);
        boolean isLiked = favoritesManager.isLiked(song.id);
        int heartRes = isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline;
        btnMiniLike.setImageResource(heartRes);
        btnFullLike.setImageResource(heartRes);
    }

    private void downloadCurrentSong() {
        if (currentSongIndex < 0 || currentSongIndex >= currentQueue.size()) return;
        final Song song = currentQueue.get(currentSongIndex);

        if (FavoritesManager.isSongDownloaded(this, song)) {
            Toast.makeText(this, "This song is already saved for offline listening! 📴", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Downloading " + song.title + "...", Toast.LENGTH_SHORT).show();
        btnFullDownload.setColorFilter(Color.parseColor("#00f5d4"));

        FavoritesManager.downloadTrack(this, song, new FavoritesManager.DownloadCallback() {
            @Override
            public void onSuccess(File file) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        btnFullDownload.setImageResource(R.drawable.ic_download_done);
                        btnFullDownload.setColorFilter(Color.parseColor("#00e676"));
                        Toast.makeText(MainActivity.this, "Downloaded! Saved for offline playback 📴", Toast.LENGTH_LONG).show();
                        songAdapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onError(final String msg) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        btnFullDownload.setColorFilter(Color.parseColor("#a0a0b8"));
                        Toast.makeText(MainActivity.this, "Download error: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void setupMoodPills() {
        View.OnClickListener pillListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetPillStyles();
                ((Button) v).setBackgroundResource(R.drawable.bg_pill_active);
                ((Button) v).setTextColor(Color.BLACK);

                int id = v.getId();
                if (id == R.id.pill_liked) {
                    loadLikedSongsQueue();
                } else if (id == R.id.pill_all) {
                    fetchTrendingHits();
                } else if (id == R.id.pill_happy) {
                    fetchMoodTracks("Happy");
                } else if (id == R.id.pill_energetic) {
                    fetchMoodTracks("Energetic");
                } else if (id == R.id.pill_calm) {
                    fetchMoodTracks("Calm");
                } else if (id == R.id.pill_sad) {
                    fetchMoodTracks("Sad");
                } else if (id == R.id.pill_romantic) {
                    fetchMoodTracks("Romantic");
                } else if (id == R.id.pill_offline) {
                    loadOfflineQueue();
                }
            }
        };

        pillLiked.setOnClickListener(pillListener);
        pillAll.setOnClickListener(pillListener);
        pillHappy.setOnClickListener(pillListener);
        pillEnergetic.setOnClickListener(pillListener);
        pillCalm.setOnClickListener(pillListener);
        pillSad.setOnClickListener(pillListener);
        pillRomantic.setOnClickListener(pillListener);
        pillOffline.setOnClickListener(pillListener);
    }

    private void resetPillStyles() {
        Button[] pills = new Button[]{pillLiked, pillAll, pillHappy, pillEnergetic, pillCalm, pillSad, pillRomantic, pillOffline};
        for (Button b : pills) {
            b.setBackgroundResource(R.drawable.bg_pill_inactive);
            b.setTextColor(Color.WHITE);
        }
    }

    private void loadLikedSongsQueue() {
        List<Song> liked = favoritesManager.getLikedSongs();
        currentQueue.clear();
        currentQueue.addAll(liked);
        songAdapter.notifyDataSetChanged();
        tvSectionTitle.setText("💚 Liked Songs Library");
        tvTrackCount.setText(currentQueue.size() + " Songs");
        tvOnlineBadge.setText("💚 Favorites");
        if (liked.isEmpty()) {
            Toast.makeText(this, "No liked songs yet. Tap the 💚 heart icon to save songs!", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadOfflineQueue() {
        currentQueue.clear();
        currentQueue.addAll(offlineSongs);
        songAdapter.notifyDataSetChanged();
        tvSectionTitle.setText("Offline Music Library");
        tvTrackCount.setText(currentQueue.size() + " Songs");
        tvOnlineBadge.setText("📴 Offline");
    }

    private void performSearch() {
        final String q = etSearch.getText().toString().trim();
        if (q.isEmpty()) return;

        loadingSpinner.setVisibility(View.VISIBLE);
        tvSectionTitle.setText("Searching: \"" + q + "\"...");

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final List<Song> results = CloudMusicApi.search(q, 30);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loadingSpinner.setVisibility(View.GONE);
                        if (!results.isEmpty()) {
                            currentQueue.clear();
                            currentQueue.addAll(results);
                            songAdapter.notifyDataSetChanged();
                            tvSectionTitle.setText("Results for \"" + q + "\"");
                            tvTrackCount.setText(results.size() + " Songs");
                            tvOnlineBadge.setText("🟢 Online (Full HD)");
                        } else {
                            Toast.makeText(MainActivity.this, "No tracks found for \"" + q + "\"", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private void fetchTrendingHits() {
        loadingSpinner.setVisibility(View.VISIBLE);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final List<Song> results = CloudMusicApi.search("Trending Hindi Hits", 25);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loadingSpinner.setVisibility(View.GONE);
                        if (!results.isEmpty()) {
                            currentQueue.clear();
                            currentQueue.addAll(offlineSongs);
                            currentQueue.addAll(results);
                            songAdapter.notifyDataSetChanged();
                            tvSectionTitle.setText("Worldwide Trending & Offline Hits");
                            tvTrackCount.setText(currentQueue.size() + " Songs");
                            tvOnlineBadge.setText("🟢 Online (Full HD)");
                        }
                    }
                });
            }
        });
    }

    private void fetchMoodTracks(final String mood) {
        loadingSpinner.setVisibility(View.VISIBLE);
        tvSectionTitle.setText("Loading " + mood + " Music...");

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final List<Song> results = CloudMusicApi.getMoodSongs(mood, 25);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loadingSpinner.setVisibility(View.GONE);
                        currentQueue.clear();
                        for (Song s : offlineSongs) {
                            if (mood.equalsIgnoreCase(s.mood)) {
                                currentQueue.add(s);
                            }
                        }
                        if (!results.isEmpty()) {
                            currentQueue.addAll(results);
                            tvOnlineBadge.setText("🟢 Online (Full HD)");
                        }
                        songAdapter.notifyDataSetChanged();
                        tvSectionTitle.setText(mood + " Mood Playlist");
                        tvTrackCount.setText(currentQueue.size() + " Songs");
                    }
                });
            }
        });
    }

    private void playTrackAtIndex(int index) {
        if (index < 0 || index >= currentQueue.size()) return;
        currentSongIndex = index;
        final Song song = currentQueue.get(index);

        songAdapter.setPlayingIndex(index);
        tvPlayerTitle.setText(song.title);
        tvPlayerArtist.setText(song.artist);

        tvFullTitle.setText(song.title);
        tvFullArtist.setText(song.artist + (song.album.isEmpty() ? "" : " • " + song.album));
        tvFullPlaylistTitle.setText(tvSectionTitle.getText());

        btnPlay.setImageResource(R.drawable.ic_pause_black);
        btnFullPlay.setImageResource(R.drawable.ic_pause_black);

        updateLikeButtonState();

        // Update Artwork
        if (song.isOffline || song.artworkUrl == null || song.artworkUrl.isEmpty()) {
            playerArt.setImageResource(R.drawable.ic_music_note);
            ivFullArt.setImageResource(R.drawable.ic_music_note);
        } else {
            songAdapter.loadArtwork(song.artworkUrl, playerArt);
            songAdapter.loadArtwork(song.artworkUrl, ivFullArt);
        }

        // Check if downloaded
        boolean isDownloaded = FavoritesManager.isSongDownloaded(this, song);
        btnFullDownload.setImageResource(isDownloaded ? R.drawable.ic_download_done : R.drawable.ic_download);
        btnFullDownload.setColorFilter(isDownloaded ? Color.parseColor("#00e676") : Color.parseColor("#a0a0b8"));

        releasePlayer();

        try {
            if (song.rawResId > 0) {
                // Native Raw Resource
                mediaPlayer = MediaPlayer.create(this, song.rawResId);
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        onTrackFinished();
                    }
                });
                int total = mediaPlayer.getDuration();
                if (total > 0) {
                    fullSeekBar.setMax(total);
                    tvFullTotalTime.setText(formatDuration(total));
                }
                mediaPlayer.start();
                startSeekUpdate();
                Toast.makeText(this, "Playing: " + song.title, Toast.LENGTH_SHORT).show();
            } else {
                // Check if we have an offline saved copy on device storage
                File downloadedFile = FavoritesManager.getDownloadFile(this, song);
                String sourcePath;
                if (downloadedFile.exists() && downloadedFile.length() > 50000) {
                    sourcePath = downloadedFile.getAbsolutePath();
                    Toast.makeText(this, "Playing Saved Offline Track 📴", Toast.LENGTH_SHORT).show();
                } else {
                    sourcePath = song.streamUrl;
                    Toast.makeText(this, "Streaming: " + song.title, Toast.LENGTH_SHORT).show();
                }

                if (sourcePath != null && !sourcePath.isEmpty()) {
                    mediaPlayer = new MediaPlayer();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        AudioAttributes attrs = new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build();
                        mediaPlayer.setAudioAttributes(attrs);
                    }
                    mediaPlayer.setDataSource(sourcePath);
                    mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            int total = mp.getDuration();
                            if (total > 0) {
                                fullSeekBar.setMax(total);
                                tvFullTotalTime.setText(formatDuration(total));
                            }
                            mp.start();
                            startSeekUpdate();
                        }
                    });
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            onTrackFinished();
                        }
                    });
                    mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override
                        public boolean onError(MediaPlayer mp, int what, int extra) {
                            Toast.makeText(MainActivity.this, "Stream unavailable. Skipping...", Toast.LENGTH_SHORT).show();
                            playNext();
                            return true;
                        }
                    });
                    mediaPlayer.prepareAsync();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Playback error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onTrackFinished() {
        if (sleepAtSongEnd) {
            sleepAtSongEnd = false;
            releasePlayer();
            btnPlay.setImageResource(R.drawable.ic_play_arrow);
            btnFullPlay.setImageResource(R.drawable.ic_play_arrow);
            tvTimerStatus.setVisibility(View.GONE);
            Toast.makeText(this, "Sleep timer ended. Goodnight! 🌙", Toast.LENGTH_LONG).show();
            return;
        }

        if (repeatMode == 2) {
            // Repeat One
            playTrackAtIndex(currentSongIndex);
        } else if (repeatMode == 1 || isShuffle) {
            playNext();
        } else {
            // Repeat Off
            if (currentSongIndex < currentQueue.size() - 1) {
                playNext();
            } else {
                releasePlayer();
                btnPlay.setImageResource(R.drawable.ic_play_arrow);
                btnFullPlay.setImageResource(R.drawable.ic_play_arrow);
            }
        }
    }

    private void playNext() {
        if (currentQueue.isEmpty()) return;
        int next;
        if (isShuffle && currentQueue.size() > 1) {
            do {
                next = random.nextInt(currentQueue.size());
            } while (next == currentSongIndex);
        } else {
            next = (currentSongIndex + 1) % currentQueue.size();
        }
        playTrackAtIndex(next);
    }

    private void playPrevious() {
        if (currentQueue.isEmpty()) return;
        int prev = (currentSongIndex - 1 + currentQueue.size()) % currentQueue.size();
        playTrackAtIndex(prev);
    }

    private void showSleepTimerDialog() {
        final String[] options = new String[]{"Off", "15 Minutes", "30 Minutes", "45 Minutes", "60 Minutes", "End of this song"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🌙 Sleep Timer");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    sleepTimerMinutes = 0;
                    sleepTimerEndTime = 0;
                    sleepAtSongEnd = false;
                    tvTimerStatus.setVisibility(View.GONE);
                    btnSleepTimer.setColorFilter(Color.parseColor("#a0a0b8"));
                    Toast.makeText(MainActivity.this, "Sleep timer turned off", Toast.LENGTH_SHORT).show();
                } else if (which == 5) {
                    sleepAtSongEnd = true;
                    sleepTimerMinutes = 0;
                    sleepTimerEndTime = 0;
                    tvTimerStatus.setVisibility(View.VISIBLE);
                    tvTimerStatus.setText("🌙 Sleep timer: End of track");
                    btnSleepTimer.setColorFilter(Color.parseColor("#00f5d4"));
                    Toast.makeText(MainActivity.this, "Sleep timer set: End of track 🌙", Toast.LENGTH_SHORT).show();
                } else {
                    int[] mins = new int[]{0, 15, 30, 45, 60};
                    sleepTimerMinutes = mins[which];
                    sleepAtSongEnd = false;
                    sleepTimerEndTime = System.currentTimeMillis() + (sleepTimerMinutes * 60 * 1000L);
                    tvTimerStatus.setVisibility(View.VISIBLE);
                    btnSleepTimer.setColorFilter(Color.parseColor("#00f5d4"));
                    Toast.makeText(MainActivity.this, "Sleep timer set for " + sleepTimerMinutes + " minutes 🌙", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.show();
    }

    private void updateSleepTimerCountdown() {
        if (sleepTimerEndTime > 0) {
            long remaining = sleepTimerEndTime - System.currentTimeMillis();
            if (remaining <= 0) {
                sleepTimerEndTime = 0;
                sleepTimerMinutes = 0;
                tvTimerStatus.setVisibility(View.GONE);
                btnSleepTimer.setColorFilter(Color.parseColor("#a0a0b8"));
                releasePlayer();
                btnPlay.setImageResource(R.drawable.ic_play_arrow);
                btnFullPlay.setImageResource(R.drawable.ic_play_arrow);
                Toast.makeText(this, "Sleep timer finished. Goodnight! 🌙", Toast.LENGTH_LONG).show();
            } else {
                int sec = (int) (remaining / 1000);
                int m = sec / 60;
                int s = sec % 60;
                tvTimerStatus.setVisibility(View.VISIBLE);
                tvTimerStatus.setText("🌙 Sleep timer: " + String.format("%02d:%02d", m, s));
            }
        }
    }

    private void startSeekUpdate() {
        seekHandler.removeCallbacks(seekUpdater);
        seekHandler.post(seekUpdater);
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private static String formatDuration(int millis) {
        int sec = millis / 1000;
        int m = sec / 60;
        int s = sec % 60;
        return String.format("%02d:%02d", m, s);
    }

    @Override
    public void onBackPressed() {
        if (nowPlayingOverlay != null && nowPlayingOverlay.getVisibility() == View.VISIBLE) {
            nowPlayingOverlay.setVisibility(View.GONE);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        seekHandler.removeCallbacks(seekUpdater);
        releasePlayer();
        executor.shutdownNow();
        super.onDestroy();
    }
}
