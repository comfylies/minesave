package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    boolean existsByName(String name);

    List<Tag> findBySourceOrderByCreatedAtDesc(Tag.Source source);

    List<Tag> findAllByOrderByCreatedAtDesc();

    List<Tag> findByNameContaining(String keyword);
}
