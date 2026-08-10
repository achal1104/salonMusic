package com.saloon.songbackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "playlist_songs")
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String title;

    @NotBlank
    @Column(nullable = false)
    private String artist;

    @Column(name = "spotify_url")
    private String spotifyUrl;

    @Column(name = "audio_url")
    private String audioUrl;

    private Integer position = 0;

    public Song() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getSpotifyUrl() { return spotifyUrl; }
    public void setSpotifyUrl(String spotifyUrl) { this.spotifyUrl = spotifyUrl; }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}
