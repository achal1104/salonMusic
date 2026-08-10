package com.saloon.songbackend.repository;

import com.saloon.songbackend.model.Song;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {
    List<Song> findAllByOrderByIdAsc();
}
