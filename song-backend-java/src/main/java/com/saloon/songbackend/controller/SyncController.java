package com.saloon.songbackend.controller;

import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    // Shared playback state for all devices
    private static final Map<String, Object> state = new ConcurrentHashMap<>();

    static {
        state.put("songId", 1);
        state.put("theme", "vintage");
        state.put("isPlaying", false);
        state.put("startedAt", System.currentTimeMillis());
        state.put("offsetSeconds", 0.0);
    }

    // GET current playback state
    @GetMapping
    public Map<String, Object> getState() {
        // Calculate current position based on time elapsed
        if ((boolean) state.get("isPlaying")) {
            long startedAt = (long) state.get("startedAt");
            double offset = (double) state.get("offsetSeconds");
            double elapsed = (System.currentTimeMillis() - startedAt) / 1000.0;
            state.put("currentTime", offset + elapsed);
        } else {
            state.put("currentTime", state.get("offsetSeconds"));
        }
        return state;
    }

    // POST update playback state (when someone presses play/pause/next)
    @PostMapping
    public Map<String, Object> updateState(@RequestBody Map<String, Object> update) {
        if (update.containsKey("songId"))      state.put("songId", update.get("songId"));
        if (update.containsKey("theme"))       state.put("theme", update.get("theme"));
        if (update.containsKey("isPlaying"))   state.put("isPlaying", update.get("isPlaying"));
        if (update.containsKey("offsetSeconds")) {
            state.put("offsetSeconds", ((Number) update.get("offsetSeconds")).doubleValue());
            state.put("startedAt", System.currentTimeMillis());
        }
        if ((boolean) state.get("isPlaying")) {
            state.put("startedAt", System.currentTimeMillis());
        }
        return getState();
    }
}
