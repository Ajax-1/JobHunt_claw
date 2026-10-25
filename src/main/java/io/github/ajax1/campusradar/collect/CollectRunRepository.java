package io.github.ajax1.campusradar.collect;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollectRunRepository extends JpaRepository<CollectRun, Long> {

    Optional<CollectRun> findFirstBySourceAndStatusInOrderByStartedAtDesc(String source, List<CollectRun.Status> statuses);

    Optional<CollectRun> findFirstBySourceOrderByStartedAtDesc(String source);
}
