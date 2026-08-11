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

@RestController
@RequestMapping("/api/songs")
public class SongController {

    private final SongRepository repository;

    public SongController(SongRepository repository) {
        this.repository = repository;
    }

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

    // GET all songs filtered by theme — resolves missing URLs in background
    @GetMapping
    public List<Song> getAllSongs(@RequestParam(required = false, defaultValue = "vintage") String theme) {
        List<Song> songs = repository.findByThemeOrderByPositionAsc(theme);
        List<Song> unresolved = songs.stream()
            .filter(s -> s.getAudioUrl() == null || s.getAudioUrl().isBlank() || !s.getAudioUrl().startsWith("http"))
            .limit(5)
            .toList();
        if (!unresolved.isEmpty()) {
            executor.submit(() -> unresolved.forEach(this::resolveAudioUrl));
        }
        return songs;
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
