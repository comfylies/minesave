package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.SavingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingItemRepository extends JpaRepository<SavingItem, Long> {

    // GitHub-style: list files in a directory
    List<SavingItem> findBySnapshotIdAndParentPathAndIsDirectory(
            Long snapshotId, String parentPath, Boolean isDirectory);

    // Breadcrumb: list subdirectories
    @Query("SELECT s.virtualPath FROM SavingItem s WHERE s.snapshotId = :snapshotId AND s.parentPath = :parentPath AND s.isDirectory = true")
    List<String> findSubdirectoryPaths(@Param("snapshotId") Long snapshotId,
                                       @Param("parentPath") String parentPath);

    // File preview lookup
    Optional<SavingItem> findBySnapshotIdAndVirtualPath(Long snapshotId, String virtualPath);

    // All items for a snapshot
    List<SavingItem> findBySnapshotId(Long snapshotId);

    // Rollback
    void deleteBySnapshotId(Long snapshotId);

    // Count
    long countBySnapshotIdAndIsDirectory(Long snapshotId, Boolean isDirectory);
}
