package ru.steam.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.steam.entity.ItemStatus;
import ru.steam.entity.db.ItemSnapshot;
import ru.steam.entity.db.PnlRecord;
import ru.steam.entity.dto.InventoryDto;
import ru.steam.entity.dto.ItemDto;
import ru.steam.entity.event.InventoryFetchedEvent;
import ru.steam.mapper.ItemSnapshotMapper;
import ru.steam.mapper.SnapshotToRecordMapper;
import ru.steam.repository.ItemsRepository;
import ru.steam.repository.PnlRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class InventoryService {

    private final ItemsRepository itemsRepository;
    private final PnlRepository pnlRepository;
    private final ItemSnapshotMapper itemSnapshotMapper;

    @Async
    @EventListener
    public void saveInventoryEvent(InventoryFetchedEvent event) {
        saveInventory(event.getInventory());
    }

    public void saveInventory(InventoryDto inventory) {
        String owner = inventory.getSteamName();

        Map<String, Integer> currentSnapshot = inventory.getItems().stream()
                .collect(Collectors.toMap(
                        ItemDto::getDisplayName,
                        ItemDto::getQuantity
                ));

        List<ItemSnapshot> allExisting = itemsRepository.findAllByOwner(owner);

        //Deleting SOLD items and moving them to pnl_record
        List<ItemSnapshot> soldItems = allExisting.stream()
                .filter(s -> ItemStatus.SOLD.name().equalsIgnoreCase(s.getStatus()))
                .toList();

        if (!soldItems.isEmpty()) {
            validateSoldItems(soldItems);
            itemsRepository.deleteAll(soldItems);
        }

        //Excluding SOLD items
        List<ItemSnapshot> prevSnapshot = allExisting.stream()
                .filter(s -> !ItemStatus.SOLD.name().equalsIgnoreCase(s.getStatus()))
                .toList();

        //Item disappeared → ON_SALE status
        List<ItemSnapshot> onSale = prevSnapshot.stream()
                .filter(s -> !currentSnapshot.containsKey(s.getDisplayName()))
                .filter(s -> ItemStatus.HOLD.name().equalsIgnoreCase(s.getStatus()))
                .toList();

        onSale.forEach(s -> s.setStatus(ItemStatus.ON_SALE.name()));
        itemsRepository.saveAll(onSale);

        //Quantity checking → dividing string
        List<ItemSnapshot> partiallyChanged = prevSnapshot.stream()
                .filter(s -> currentSnapshot.containsKey(s.getDisplayName()))
                .filter(s -> ItemStatus.HOLD.name().equalsIgnoreCase(s.getStatus()))
                .filter(s -> s.getQuantity() > currentSnapshot.get(s.getDisplayName()))
                .toList();

        List<ItemSnapshot> onSaleSnapshots = new ArrayList<>();

        partiallyChanged.forEach(s -> {
            int oldQty = s.getQuantity();
            int newQty = currentSnapshot.get(s.getDisplayName());
            int soldQty = oldQty - newQty;

            s.setQuantity(newQty);

            onSaleSnapshots.add(ItemSnapshot.builder()
                    .displayName(s.getDisplayName())
                    .owner(s.getOwner())
                    .quantity(soldQty)
                    .status(ItemStatus.ON_SALE.name())
                    .priceInitial(s.getPriceInitial())
                    .priceNow(s.getPriceNow())
                    .purchaseDate(s.getPurchaseDate())
                    .holdTime(s.getHoldTime())
                    .type(s.getType())
                    .difference(s.getDifference())
                    .date(LocalDate.now())
                    .build());
        });

        itemsRepository.saveAll(partiallyChanged);
        itemsRepository.saveAll(onSaleSnapshots);

        //Upsert new items
        Map<String, ItemSnapshot> prevSnapshotMap = prevSnapshot.stream()
                .filter(s -> ItemStatus.HOLD.name().equalsIgnoreCase(s.getStatus()))
                .collect(Collectors.toMap(ItemSnapshot::getDisplayName, s -> s));

        List<ItemSnapshot> toSave = new ArrayList<>();

        for (ItemDto item : inventory.getItems()) {
            ItemSnapshot existing = prevSnapshotMap.get(item.getDisplayName());
            ItemSnapshot snapshot;

            if (Objects.nonNull(existing)) {
                snapshot = existing;
                snapshot.setPriceNow(item.getPrice());
                snapshot.setDate(LocalDate.now());

                BigDecimal diff = item.getPrice()
                        .subtract(snapshot.getPriceInitial())
                        .divide(snapshot.getPriceInitial(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP);

                String sign = diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                snapshot.setDifference(sign + diff + "%");
                snapshot.setQuantity(item.getQuantity());

                long holdDays = ChronoUnit.DAYS.between(snapshot.getPurchaseDate(), LocalDate.now());
                snapshot.setHoldTime((int) holdDays);

                if (ItemStatus.ON_SALE.name().equalsIgnoreCase(snapshot.getStatus())) {
                    snapshot.setStatus(ItemStatus.HOLD.name());
                }

            } else {
                snapshot = itemSnapshotMapper.toItemSnapshot(item);
                snapshot.setOwner(owner);
                snapshot.setType(resolveType(item.getType(), item.getDisplayName()));
                snapshot.setPriceInitial(item.getPrice());
                snapshot.setPriceNow(item.getPrice());
                snapshot.setPurchaseDate(LocalDate.now());
                snapshot.setHoldTime(0);
                snapshot.setDifference("0%");
                snapshot.setDate(LocalDate.now());
                snapshot.setStatus(ItemStatus.HOLD.name());
            }

            toSave.add(snapshot);
        }

        itemsRepository.saveAll(toSave);
        log.info("[InventoryService] Snapshot updated: {} upserted, {} on sale, {} partial, {} sold",
                toSave.size(), onSale.size(), onSaleSnapshots.size(), soldItems.size());
    }

    private void validateSoldItems(List<ItemSnapshot> soldItems) {
        List<PnlRecord> sold = soldItems.stream()
                .map(SnapshotToRecordMapper::toRecord)
                .toList();

        pnlRepository.saveAll(sold);
    }

    public List<ItemSnapshot> getPortfolioReport(String steamId) {
        return itemsRepository.findAllByOwner(steamId);
    }

    private String resolveType(String type, String displayName) {
        if (type == null) return "Unknown";
        if ("Case".equalsIgnoreCase(type)) {
            String nameLower = displayName.toLowerCase();
            if (nameLower.contains("capsule") || nameLower.contains("package"))
                return "Capsule";
        }
        return type;
    }
}
