/**
 * MoodBeats Standalone Engine
 * Enables MoodBeats to operate completely independently like Spotify:
 * - Online: Direct cloud music catalog (search, stream, mood recommendation)
 * - Offline: Plays bundled and downloaded tracks with zero internet / zero PC needed
 */

const MoodBeatsEngine = (function () {
    const DB_NAME = 'MoodBeatsOfflineDB';
    const DB_VERSION = 1;
    const STORE_NAME = 'downloaded_songs';
    let db = null;

    // Bundled High-Quality Offline Songs
    const BUNDLED_OFFLINE_SONGS = [
        {
            title: "Ilahi",
            artist: "Arijit Singh",
            album: "Yeh Jawaani Hai Deewani",
            duration: "3:49",
            mood: "Happy",
            valence: 0.92,
            energy: 0.88,
            thumbnail_url: "static/img/aurabeats_logo.png",
            stream_url: "music/ilahi.mp3",
            source: "offline"
        },
        {
            title: "Kesariya",
            artist: "Arijit Singh",
            album: "Brahmastra",
            duration: "4:28",
            mood: "Romantic",
            valence: 0.85,
            energy: 0.70,
            thumbnail_url: "static/img/aurabeats_logo.png",
            stream_url: "music/kesariya.mp3",
            source: "offline"
        },
        {
            title: "Channa Mereya",
            artist: "Arijit Singh",
            album: "Ae Dil Hai Mushkil",
            duration: "4:49",
            mood: "Sad",
            valence: 0.25,
            energy: 0.40,
            thumbnail_url: "static/img/aurabeats_logo.png",
            stream_url: "music/channa_mereya.mp3",
            source: "offline"
        },
        {
            title: "O Mere Dil Ke Chain",
            artist: "Kishore Kumar",
            album: "Mere Jeevan Saathi",
            duration: "4:32",
            mood: "Calm",
            valence: 0.75,
            energy: 0.45,
            thumbnail_url: "static/img/aurabeats_logo.png",
            stream_url: "music/dil_ke_chain.mp3",
            source: "offline"
        }
    ];

    // Mood query mappings for worldwide cloud search
    const MOOD_SEARCH_TERMS = {
        'Happy': 'Upbeat Pop Hits Uplifting',
        'Energetic': 'Workout EDM Fast Beats Adrenaline',
        'Calm': 'Lo-Fi Chill Beats Acoustic Peaceful',
        'Sad': 'Acoustic Soulful Sad Piano Ballads',
        'Romantic': 'Romantic Love Songs Acoustic Melody'
    };

    // Open IndexedDB for offline downloads
    function initDB() {
        return new Promise((resolve) => {
            if (!window.indexedDB) {
                console.warn('IndexedDB not supported in this environment.');
                return resolve(null);
            }
            const request = indexedDB.open(DB_NAME, DB_VERSION);
            request.onupgradeneeded = function (e) {
                const dbInstance = e.target.result;
                if (!dbInstance.objectStoreNames.contains(STORE_NAME)) {
                    dbInstance.createObjectStore(STORE_NAME, { keyPath: 'id' });
                }
            };
            request.onsuccess = function (e) {
                db = e.target.result;
                resolve(db);
            };
            request.onerror = function (e) {
                console.error('IndexedDB open error:', e);
                resolve(null);
            };
        });
    }

    // Format duration helper (sec to mm:ss)
    function formatTime(sec) {
        if (isNaN(sec) || sec < 0) return "03:30";
        let m = Math.floor(sec / 60);
        let s = Math.floor(sec % 60);
        return `${m < 10 ? '0' : ''}${m}:${s < 10 ? '0' : ''}${s}`;
    }

    // Convert raw iTunes / Cloud song to MoodBeats Song format
    function normalizeCloudSong(item, fallbackMood = 'Happy') {
        const title = item.trackName || item.name || 'Unknown Track';
        const artist = item.artistName || item.artist || 'Unknown Artist';
        const album = item.collectionName || item.album || 'Worldwide Single';
        const duration = item.trackTimeMillis ? formatTime(item.trackTimeMillis / 1000) : '03:30';
        let thumb = item.artworkUrl100 || 'static/img/aurabeats_logo.png';
        if (thumb && thumb.includes('100x100bb')) {
            thumb = thumb.replace('100x100bb', '600x600bb');
        }

        return {
            id: 'cloud_' + (item.trackId || Math.random().toString(36).substr(2, 9)),
            title: title,
            artist: artist,
            album: album,
            duration: duration,
            thumbnail_url: thumb,
            stream_url: item.previewUrl || item.stream_url || '',
            mood: fallbackMood,
            source: 'cloud'
        };
    }

    // Search cloud music without PC server
    async function searchCloudMusic(query, limit = 25) {
        if (!query || !query.trim()) return [];
        const cleanQ = query.trim();

        // 1. If PC server explicitly configured and user wants it, try it first
        const pcServer = localStorage.getItem('moodbeats_server');
        if (pcServer) {
            try {
                const res = await fetch(`${pcServer.replace(/\/$/, '')}/api/search?q=${encodeURIComponent(cleanQ)}`, { signal: AbortSignal.timeout(3000) });
                if (res.ok) {
                    const tracks = await res.json();
                    if (tracks && tracks.length > 0) return tracks;
                }
            } catch (e) {
                console.log('PC server not reachable for search, falling back to direct cloud:', e);
            }
        }

        // 2. Direct Cloud Search via iTunes Worldwide Catalog
        try {
            const url = `https://itunes.apple.com/search?term=${encodeURIComponent(cleanQ)}&entity=song&limit=${limit}`;
            const res = await fetch(url);
            const data = await res.json();
            if (data && data.results && data.results.length > 0) {
                return data.results.map(item => normalizeCloudSong(item));
            }
        } catch (e) {
            console.warn('Cloud search error, falling back to offline search:', e);
        }

        // 3. Offline Search Fallback
        return searchOfflineSongs(cleanQ);
    }

    // Get Mood Recommendations
    async function getMoodSongs(mood = 'Happy', valence = 0.9, energy = 0.8, limit = 25) {
        // 1. Try PC server if configured
        const pcServer = localStorage.getItem('moodbeats_server');
        if (pcServer) {
            try {
                const res = await fetch(`${pcServer.replace(/\/$/, '')}/api/recommend`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ mode: 'preset', mood: mood, valence: valence, energy: energy }),
                    signal: AbortSignal.timeout(3000)
                });
                if (res.ok) {
                    const data = await res.json();
                    if (data && data.recommendations && data.recommendations.length > 0) {
                        return data.recommendations;
                    }
                }
            } catch (e) {
                console.log('PC server not reachable for mood, falling back to cloud/offline:', e);
            }
        }

        // 2. Direct Cloud Mood Search
        if (navigator.onLine) {
            const term = MOOD_SEARCH_TERMS[mood] || `${mood} music hits`;
            try {
                const url = `https://itunes.apple.com/search?term=${encodeURIComponent(term)}&entity=song&limit=${limit}`;
                const res = await fetch(url);
                const data = await res.json();
                if (data && data.results && data.results.length > 0) {
                    return data.results.map(item => normalizeCloudSong(item, mood));
                }
            } catch (e) {
                console.warn('Cloud mood search error:', e);
            }
        }

        // 3. Offline Mood Fallback
        return getOfflineMoodSongs(mood);
    }

    // Get Endless Radio
    async function getEndlessRadio(seedSong, limit = 20) {
        if (!seedSong) return getOfflineTracks();

        if (navigator.onLine) {
            const query = `${seedSong.artist} ${seedSong.title}`;
            const cloudResults = await searchCloudMusic(query, limit);
            if (cloudResults.length > 0) return cloudResults;
        }

        // Offline shuffle
        const allOffline = await getOfflineTracks();
        return allOffline.sort(() => 0.5 - Math.random());
    }

    // Offline Search
    async function searchOfflineSongs(query) {
        const q = query.toLowerCase();
        const all = await getOfflineTracks();
        return all.filter(s =>
            (s.title && s.title.toLowerCase().includes(q)) ||
            (s.artist && s.artist.toLowerCase().includes(q)) ||
            (s.album && s.album.toLowerCase().includes(q))
        );
    }

    // Offline Mood Songs
    async function getOfflineMoodSongs(mood) {
        const all = await getOfflineTracks();
        const matches = all.filter(s => s.mood && s.mood.toLowerCase() === mood.toLowerCase());
        return matches.length > 0 ? matches : all;
    }

    // Get All Offline Tracks (Bundled + Downloaded)
    async function getOfflineTracks() {
        const downloaded = await getDownloadedTracks();
        return [...downloaded, ...BUNDLED_OFFLINE_SONGS];
    }

    // Download song for offline playback (into IndexedDB)
    async function downloadSong(song) {
        if (!song || !song.stream_url) return false;
        try {
            const res = await fetch(song.stream_url);
            const blob = await res.blob();
            if (!db) await initDB();

            const songRecord = {
                id: song.id || (song.title + '_' + song.artist),
                title: song.title,
                artist: song.artist,
                album: song.album || 'Downloaded',
                duration: song.duration || '03:30',
                thumbnail_url: song.thumbnail_url,
                mood: song.mood || 'Happy',
                audioBlob: blob,
                downloadedAt: new Date().toISOString(),
                source: 'downloaded'
            };

            return new Promise((resolve) => {
                const tx = db.transaction(STORE_NAME, 'readwrite');
                const store = tx.objectStore(STORE_NAME);
                store.put(songRecord);
                tx.oncomplete = () => resolve(true);
                tx.onerror = () => resolve(false);
            });
        } catch (e) {
            console.error('Download error:', e);
            return false;
        }
    }

    // Retrieve downloaded tracks from IndexedDB
    async function getDownloadedTracks() {
        if (!db) await initDB();
        if (!db) return [];

        return new Promise((resolve) => {
            const tx = db.transaction(STORE_NAME, 'readonly');
            const store = tx.objectStore(STORE_NAME);
            const req = store.getAll();
            req.onsuccess = function () {
                const list = req.result || [];
                const songs = list.map(item => {
                    const url = URL.createObjectURL(item.audioBlob);
                    return {
                        ...item,
                        stream_url: url,
                        source: 'offline_download'
                    };
                });
                resolve(songs);
            };
            req.onerror = function () {
                resolve([]);
            };
        });
    }

    // Feelings & Reflections (Local Persistence)
    function saveSongFeeling(title, artist, tag, story, userName = 'You') {
        try {
            const list = getSongFeelings();
            const newItem = {
                id: Date.now(),
                song_title: title,
                song_artist: artist,
                feeling_tag: tag,
                story_text: story,
                user_name: userName,
                created_at: new Date().toISOString().replace('T', ' ').substring(0, 19)
            };
            list.unshift(newItem);
            localStorage.setItem('moodbeats_saved_feelings', JSON.stringify(list.slice(0, 50)));
            return newItem;
        } catch (e) {
            console.error('Error saving feeling:', e);
            return null;
        }
    }

    function getSongFeelings(title = null) {
        try {
            const raw = localStorage.getItem('moodbeats_saved_feelings');
            let list = raw ? JSON.parse(raw) : [];
            if (list.length === 0) {
                list = [
                    {
                        id: 1,
                        song_title: "Ilahi",
                        song_artist: "Arijit Singh",
                        feeling_tag: "Energetic",
                        story_text: "Takes me back to mountain treks and sunset drives.",
                        user_name: "Explorer",
                        created_at: "Today"
                    },
                    {
                        id: 2,
                        song_title: "Channa Mereya",
                        song_artist: "Arijit Singh",
                        feeling_tag: "Reflective",
                        story_text: "Perfect song for late-night thoughts and solitude.",
                        user_name: "MelodyLover",
                        created_at: "Yesterday"
                    }
                ];
                localStorage.setItem('moodbeats_saved_feelings', JSON.stringify(list));
            }
            if (title) {
                return list.filter(item => item.song_title.toLowerCase() === title.toLowerCase());
            }
            return list;
        } catch (e) {
            return [];
        }
    }

    // Auto-initialize DB
    initDB();

    return {
        search: searchCloudMusic,
        getMoodSongs: getMoodSongs,
        getEndlessRadio: getEndlessRadio,
        getOfflineTracks: getOfflineTracks,
        downloadSong: downloadSong,
        getDownloadedTracks: getDownloadedTracks,
        saveFeeling: saveSongFeeling,
        getFeelings: getSongFeelings,
        bundledSongs: BUNDLED_OFFLINE_SONGS
    };
})();
