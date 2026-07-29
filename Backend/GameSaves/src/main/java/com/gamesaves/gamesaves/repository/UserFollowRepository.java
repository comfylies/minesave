package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.entity.UserFollow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    Optional<UserFollow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    @Query("SELECT u FROM UserFollow f JOIN User u ON u.id = f.followingId " +
            "WHERE f.followerId = :followerId AND u.isActive = true ORDER BY f.createdAt DESC")
    List<User> findFollowedUsersByFollowerId(@Param("followerId") Long followerId, Pageable pageable);

    @Query("SELECT COUNT(f) FROM UserFollow f JOIN User u ON u.id = f.followingId " +
            "WHERE f.followerId = :followerId AND u.isActive = true")
    long countActiveFollowingByFollowerId(@Param("followerId") Long followerId);
}
