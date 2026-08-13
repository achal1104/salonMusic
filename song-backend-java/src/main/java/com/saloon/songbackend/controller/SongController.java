package com.saloon.songbackend.controller;

import com.saloon.songbackend.model.Song;
import com.saloon.songbackend.repository.SongRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.io.IOException;

@RestController
@RequestMapping("/api/songs")
public class SongController {

    private final SongRepository repository;

    public SongController(SongRepository repository) {
        this.repository = repository;
    }

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    @org.springframework.beans.factory.annotation.Value("${songs.download.dir:}")
    private String downloadsDir;

    // Fetch JioSaavn stream URL for a song, cache in DB
    private void resolveAudioUrl(Song song) {
        try {
            String query = java.net.URLEncoder.encode(song.getTitle(), "UTF-8");
            String apiUrl = "https://jiosaavn-api-2.vercel.app/search/songs?query=" + query + "&limit=1";
            String response = restTemplate.getForObject(apiUrl, String.class);
            JsonNode results = objectMapper.readTree(response).path("results");
            if (results.isArray() && results.size() > 0) {
                JsonNode downloadUrls = results.get(0).path("downloadUrl");
                String url = null;
                // prefer 96kbps for broad device support
                for (JsonNode u : downloadUrls) {
                    String q = u.path("quality").asText();
                    if ("96kbps".equals(q)) { url = u.path("link").asText(); break; }
                }
                if (url == null && downloadUrls.size() > 0) url = downloadUrls.get(0).path("link").asText();
                if (url != null && !url.isBlank() && url.startsWith("http")) {
                    song.setAudioUrl(url);
                    repository.save(song);
                }
            }
        } catch (Exception ignored) {}
    }

    // GET fresh stream URL from JioSaavn for a song by title
    private String fetchFreshUrl(String title) {
        try {
            String query = java.net.URLEncoder.encode(title, "UTF-8");
            String response = restTemplate.getForObject(
                "https://jiosaavn-api-2.vercel.app/search/songs?query=" + query + "&limit=1", String.class);
            JsonNode results = objectMapper.readTree(response).path("results");
            if (results.isArray() && results.size() > 0) {
                JsonNode urls = results.get(0).path("downloadUrl");
                for (JsonNode u : urls) {
                    if ("96kbps".equals(u.path("quality").asText())) return u.path("link").asText();
                }
                if (urls.size() > 0) return urls.get(urls.size() - 1).path("link").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    // GET /api/songs/{id}/stream-url — returns fresh JioSaavn URL as JSON
    @GetMapping("/{id}/stream-url")
    public ResponseEntity<String> getStreamUrl(@PathVariable Long id) {
        return repository.findById(id).map(song -> {
            String freshUrl = fetchFreshUrl(song.getTitle());
            if (freshUrl == null) return ResponseEntity.notFound().<String>build();
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("\"" + freshUrl + "\"");
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // GET all songs filtered by theme
    @GetMapping
    public List<Song> getAllSongs(@RequestParam(required = false, defaultValue = "vintage") String theme) {
        return repository.findByThemeOrderByPositionAsc(theme);
    }

    // POST create a new song (adds to DB list only)
    @PostMapping
    public ResponseEntity<Song> createSong(@RequestBody Song input) {
        try {
            Song s = new Song();
            s.setTitle(input.getTitle());
            s.setArtist(input.getArtist() == null || input.getArtist().isBlank() ? "Unknown" : input.getArtist());
            s.setAudioUrl(input.getAudioUrl());
            s.setTheme(input.getTheme() == null || input.getTheme().isBlank() ? "vintage" : input.getTheme());
            // determine next position
            int maxPos = repository.findAll().stream().map(Song::getPosition).max(java.util.Comparator.naturalOrder()).orElse(0);
            s.setPosition(maxPos + 1);
            Song saved = repository.save(s);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // POST resolve all missing audio URLs — call once to seed everything
    @PostMapping("/resolve-all")
    public ResponseEntity<String> resolveAll() {
        List<Song> unresolved = repository.findAll().stream()
            .filter(s -> s.getAudioUrl() == null || s.getAudioUrl().isBlank() || !s.getAudioUrl().startsWith("http"))
            .toList();
        // submit each song as its own task — 5 threads run in parallel
        unresolved.forEach(song -> executor.submit(() -> resolveAudioUrl(song)));
        return ResponseEntity.ok("Resolving " + unresolved.size() + " songs in background");
    }

    // POST scan a local downloads folder for new .mp3 files and add to DB
    @PostMapping("/scan-downloads")
    public ResponseEntity<List<Song>> scanDownloads() {
        try {
            String dir = downloadsDir;
            if (dir == null || dir.isBlank()) {
                dir = java.nio.file.Paths.get(System.getProperty("user.home"), "Downloads").toString();
            }
            java.io.File folder = new java.io.File(dir);
            if (!folder.exists() || !folder.isDirectory()) return ResponseEntity.badRequest().body(java.util.Collections.emptyList());

            java.util.List<Song> added = new java.util.ArrayList<>();
            java.util.List<Song> all = repository.findAll();
            int maxPos = all.stream().map(Song::getPosition).max(java.util.Comparator.naturalOrder()).orElse(0);

            java.io.FilenameFilter filter = (d, name) -> name.toLowerCase().endsWith(".mp3");
            java.io.File[] files = folder.listFiles(filter);
            if (files == null) return ResponseEntity.ok(added);
            for (java.io.File f : files) {
                String abs = f.getAbsolutePath();
                String base = f.getName();
                String title = base.replaceFirst("\\\\.[^.]+$", "");
                boolean exists = all.stream().anyMatch(s -> abs.equals(s.getAudioUrl()) || title.equalsIgnoreCase(s.getTitle()));
                if (!exists) {
                    Song s = new Song();
                    s.setTitle(title);
                    s.setArtist("Unknown");
                    s.setAudioUrl(abs);
                    s.setTheme("vintage");
                    s.setPosition(++maxPos);
                    repository.save(s);
                    added.add(s);
                }
            }
            return ResponseEntity.ok(added);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Collections.emptyList());
        }
    }

    // Upload local mp3 files from client and add DB rows (stores files in server temp dir)
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<Song>> uploadSongs(@RequestParam("files") MultipartFile[] files) {
        try {
            Path outDir = Paths.get(System.getProperty("java.io.tmpdir"), "uploads");
            Files.createDirectories(outDir);
            java.util.List<Song> all = repository.findAll();
            int maxPos = all.stream().map(Song::getPosition).max(Comparator.naturalOrder()).orElse(0);
            java.util.List<Song> added = new ArrayList<>();
            for (MultipartFile f : files) {
                if (f == null || f.isEmpty()) continue;
                String filename = StringUtils.cleanPath(f.getOriginalFilename());
                Path out = outDir.resolve(filename);
                try (InputStream in = f.getInputStream()) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
                String title = filename.replaceFirst("\\.[^.]+$", "");
                boolean exists = all.stream().anyMatch(s -> out.toString().equals(s.getAudioUrl()) || title.equalsIgnoreCase(s.getTitle()));
                if (!exists) {
                    Song s = new Song();
                    s.setTitle(title);
                    s.setArtist("Unknown");
                    s.setAudioUrl(out.toString());
                    s.setTheme("vintage");
                    s.setPosition(++maxPos);
                    repository.save(s);
                    added.add(s);
                    all.add(s);
                }
            }
            return ResponseEntity.ok(added);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    // GET single song
    @GetMapping("/{id}")
    public ResponseEntity<Song> getSong(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // GET audio — streams mp3 with range support for fast start
    @GetMapping("/{id}/audio")
    public ResponseEntity<?> getAudio(@PathVariable Long id,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        return repository.findById(id)
                .filter(s -> s.getAudioUrl() != null && !s.getAudioUrl().isBlank())
                .map(s -> {
                    try {
                        String audio = s.getAudioUrl();
                        Resource resource = null;
                        if (!audio.startsWith("http") && !audio.contains(":") && !audio.startsWith("/")) {
                            resource = new ClassPathResource("static/audio/" + audio);
                            if (!resource.exists()) return ResponseEntity.notFound().build();
                        } else if (audio.startsWith("http")) {
                            return ResponseEntity.status(HttpStatus.FOUND)
                                    .header(HttpHeaders.LOCATION, audio).build();
                        } else {
                            java.nio.file.Path p = java.nio.file.Paths.get(audio);
                            if (!java.nio.file.Files.exists(p)) return ResponseEntity.notFound().build();
                            resource = new org.springframework.core.io.PathResource(p);
                        }
                        long contentLength = resource.contentLength();
                        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                            String[] parts = rangeHeader.substring(6).split("-");
                            long start = Long.parseLong(parts[0]);
                            long end = parts.length > 1 && !parts[1].isEmpty()
                                    ? Long.parseLong(parts[1]) : contentLength - 1;
                            end = Math.min(end, contentLength - 1);
                            long rangeLength = end - start + 1;
                            try (InputStream in = resource.getInputStream()) {
                                in.skip(start);
                                byte[] data = in.readNBytes((int) rangeLength);
                                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                                        .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                        .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + contentLength)
                                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(rangeLength))
                                        .body(data);
                            }
                        }
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                                .body(resource);
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // PUT update audio_url for a song
    @PutMapping("/{id}/audio-url")
    public ResponseEntity<Song> updateAudioUrl(@PathVariable Long id, @RequestParam String audioUrl) {
        return repository.findById(id)
                .map(song -> {
                    song.setAudioUrl(audioUrl);
                    return ResponseEntity.ok(repository.save(song));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // DELETE a song
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSong(@PathVariable Long id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
