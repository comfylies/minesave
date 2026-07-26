package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    boolean existsByName(String name);

    List<Tag> findBySourceOrderByCreatedAtDesc(Tag.Source source);

    List<Tag> findAllByOrderByCreatedAtDesc();

    List<Tag> findByNameContaining(String keyword);

    List<Tag> findTop8BySourceOrderByCreatedAtDesc(Tag.Source source);

    List<Tag> findByIdIn(Collection<Long> ids);

    Optional<Tag> findByName(String name);

    @Query("""
            SELECT t FROM Tag t
            WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY CASE WHEN LOWER(t.name) = LOWER(:keyword) THEN 0 ELSE 1 END,
                     CASE WHEN t.source = com.gamesaves.gamesaves.entity.Tag.Source.admin THEN 0 ELSE 1 END,
                     t.createdAt DESC
            """)
    List<Tag> searchFallback(@Param("keyword") String keyword, Pageable pageable);
}
