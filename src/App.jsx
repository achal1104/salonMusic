import React, { useState, useEffect, useRef, useCallback } from "react";
import { Play, Pause, SkipBack, SkipForward, Music2, Headphones, Radio } from "lucide-react";
import STATIC_PLAYLIST from "./playlist.js";

const API_BASE = "http://localhost:4000";
const BACKGROUND_IMAGE = "/images/rajkumarsalon.jpg";

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
  const [index, setIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [mouse, setMouse] = useState({ x: 0.5, y: 0.5 });
  const [clock, setClock] = useState(() => formatClock(new Date()));
  const [onlineCount, setOnlineCount] = useState(41);
  const [playlist, setPlaylist] = useState(STATIC_PLAYLIST);

  const pageRef = useRef(null);
  const bgRef = useRef(null);
  const audioRef = useRef(null);
  const progressTrackRef = useRef(null);
  const rafRef = useRef(null);
  const targetMouse = useRef({ x: 0.5, y: 0.5 });
  const currentMouse = useRef({ x: 0.5, y: 0.5 });
  const isPlayingRef = useRef(false);

  // keep ref in sync so callbacks always see latest value
  useEffect(() => { isPlayingRef.current = isPlaying; }, [isPlaying]);

  // fetch playlist from backend — only songs with audio_url
  useEffect(() => {
    fetch(`${API_BASE}/api/songs`)
      .then((res) => { if (!res.ok) throw new Error(); return res.json(); })
      .then((songs) => {
        const withAudio = songs.filter(s => s.audioUrl);
        if (withAudio.length) setPlaylist(withAudio);
      })
      .catch(() => {});
  }, []);

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

  // load track when index changes
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
    if (isPlayingRef.current) {
      el.play().catch((err) => console.error("Play error:", err));
    }
  }, [index, playlist]);

  // play/pause toggle effect
  useEffect(() => {
    const el = audioRef.current;
    if (!el) return;
    if (isPlaying) {
      el.play().catch((err) => console.error("Play error:", err));
    } else {
      el.pause();
    }
  }, [isPlaying]);

  const goTo = useCallback((newIndex, autoplay) => {
    const next = (newIndex + playlist.length) % playlist.length;
    setIndex(next);
    setIsPlaying(autoplay);
  }, [playlist]);

  const handleNext = () => goTo(index + 1, isPlayingRef.current);
  const handlePrev = () => goTo(index - 1, isPlayingRef.current);
  const togglePlay = () => setIsPlaying((p) => !p);

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
  const px = (mouse.x - 0.5) * 18;
  const py = (mouse.y - 0.5) * 10;

  return (
    <div className="dx-page" ref={pageRef}>
      <div
        ref={bgRef}
        className="dx-bg"
        style={{
          backgroundImage: `url(${BACKGROUND_IMAGE})`,
          transform: `scale(1.08) translate(${-px}px, ${-py}px)`,
        }}
      />
      <div className="dx-colorgrade" />
      <div className="dx-vignette" />
      <div className="dx-grain" />
      <div className="dx-bloom" />

      <svg className="dx-particles" viewBox="0 0 100 100" preserveAspectRatio="none">
        {PARTICLES.map((p) => (
          <circle
            key={p.id}
            className="dx-particle"
            cx={p.x}
            cy={p.y}
            r={p.r * 0.18}
            fill="#f7c65a"
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