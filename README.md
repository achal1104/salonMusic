# Saloon Music Player — Setup Guide

## 1. Install Node.js
Download and install Node.js (LTS) from https://nodejs.org if you don't have it.
Check it worked:
```
node -v
npm -v
```

## 2. Unzip the project
Unzip `deluxe-saloon.zip` anywhere, then open a terminal in that folder:
```
cd deluxe-saloon
```

## 3. Install dependencies
```

npm install
```

## 4. Run it locally
```
npm run dev
```
Open the URL it prints (usually http://localhost:5173).

## 5. Add your real songs
- Drop your mp3 files into `public/songs/` (e.g. `01.mp3`, `02.mp3`, ... `25.mp3`)
- Open `src/playlist.js` and make sure each `src` path matches your filenames,
  and update `title` / `artist` / `duration` (in seconds) for each song.

## 6. Set your salon name
Open `src/App.jsx`, find this line near the top, and change it:
```js
const SALOON_NAME = "तुमचे सैलून";
```

## 7. Build for production (when ready to deploy)
```
npm run build
```
This creates a `dist/` folder — upload that to any static host
(Vercel, Netlify, GitHub Pages, etc). No backend needed.

## Project structure
```
deluxe-saloon/
├── index.html          ← page shell, loads Google Fonts
├── package.json
├── vite.config.js
├── public/
│   └── songs/           ← put your mp3 files here
└── src/
    ├── main.jsx          ← mounts React app
    ├── App.jsx            ← the player UI (JSX)
    ├── App.css            ← all styling (CSS)
    └── playlist.js         ← the 25-song list, edit titles/artists/audio paths here
```
