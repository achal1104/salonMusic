// Drop-in replacement snippets for App.jsx to load the playlist from the
// Spring Boot backend (http://localhost:4000/api/songs) instead of the
// static playlist.js file.

// 1. Remove this line:
//    import PLAYLIST from "./playlist.js";

// 2. Add near the top of App.jsx:
const API_BASE = "http://localhost:4000";

// 3. Inside the App component, replace the fixed PLAYLIST usage with state:
//
// const [playlist, setPlaylist] = useState([]);
// const [loading, setLoading] = useState(true);
// const [loadError, setLoadError] = useState(null);
//
// useEffect(() => {
//   fetch(`${API_BASE}/api/songs`)
//     .then((res) => {
//       if (!res.ok) throw new Error(`Request failed: ${res.status}`);
//       return res.json();
//     })
//     .then((songs) => {
//       // prefix relative src paths with the backend origin
//       const withFullSrc = songs.map((s) => ({
//         ...s,
//         src: s.src ? `${API_BASE}${s.src}` : "",
//       }));
//       setPlaylist(withFullSrc);
//     })
//     .catch((err) => setLoadError(err.message))
//     .finally(() => setLoading(false));
// }, []);
//
// const track = playlist[index]; // instead of PLAYLIST[index]
//
// Guard the render until data arrives, e.g.:
// if (loading) return <div className="dx-page">Loading playlist…</div>;
// if (loadError || playlist.length === 0) {
//   return <div className="dx-page">Couldn't load playlist. Is the backend running on :4000?</div>;
// }

// 4. Everywhere else in the file that reads `PLAYLIST.length`,
//    change it to `playlist.length` (e.g. inside goTo()).
