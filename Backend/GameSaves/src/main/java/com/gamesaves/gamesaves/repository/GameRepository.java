package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    Optional<Game> findByName(String name);

    boolean existsByName(String name);

    List<Game> findAllByOrderByCreatedAtDesc();
}
