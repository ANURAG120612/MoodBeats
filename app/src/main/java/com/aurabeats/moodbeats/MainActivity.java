package com.aurabeats.moodbeats;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
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
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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

    // YouTube Audio/Video Engine WebView
    private WebView webYoutubePlayer;
    private boolean isYoutubeReady = false;
    private boolean isYoutubePlaying = false;

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
        setupYouTubePlayer();
        setupPlayerControls();
        setupMoodPills();
        setupBottomNav();
        setupNowPlayingOverlay();

        // Start with Trending Hits immediately
        fetchTrendingHits();
    }

    private void initOfflineLibrary() {
        offlineSongs.clear();
        offlineSongs.add(new Song("off_1", "Ilahi", "Arijit Singh", "Yeh Jawaani Hai Deewani", "03:49", "https://i.ytimg.com/vi/6wZ_a3T8uio/hqdefault.jpg", "6wZ_a3T8uio", R.raw.ilahi, "Happy", true));
        offlineSongs.add(new Song("off_2", "Kesariya", "Arijit Singh", "Brahmastra", "04:28", "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg", "BddP6PYo2gs", R.raw.kesariya, "Romantic", true));
        offlineSongs.add(new Song("off_3", "Channa Mereya", "Arijit Singh", "Ae Dil Hai Mushkil", "04:49", "https://i.ytimg.com/vi/284Ov7ysmfA/hqdefault.jpg", "284Ov7ysmfA", R.raw.channa_mereya, "Sad", true));
        offlineSongs.add(new Song("off_4", "O Mere Dil Ke Chain", "Kishore Kumar", "Mere Jeevan Saathi", "04:32", "https://i.ytimg.com/vi/w_h9O0y4uEI/hqdefault.jpg", "w_h9O0y4uEI", R.raw.dil_ke_chain, "Calm", true));
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
        webYoutubePlayer = findViewById(R.id.web_youtube_player);

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

        // Populate initial queue with offline tracks so UI is never empty
        currentQueue.clear();
        currentQueue.addAll(offlineSongs);

        songAdapter = new SongAdapter(this, currentQueue, favoritesManager);
        songAdapter.setOnFavoriteChangeListener(new SongAdapter.OnFavoriteChangeListener() {
            @Override
            public void onFavoriteChanged() {
                updateLikeButtonState();
            }
        });
        songAdapter.setOnSongClickListener(new SongAdapter.OnSongClickListener() {
            @Override
            public void onSongClick(Song song, int position) {
                playTrackAtIndex(position);
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

    private void setupYouTubePlayer() {
        if (webYoutubePlayer == null) return;
        WebSettings settings = webYoutubePlayer.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        webYoutubePlayer.setWebChromeClient(new WebChromeClient());
        webYoutubePlayer.setWebViewClient(new WebViewClient());
        webYoutubePlayer.addJavascriptInterface(new YouTubeInterface(), "Android");

        String html = "<!DOCTYPE html><html><head>"
            + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
            + "<script src='https://www.youtube.com/iframe_api'></script>"
            + "</head><body style='margin:0;padding:0;background-color:#0a0a0f;'>"
            + "<div id='player'></div>"
            + "<script>"
            + "var player; var isReady=false; var pendingId=null;"
            + "function onYouTubeIframeAPIReady(){"
            + "  player = new YT.Player('player',{"
            + "    height:'100%', width:'100%', videoId:'',"
            + "    playerVars:{'autoplay':1,'playsinline':1,'controls':0,'rel':0,'showinfo':0,'modestbranding':1},"
            + "    events:{'onReady':function(e){isReady=true; if(window.Android) window.Android.onPlayerReady(); if(pendingId){loadVideo(pendingId); pendingId=null;}},"
            + "            'onStateChange':function(e){if(window.Android) window.Android.onStateChange(e.data);},"
            + "            'onError':function(e){if(window.Android) window.Android.onError(e.data);}}"
            + "  });"
            + "}"
            + "function loadVideo(id){if(!isReady||!player||!player.loadVideoById){pendingId=id; return;} player.loadVideoById(id); player.playVideo();}"
            + "function playVideo(){if(player&&player.playVideo) player.playVideo();}"
            + "function pauseVideo(){if(player&&player.pauseVideo) player.pauseVideo();}"
            + "function seekTo(s){if(player&&player.seekTo) player.seekTo(s,true);}"
            + "setInterval(function(){"
            + "  if(player&&player.getCurrentTime&&player.getDuration){"
            + "    var c=player.getCurrentTime()||0; var d=player.getDuration()||0;"
            + "    if(window.Android && d>0) window.Android.onProgress(c,d);"
            + "  }"
            + "}, 500);"
            + "</script></body></html>";

        webYoutubePlayer.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
    }

    public class YouTubeInterface {
        @JavascriptInterface
        public void onPlayerReady() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    isYoutubeReady = true;
                }
            });
        }

        @JavascriptInterface
        public void onStateChange(final int state) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (state == 1) { // Playing
                        isYoutubePlaying = true;
                        btnPlay.setImageResource(R.drawable.ic_pause_black);
                        btnFullPlay.setImageResource(R.drawable.ic_pause_black);
                    } else if (state == 2) { // Paused
                        isYoutubePlaying = false;
                        btnPlay.setImageResource(R.drawable.ic_play_arrow);
                        btnFullPlay.setImageResource(R.drawable.ic_play_arrow);
                    } else if (state == 0) { // Ended
                        isYoutubePlaying = false;
                        onTrackFinished();
                    }
                }
            });
        }

        @JavascriptInterface
        public void onError(final int code) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "YouTube stream unavailable, skipping...", Toast.LENGTH_SHORT).show();
                    playNext();
                }
            });
        }

        @JavascriptInterface
        public void onProgress(final float currentSec, final float totalSec) {
            if (isUserSeeking) return;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    int posMs = (int) (currentSec * 1000);
                    int totalMs = (int) (totalSec * 1000);
                    if (totalMs > 0) {
                        fullSeekBar.setMax(totalMs);
                        fullSeekBar.setProgress(posMs);
                        tvFullCurrentTime.setText(formatDuration(posMs));
                        tvFullTotalTime.setText(formatDuration(totalMs));
                    }
                }
            });
        }
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
                searchBarLayout.setVisibility(View.VISIBLE);
                etSearch.requestFocus();
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
        navIconHome.setColorFilter(index == 0 ? Color.parseColor("#00f5d4") : Color.parseColor("#a0a0b8"));
        navTextHome.setTextColor(index == 0 ? Color.parseColor("#00f5d4") : Color.parseColor("#a0a0b8"));

        navIconSearch.setColorFilter(index == 1 ? Color.parseColor("#00f5d4") : Color.parseColor("#a0a0b8"));
        navTextSearch.setTextColor(index == 1 ? Color.parseColor("#00f5d4") : Color.parseColor("#a0a0b8"));

        navIconLibrary.setColorFilter(index == 2 ? Color.parseColor("#00f5d4") : Color.parseColor("#a0a0b8"));
        navTextLibrary.setTextColor(index == 2 ? Color.parseColor("#00f5d4") : Color.parseColor("#a0a0b8"));
    }

    private void setupPlayerControls() {
        miniPlayerLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentSongIndex >= 0 && currentSongIndex < currentQueue.size()) {
                    nowPlayingOverlay.setVisibility(View.VISIBLE);
                }
            }
        });

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayPause();
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNext();
            }
        });

        btnMiniLike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCurrentSongLike();
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
                toggleCurrentSongLike();
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
                if (currentSongIndex >= 0 && currentSongIndex < currentQueue.size()) {
                    Song s = currentQueue.get(currentSongIndex);
                    if (s.isOffline) {
                        if (mediaPlayer != null) {
                            mediaPlayer.seekTo(seekBar.getProgress());
                        }
                    } else {
                        float sec = seekBar.getProgress() / 1000f;
                        if (webYoutubePlayer != null) {
                            webYoutubePlayer.loadUrl("javascript:seekTo(" + sec + ")");
                        }
                    }
                }
            }
        });
    }

    private void togglePlayPause() {
        if (currentSongIndex < 0 || currentSongIndex >= currentQueue.size()) {
            if (!currentQueue.isEmpty()) {
                playTrackAtIndex(0);
            }
            return;
        }

        Song song = currentQueue.get(currentSongIndex);
        if (song.isOffline) {
            if (mediaPlayer == null) {
                playTrackAtIndex(currentSongIndex);
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
        } else {
            if (isYoutubePlaying) {
                if (webYoutubePlayer != null) webYoutubePlayer.loadUrl("javascript:pauseVideo()");
                isYoutubePlaying = false;
                btnPlay.setImageResource(R.drawable.ic_play_arrow);
                btnFullPlay.setImageResource(R.drawable.ic_play_arrow);
            } else {
                if (webYoutubePlayer != null) webYoutubePlayer.loadUrl("javascript:playVideo()");
                isYoutubePlaying = true;
                btnPlay.setImageResource(R.drawable.ic_pause_black);
                btnFullPlay.setImageResource(R.drawable.ic_pause_black);
            }
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
        currentQueue.clear();
        currentQueue.addAll(favoritesManager.getLikedSongs());
        if (currentQueue.isEmpty()) {
            currentQueue.addAll(offlineSongs);
            Toast.makeText(this, "No liked songs yet! Showing offline library.", Toast.LENGTH_SHORT).show();
        }
        songAdapter.notifyDataSetChanged();
        tvSectionTitle.setText("💚 Your Liked Songs");
        tvTrackCount.setText(currentQueue.size() + " Songs");
    }

    private void loadOfflineQueue() {
        currentQueue.clear();
        currentQueue.addAll(offlineSongs);
        songAdapter.notifyDataSetChanged();
        tvSectionTitle.setText("📴 100% Offline Songs (No Internet)");
        tvTrackCount.setText(currentQueue.size() + " Songs");
        tvOnlineBadge.setText("📴 Offline Mode");
    }

    private void performSearch() {
        final String query = etSearch.getText().toString().trim();
        if (query.isEmpty()) return;

        loadingSpinner.setVisibility(View.VISIBLE);
        tvSectionTitle.setText("Searching YouTube for \"" + query + "\"...");

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final List<Song> results = YouTubeMusicApi.search(query, 25);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loadingSpinner.setVisibility(View.GONE);
                        currentQueue.clear();
                        currentQueue.addAll(offlineSongs);
                        currentQueue.addAll(results);
                        songAdapter.notifyDataSetChanged();
                        tvSectionTitle.setText("Search Results: " + query);
                        tvTrackCount.setText(currentQueue.size() + " Songs");
                        tvOnlineBadge.setText("▶ YouTube");
                        if (!results.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Found " + results.size() + " YouTube songs!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private void fetchTrendingHits() {
        loadingSpinner.setVisibility(View.VISIBLE);
        tvSectionTitle.setText("Loading YouTube Trending Hits...");

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final List<Song> results = YouTubeMusicApi.getMoodSongs("Trending", 25);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loadingSpinner.setVisibility(View.GONE);
                        currentQueue.clear();
                        currentQueue.addAll(offlineSongs);
                        currentQueue.addAll(results);
                        songAdapter.notifyDataSetChanged();
                        tvSectionTitle.setText("Trending Hits & Offline Library");
                        tvTrackCount.setText(currentQueue.size() + " Songs");
                        tvOnlineBadge.setText("▶ YouTube Music");
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
                final List<Song> results = YouTubeMusicApi.getMoodSongs(mood, 25);
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
                        currentQueue.addAll(results);
                        songAdapter.notifyDataSetChanged();
                        tvSectionTitle.setText(mood + " Mood Playlist");
                        tvTrackCount.setText(currentQueue.size() + " Songs");
                        tvOnlineBadge.setText("▶ YouTube Music");
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
        if (song.artworkUrl != null && !song.artworkUrl.isEmpty()) {
            songAdapter.loadArtwork(song.artworkUrl, playerArt);
            songAdapter.loadArtwork(song.artworkUrl, ivFullArt);
        } else {
            playerArt.setImageResource(R.drawable.ic_music_note);
            ivFullArt.setImageResource(R.drawable.ic_music_note);
        }

        // Check if downloaded / favorite
        boolean isLiked = favoritesManager.isLiked(song.id);
        btnFullDownload.setImageResource(isLiked ? R.drawable.ic_download_done : R.drawable.ic_download);
        btnFullDownload.setColorFilter(isLiked ? Color.parseColor("#00e676") : Color.parseColor("#a0a0b8"));

        // Release native player before switching
        releasePlayer();

        try {
            if (song.isOffline && song.rawResId > 0) {
                // Pause YouTube player if active
                if (webYoutubePlayer != null) {
                    webYoutubePlayer.loadUrl("javascript:pauseVideo()");
                }
                isYoutubePlaying = false;

                // Native Raw Resource playback
                mediaPlayer = MediaPlayer.create(this, song.rawResId);
                if (mediaPlayer != null) {
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
                    Toast.makeText(this, "Playing Offline: " + song.title, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Could not open audio track", Toast.LENGTH_SHORT).show();
                }
            } else {
                // YouTube Music Playback
                String videoId = song.streamUrl;
                if (videoId == null || videoId.isEmpty()) {
                    videoId = song.id.replace("yt_", "");
                }
                if (videoId.contains("watch?v=")) {
                    int vIdx = videoId.indexOf("watch?v=");
                    videoId = videoId.substring(vIdx + 8);
                }

                if (webYoutubePlayer != null && !videoId.isEmpty()) {
                    isYoutubePlaying = true;
                    webYoutubePlayer.loadUrl("javascript:loadVideo('" + videoId + "')");
                    Toast.makeText(this, "Playing YouTube: " + song.title, Toast.LENGTH_SHORT).show();
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
            if (webYoutubePlayer != null) webYoutubePlayer.loadUrl("javascript:pauseVideo()");
            btnPlay.setImageResource(R.drawable.ic_play_arrow);
            btnFullPlay.setImageResource(R.drawable.ic_play_arrow);
            tvTimerStatus.setVisibility(View.GONE);
            Toast.makeText(this, "Sleep timer ended. Goodnight! 🌙", Toast.LENGTH_LONG).show();
            return;
        }

        if (repeatMode == 2) {
            // Repeat Current Track
            playTrackAtIndex(currentSongIndex);
        } else if (repeatMode == 1) {
            // Repeat All
            playNext();
        } else {
            // Repeat Off
            if (currentSongIndex < currentQueue.size() - 1) {
                playNext();
            } else {
                btnPlay.setImageResource(R.drawable.ic_play_arrow);
                btnFullPlay.setImageResource(R.drawable.ic_play_arrow);
            }
        }
    }

    private void playNext() {
        if (currentQueue.isEmpty()) return;
        if (isShuffle) {
            int next = random.nextInt(currentQueue.size());
            playTrackAtIndex(next);
        } else {
            int next = (currentSongIndex + 1) % currentQueue.size();
            playTrackAtIndex(next);
        }
    }

    private void playPrevious() {
        if (currentQueue.isEmpty()) return;
        int prev = (currentSongIndex - 1 + currentQueue.size()) % currentQueue.size();
        playTrackAtIndex(prev);
    }

    private void showSleepTimerDialog() {
        final String[] options = new String[]{"Off", "15 Minutes", "30 Minutes", "45 Minutes", "60 Minutes", "End of this song"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⏱ Sleep Timer");
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
                    tvTimerStatus.setText("⏱ Sleep timer: End of track");
                    btnSleepTimer.setColorFilter(Color.parseColor("#00f5d4"));
                    Toast.makeText(MainActivity.this, "Sleep timer set: End of track 🌙", Toast.LENGTH_SHORT).show();
                } else {
                    int[] mins = new int[]{0, 15, 30, 45, 60};
                    sleepTimerMinutes = mins[which];
                    sleepAtSongEnd = false;
                    sleepTimerEndTime = System.currentTimeMillis() + (sleepTimerMinutes * 60 * 1000L);
                    tvTimerStatus.setVisibility(View.VISIBLE);
                    btnSleepTimer.setColorFilter(Color.parseColor("#00f5d4"));
                    Toast.makeText(MainActivity.this, "Sleep timer: " + sleepTimerMinutes + " min 🌙", Toast.LENGTH_SHORT).show();
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
                releasePlayer();
                if (webYoutubePlayer != null) webYoutubePlayer.loadUrl("javascript:pauseVideo()");
                btnPlay.setImageResource(R.drawable.ic_play_arrow);
                btnFullPlay.setImageResource(R.drawable.ic_play_arrow);
                tvTimerStatus.setVisibility(View.GONE);
                btnSleepTimer.setColorFilter(Color.parseColor("#a0a0b8"));
                Toast.makeText(this, "Sleep timer expired. Playback paused.", Toast.LENGTH_LONG).show();
            } else {
                int sec = (int) (remaining / 1000);
                int m = sec / 60;
                int s = sec % 60;
                tvTimerStatus.setVisibility(View.VISIBLE);
                tvTimerStatus.setText("⏱ Sleep timer: " + String.format("%02d:%02d", m, s));
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
        if (webYoutubePlayer != null) {
            try {
                webYoutubePlayer.loadUrl("about:blank");
                webYoutubePlayer.destroy();
            } catch (Exception ignored) {}
        }
        executor.shutdownNow();
        super.onDestroy();
    }
}
