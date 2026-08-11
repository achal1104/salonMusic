import React, { useState, useEffect, useRef, useCallback } from "react";
import { Play, Pause, SkipBack, SkipForward, Music2, Headphones, Radio, Sparkles } from "lucide-react";
import STATIC_PLAYLIST from "./playlist.js";
import MODERN_PLAYLIST from "./playlistModern.js";

const API_BASE = "https://rajkumar-salon-backend.onrender.com";

// One background image per theme. Drop your own "modern salon" photo at
// /images/modernsalon.jpg (or change the path below) — the app swaps to it
// automatically when the toggle is switched.
const BACKGROUND_IMAGES = {
  vintage: "/images/rajkumarsalons.jpg",
  // modern: "/images/modernsalon.png",
  modern: "/images/rajkumarsalonfinal.jpeg",
};

// Salon name shown as a MOBILE-ONLY overlay on top of the hero photo.
// This is the shop's fixed brand identity, so it stays the same regardless
// of which theme (vintage/modern) is active — only the background photo and
// colors change between themes, not the brand name. CSS (.dx-salon-name)
// hides this overlay entirely on desktop/tablet and only shows it under the
// mobile media query.
const SALON_NAME = { title: "राजकुमार हेयर सैलून", sub: "Rajkumar Hair Salon" };

const STATIC_PLAYLISTS = {
  vintage: STATIC_PLAYLIST,
  modern: MODERN_PLAYLIST,
};

const PARTICLES = Array.from({ length: 18 }, (_, i) => ({
  id: i,
  x: Math.random() * 100,
  y: 30 + Math.random() * 60,
  r: 2 + Math.random() * 5,
  dur: 6 + Math.random() * 10,
  delay: -(Math.random() * 12),
  opacity: 0.12 + Math.random() * 0.22,
}));

function formatTime(sec) {
  if (!sec || isNaN(sec)) return "0:00";
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

function formatClock(d) {
  let h = d.getHours();
  const m = d.getMinutes();
  const ampm = h >= 12 ? "pm" : "am";
  h = h % 12 || 12;
  return `${h}:${m.toString().padStart(2, "0")} ${ampm}`;
}

export default function App() {
  const [theme, setTheme] = useState("vintage"); // "vintage" | "modern"
  const [index, setIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [mouse, setMouse] = useState({ x: 0.5, y: 0.5 });
  const [clock, setClock] = useState(() => formatClock(new Date()));
  const [onlineCount, setOnlineCount] = useState(41);
  const [playlist, setPlaylist] = useState(STATIC_PLAYLIST);

  // ── Modern background crop tuner ──────────────────────────────────────
  // Only active when the URL has ?tune=1, so it never appears for real
  // visitors. Lets you drag the modern theme's background crop live in the
  // browser (no code editing, no reload) instead of guessing CSS values
  // blind. The three values are pushed into CSS custom properties that
  // [data-theme='modern'] .dx-bg already reads from in App.css, so moving
  // a slider repositions the actual background instantly.
  const tuneMode = typeof window !== "undefined" && new URLSearchParams(window.location.search).get("tune") === "1";
  const [tuneX, setTuneX] = useState(66);
  const [tuneY, setTuneY] = useState(80);
  const [tuneZoom, setTuneZoom] = useState(130);

  useEffect(() => {
    if (!tuneMode) return;
    const root = document.documentElement;
    root.style.setProperty("--dx-modern-bg-x", `${tuneX}%`);
    root.style.setProperty("--dx-modern-bg-y", `${tuneY}%`);
    root.style.setProperty("--dx-modern-bg-zoom", `${tuneZoom / 100}`);
  }, [tuneMode, tuneX, tuneY, tuneZoom]);
  // ─────────────────────────────────────────────────────────────────────

  const pageRef = useRef(null);
  const bgRef = useRef(null);
  const audioRef = useRef(null);
  const progressTrackRef = useRef(null);
  const rafRef = useRef(null);
  const targetMouse = useRef({ x: 0.5, y: 0.5 });
  const currentMouse = useRef({ x: 0.5, y: 0.5 });
  const isPlayingRef = useRef(false);
  const syncIntervalRef = useRef(null);
  const isSyncingRef = useRef(false);

  // keep ref in sync so callbacks always see latest value
  useEffect(() => { isPlayingRef.current = isPlaying; }, [isPlaying]);

  // ── Sync with backend every 1 second ──
  useEffect(() => {
    const syncWithBackend = () => {
      if (isSyncingRef.current) return;
      fetch(`${API_BASE}/api/sync`)
        .then((res) => res.json())
        .then((s) => {
          const el = audioRef.current;
          if (!el) return;

          const newId = Number(s.songId);
          const newTheme = s.theme;
          const shouldPlay = Boolean(s.isPlaying);
          const serverTime = Number(s.currentTime);

          // sync theme
          if (newTheme && newTheme !== theme) setTheme(newTheme);

          // sync song index
          setPlaylist((prev) => {
            const idx = prev.findIndex((t) => Number(t.id) === newId);
            if (idx !== -1 && idx !== index) setIndex(idx);
            return prev;
          });

          // sync play/pause
          if (shouldPlay && !isPlayingRef.current) {
            setIsPlaying(true);
            if (el.readyState >= 2) el.play().catch(() => {});
          } else if (!shouldPlay && isPlayingRef.current) {
            setIsPlaying(false);
            el.pause();
          }

          // sync position — correct if drift > 1.5 seconds
          if (shouldPlay && Math.abs(el.currentTime - serverTime) > 1.5) {
            el.currentTime = serverTime;
          }
        })
        .catch(() => {});
    };
    syncIntervalRef.current = setInterval(syncWithBackend, 1000);
    return () => clearInterval(syncIntervalRef.current);
  }, [theme, index]);

  // push state to backend when user interacts
  const pushSync = (patch) => {
    isSyncingRef.current = true;
    fetch(`${API_BASE}/api/sync`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(patch),
    })
      .catch(() => {})
      .finally(() => { isSyncingRef.current = false; });
  };

  // fetch playlist from backend whenever the theme changes — only songs with audio_url,
  // falling back to the static per-theme playlist if the backend has nothing for it.
  useEffect(() => {
    let cancelled = false;
    fetch(`${API_BASE}/api/songs?theme=${theme}`)
      .then((res) => { if (!res.ok) throw new Error(); return res.json(); })
      .then((songs) => {
        if (cancelled) return;
        const withAudio = songs.filter((s) => s.audioUrl);
        setPlaylist(withAudio.length ? withAudio : STATIC_PLAYLISTS[theme]);
      })
      .catch(() => { if (!cancelled) setPlaylist(STATIC_PLAYLISTS[theme]); });
    return () => { cancelled = true; };
  }, [theme]);

  // switching themes starts the new playlist fresh, paused, from track 0
  useEffect(() => {
    setIndex(0);
    setIsPlaying(false);
    setProgress(0);
    setDuration(0);
  }, [theme]);

  const track = playlist[index];

  // clock
  useEffect(() => {
    const id = setInterval(() => setClock(formatClock(new Date())), 30000);
    return () => clearInterval(id);
  }, []);

  // online count fluctuation
  useEffect(() => {
    const id = setInterval(() => {
      setOnlineCount((c) => {
        const next = c + Math.floor(Math.random() * 5) - 2;
        return Math.min(55, Math.max(28, next));
      });
    }, 4000);
    return () => clearInterval(id);
  }, []);

  // mouse parallax — disabled on touch-only devices (phones/tablets)
  useEffect(() => {
    const isTouchOnly = window.matchMedia("(hover: none)").matches;
    if (isTouchOnly) return;
    const onMove = (e) => {
      const rect = pageRef.current?.getBoundingClientRect();
      if (!rect) return;
      targetMouse.current = {
        x: (e.clientX - rect.left) / rect.width,
        y: (e.clientY - rect.top) / rect.height,
      };
    };
    window.addEventListener("mousemove", onMove);
    const tick = () => {
      const t = targetMouse.current;
      const c = currentMouse.current;
      c.x += (t.x - c.x) * 0.04;
      c.y += (t.y - c.y) * 0.04;
      setMouse({ x: c.x, y: c.y });
      rafRef.current = requestAnimationFrame(tick);
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => {
      window.removeEventListener("mousemove", onMove);
      cancelAnimationFrame(rafRef.current);
    };
  }, []);

  // load track when index/playlist changes
  useEffect(() => {
    const el = audioRef.current;
    if (!el || !track?.id) return;
    const audioUrl = track.audioUrl
      ? (track.audioUrl.startsWith("http") ? track.audioUrl : `${API_BASE}/api/songs/${track.id}/audio`)
      : `${API_BASE}/api/songs/${track.id}/audio`;
    el.src = audioUrl;
    el.load();
    setProgress(0);
    setDuration(0);
    // play immediately as soon as enough data is loaded
    if (isPlayingRef.current) {
      const tryPlay = () => {
        el.play().catch(() => {});
        el.removeEventListener("loadeddata", tryPlay);
      };
      el.addEventListener("loadeddata", tryPlay);
    }
  }, [index, playlist]);

  // play/pause toggle — immediate
  useEffect(() => {
    const el = audioRef.current;
    if (!el) return;
    if (isPlaying) {
      // if src not loaded yet, load then play
      if (el.readyState >= 2) {
        el.play().catch(() => {});
      } else {
        const tryPlay = () => {
          el.play().catch(() => {});
          el.removeEventListener("loadeddata", tryPlay);
        };
        el.addEventListener("loadeddata", tryPlay);
      }
    } else {
      el.pause();
    }
  }, [isPlaying]);

  const goTo = useCallback((newIndex, autoplay) => {
    const next = (newIndex + playlist.length) % playlist.length;
    setIndex(next);
    setIsPlaying(autoplay);
  }, [playlist]);

  const handleNext = () => {
    const next = (index + 1) % playlist.length;
    goTo(next, isPlayingRef.current);
    pushSync({ songId: playlist[next]?.id, isPlaying: isPlayingRef.current, offsetSeconds: 0 });
  };
  const handlePrev = () => {
    const next = (index - 1 + playlist.length) % playlist.length;
    goTo(next, isPlayingRef.current);
    pushSync({ songId: playlist[next]?.id, isPlaying: isPlayingRef.current, offsetSeconds: 0 });
  };
  const togglePlay = () => {
    const newPlaying = !isPlayingRef.current;
    setIsPlaying(newPlaying);
    pushSync({ isPlaying: newPlaying, offsetSeconds: audioRef.current?.currentTime || 0 });
  };
  const toggleTheme = () => {
    const newTheme = theme === "vintage" ? "modern" : "vintage";
    setTheme(newTheme);
    pushSync({ theme: newTheme, songId: playlist[0]?.id, isPlaying: false, offsetSeconds: 0 });
  };

  const handleSeek = (e) => {
    const el = progressTrackRef.current;
    if (!el || !duration) return;
    const rect = el.getBoundingClientRect();
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const ratio = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
    const next = ratio * duration;
    setProgress(next);
    if (audioRef.current) audioRef.current.currentTime = next;
  };

  const pct = Math.min(100, duration > 0 ? (progress / duration) * 100 : 0);
  const px = (mouse.x - 0.5) * 10;
  const py = (mouse.y - 0.5) * 6;

  return (
    <div className="dx-page" data-theme={theme} ref={pageRef}>
      <div
        ref={bgRef}
        className="dx-bg"  
        style={{
          backgroundImage: `url(${BACKGROUND_IMAGES[theme]})`,
          transform: `scale(1.02) translate(${-px}px, ${-py}px)`,
        }}
      />
  
      <div className="dx-colorgrade" />
      <div className="dx-vignette" />
      <div className="dx-grain" />
      <div className="dx-bloom" />

      {/* Salon name overlay — mobile-only (see .dx-salon-name in CSS, which
          is display:none by default and only re-enabled under the mobile
          media query). Fixed brand text, same on both themes. */}
      <div className="dx-salon-name">
        {SALON_NAME.title}
        <span>{SALON_NAME.sub}</span>
      </div>

      {/* Crop tuner UI — only rendered with ?tune=1 in the URL. Not part of
          the normal app experience; safe to leave in the code permanently
          since real visitors never see it. Delete this block (and the
          state/effect above) once you're happy with the final crop and
          have baked the numbers into App.css directly. */}
      {tuneMode && (
        <div
          style={{
            position: "fixed",
            left: 12,
            bottom: 12,
            zIndex: 999,
            background: "rgba(0,0,0,0.82)",
            border: "1px solid rgba(255,255,255,0.2)",
            borderRadius: 12,
            padding: "12px 14px",
            color: "#fff",
            fontFamily: "monospace",
            fontSize: 12,
            width: 230,
          }}
        >
          <div style={{ marginBottom: 8, opacity: 0.8 }}>
            {theme === "modern" ? "Tuning MODERN crop" : "Switch to Modern to tune"}
          </div>
          <label style={{ display: "block", marginBottom: 6 }}>
            X: {tuneX}%
            <input type="range" min={0} max={100} value={tuneX}
              onChange={(e) => setTuneX(Number(e.target.value))}
              style={{ width: "100%" }} />
          </label>
          <label style={{ display: "block", marginBottom: 6 }}>
            Y: {tuneY}%
            <input type="range" min={0} max={100} value={tuneY}
              onChange={(e) => setTuneY(Number(e.target.value))}
              style={{ width: "100%" }} />
          </label>
          <label style={{ display: "block", marginBottom: 6 }}>
            Zoom: {(tuneZoom / 100).toFixed(2)}x
            <input type="range" min={100} max={220} value={tuneZoom}
              onChange={(e) => setTuneZoom(Number(e.target.value))}
              style={{ width: "100%" }} />
          </label>
          <div style={{ marginTop: 6, opacity: 0.65, lineHeight: 1.4 }}>
            Drag until the text is gone and barber+customer are centered,
            then send me these 3 numbers.
          </div>
        </div>
      )}

      <svg className="dx-particles" viewBox="0 0 100 100" preserveAspectRatio="none">
        {PARTICLES.map((p) => (
          <circle
            key={p.id}
            className="dx-particle"
            cx={p.x}
            cy={p.y}
            r={p.r * 0.18}
            fill="var(--dx-amber)"
            opacity={p.opacity}
            style={{ animationDuration: `${p.dur}s`, animationDelay: `${p.delay}s` }}
          />
        ))}
      </svg>

      <div className="dx-topbar">
        <div className="dx-topbar-clock">{clock}</div>
        <div className="dx-topbar-online" aria-live="polite">
          <span className="dx-online-dot" />
          {onlineCount} online
        </div>
        <div className="dx-topbar-links">
          <button
            type="button"
            className="dx-link-badge dx-theme-toggle"
            onClick={toggleTheme}
            aria-label={`Switch to ${theme === "vintage" ? "modern" : "vintage"} salon`}
          >
            <Sparkles size={13} />
            <span>{theme === "vintage" ? "Modern" : "Vintage"}</span>
          </button>
          <a href="https://open.spotify.com" target="_blank" rel="noreferrer" className="dx-link-badge">
            <Headphones size={13} /><span>Spotify</span>
          </a>
          <a href="https://music.youtube.com" target="_blank" rel="noreferrer" className="dx-link-badge">
            <Radio size={13} /><span>YT Music</span>
          </a>
        </div>
      </div>

      {/* HTML5 Audio element — src set imperatively */}
      <audio
        ref={audioRef}
        onTimeUpdate={(e) => setProgress(e.currentTarget.currentTime)}
        onDurationChange={(e) => setDuration(e.currentTarget.duration)}
        onEnded={handleNext}
        onError={(e) => console.error("Audio error:", e.currentTarget.error)}
      />

      <div className="dx-player-wrap">
        <div className={`dx-player-card${isPlaying ? " dx-player-card--playing" : ""}`}>
          <div className={`dx-album-art${isPlaying ? " dx-album-art--playing" : ""}`}>
            {track?.art ? <img src={track.art} alt="" /> : <Music2 size={18} />}
          </div>

          <div className="dx-player-body">
            <div className="dx-track-title">
              <span className="dx-track-title-text">{track?.title}</span>
              {isPlaying && (
                <span className="dx-eq" aria-hidden="true">
                  <span /><span /><span /><span />
                </span>
              )}
            </div>
            <div className="dx-track-artist">{track?.artist}</div>
            <div
              className="dx-progress-track"
              ref={progressTrackRef}
              onClick={handleSeek}
              role="slider"
              aria-label="Seek"
              aria-valuemin={0}
              aria-valuemax={duration}
              aria-valuenow={Math.round(progress)}
              tabIndex={0}
            >
              <div className="dx-progress-fill" style={{ width: `${pct}%` }}>
                <div className="dx-progress-dot" />
              </div>
            </div>
            <div className="dx-time-row">
              <span>{formatTime(progress)}</span>
              <span className="dx-time-sep">·</span>
              <span className="dx-time-total">{formatTime(duration)}</span>
            </div>
          </div>

          <div className="dx-controls">
            <button className="dx-btn" onClick={handlePrev} aria-label="Previous">
              <SkipBack size={15} fill="currentColor" />
            </button>
            <button className="dx-btn dx-btn--play" onClick={togglePlay} aria-label={isPlaying ? "Pause" : "Play"}>
              {isPlaying
                ? <Pause size={16} fill="currentColor" />
                : <Play size={16} fill="currentColor" style={{ marginLeft: 1 }} />}
            </button>
            <button className="dx-btn" onClick={handleNext} aria-label="Next">
              <SkipForward size={15} fill="currentColor" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}