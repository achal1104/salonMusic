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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.InputStream;
import java.net.URL;

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

    // GET single song
    @GetMapping("/{id}")
    public ResponseEntity<Song> getSong(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // GET audio — serves mp3 from static/audio/
    @GetMapping("/{id}/audio")
    public ResponseEntity<Resource> getAudio(@PathVariable Long id) {
        return repository.findById(id)
                .filter(s -> s.getAudioUrl() != null && !s.getAudioUrl().isBlank())
                .map(s -> {
                    try {
                        String filename = s.getAudioUrl();
                        Resource resource = new ClassPathResource("static/audio/" + filename);
                        if (!resource.exists()) return ResponseEntity.notFound().<Resource>build();
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                .contentType(MediaType.parseMediaType("audio/mpeg"))
                                .body(resource);
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Resource>build();
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
