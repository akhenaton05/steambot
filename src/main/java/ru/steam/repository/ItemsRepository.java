package ru.steam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.steam.entity.db.ItemSnapshot;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemsRepository extends JpaRepository<ItemSnapshot, Long> {
    Optional<ItemSnapshot> findFirstByOwnerAndDisplayNameOrderByDateAsc(String owner, String displayName);
    List<ItemSnapshot> findAllByOwner(String owner);
    boolean existsByOwnerAndDisplayName(String owner, String displayName);
}
