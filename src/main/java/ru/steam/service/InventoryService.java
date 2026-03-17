package ru.steam.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.steam.entity.db.ItemSnapshot;
import ru.steam.entity.dto.InventoryDto;
import ru.steam.entity.dto.ItemDto;
import ru.steam.mapper.ItemSnapshotMapper;
import ru.steam.repository.ItemsRepository;

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
    private final SteamService steamService;
    private final ItemSnapshotMapper itemSnapshotMapper;

    private static final String STEAM_ID = "76561198158734100";

    @Scheduled(cron = "0 0 10,22 * * *")
    public void takeItemsSnapshot() throws InterruptedException {
        InventoryDto inventory = steamService.getInventory(STEAM_ID);
        String owner = inventory.getSteamName();

        // Текущие имена предметов в инвентаре
        Set<String> currentItemNames = inventory.getItems().stream()
                .map(ItemDto::getDisplayName)
                .collect(Collectors.toSet());

        // Удаляем то чего больше нет в инвентаре
        List<ItemSnapshot> allExisting = itemsRepository.findAllByOwner(owner);
        List<ItemSnapshot> toDelete = allExisting.stream()
                .filter(s -> !currentItemNames.contains(s.getDisplayName()))
                .toList();
        itemsRepository.deleteAll(toDelete);

        // Upsert — обновляем существующие или создаём новые
        List<ItemSnapshot> toSave = new ArrayList<>();

        for (ItemDto item : inventory.getItems()) {
            Optional<ItemSnapshot> existing = itemsRepository
                    .findFirstByOwnerAndDisplayNameOrderByDateAsc(owner, item.getDisplayName());

            ItemSnapshot snapshot;

            if (existing.isPresent()) {
                // Обновляем существующую запись — меняем только цену, дату, разницу
                snapshot = existing.get();
                snapshot.setPriceNow(item.getPrice());
                snapshot.setDate(LocalDate.now());

                BigDecimal diff = item.getPrice()
                        .subtract(snapshot.getPriceInitial())
                        .divide(snapshot.getPriceInitial(), 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                String sign = diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                snapshot.setDifference(sign + diff + "%");

                // holdTime = сегодня - дата покупки
                long holdDays = ChronoUnit.DAYS.between(snapshot.getPurchaseDate(), LocalDate.now());
                snapshot.setHoldTime((int) holdDays);

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
            }

            toSave.add(snapshot);
        }

        itemsRepository.saveAll(toSave);
        log.info("[InventoryService] Snapshot updated: {} items added, {} items deleted", toSave.size(), toDelete.size());
    }

    public String getPortfolioReport() {
        String steamName = steamService.steamIdToName(STEAM_ID);
        List<ItemSnapshot> items = itemsRepository.findAllByOwner(steamName);
        if (items.isEmpty()) return "📭 No data";

        BigDecimal totalNow = items.stream()
                .map(i -> i.getPriceNow().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInitial = items.stream()
                .map(i -> i.getPriceInitial().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiff = totalNow.subtract(totalInitial);
        String totalSign = totalDiff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";

        StringBuilder sb = new StringBuilder();
        sb.append("*🤖 [SteamBot]*\n");
        sb.append("*📊 Portfolio Report*\n");
        sb.append("*\uD83D\uDDFF Profile: *").append(steamName).append("\n\n");
        sb.append(String.format("💰 Now:     *%,.2f ₽*\n", totalNow));
        sb.append(String.format("📌 Initial: *%,.2f ₽*\n", totalInitial));
        sb.append(String.format("\uD83D\uDC51 PnL:     *%s%,.2f ₽*\n\n", totalSign, totalDiff));

        // Топ 3 gainers
        sb.append("📈 *Top Gainers:*\n");
        items.stream()
                .sorted(Comparator.<ItemSnapshot, BigDecimal>comparing(i ->
                        i.getPriceNow().subtract(i.getPriceInitial())
                                .multiply(BigDecimal.valueOf(i.getQuantity()))).reversed())
                .limit(3)
                .forEach(i -> {
                    BigDecimal pnl = i.getPriceNow()
                            .subtract(i.getPriceInitial())
                            .multiply(BigDecimal.valueOf(i.getQuantity()));
                    String sign = pnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                    sb.append(String.format("*%s*  %s  (%s%,.2f ₽)\n",
                            i.getDisplayName(), i.getDifference(), sign, pnl));
                });

        // Топ 3 losers
        sb.append("\n📉 *Top Losers:*\n");
        items.stream()
                .sorted(Comparator.comparing(i ->
                        i.getPriceNow().subtract(i.getPriceInitial())
                                .multiply(BigDecimal.valueOf(i.getQuantity()))))
                .limit(3)
                .forEach(i -> {
                    BigDecimal pnl = i.getPriceNow()
                            .subtract(i.getPriceInitial())
                            .multiply(BigDecimal.valueOf(i.getQuantity()));
                    String sign = pnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                    sb.append(String.format(": %s  %s  (%s%,.2f ₽)\n",
                            i.getDisplayName(), i.getDifference(), sign, pnl));
                });

        // По типам
        sb.append("\n📦 *By Type:*\n");
        items.stream()
                .collect(Collectors.groupingBy(ItemSnapshot::getType))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(
                        Comparator.comparingDouble(list ->
                                -list.stream()
                                        .mapToDouble(i -> i.getPriceNow().doubleValue() * i.getQuantity())
                                        .sum())
                ))
                .forEach(e -> {
                    double sum = e.getValue().stream()
                            .mapToDouble(i -> i.getPriceNow().doubleValue() * i.getQuantity())
                            .sum();
                    int totalPcs = e.getValue().stream()
                            .mapToInt(ItemSnapshot::getQuantity)
                            .sum();
                    sb.append(String.format("*%-10s* %,.2f ₽  (%d pcs)\n",
                            e.getKey(), sum, totalPcs));
                });

        return sb.toString();
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
