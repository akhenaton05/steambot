package ru.steam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.steam.entity.db.PnlRecord;

@Repository
public interface PnlRepository extends JpaRepository<PnlRecord, Long> {
}
