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

import java.util.List;

@RestController
@RequestMapping("/api/songs")
public class SongController {

    private final SongRepository repository;

    public SongController(SongRepository repository) {
        this.repository = repository;
    }

    // GET all songs filtered by theme
    @GetMapping
    public List<Song> getAllSongs(@RequestParam(required = false, defaultValue = "vintage") String theme) {
        return repository.findByThemeOrderByPositionAsc(theme);
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
