package ru.steam.utils;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.steam.entity.*;
import ru.steam.entity.dto.InventoryDto;
import ru.steam.mapper.InventoryMapper;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InventoryParser {

    public Inventory parse(InventoryResponse response, String steamId) {

        // ШАГ 1: словарь описаний по ключу "classId_instanceId"
        // зачем: чтобы за O(1) находить описание по данным из asset
        Map<String, Description> descMap = new HashMap<>();
        for (Description desc : response.getDescriptions()) {
            String key = desc.getClassId() + "_" + desc.getInstanceId();
            descMap.put(key, desc);
        }
        // пример: "7993037205_1363818008" → Description(marketHashName="Tec-9 | Tiger...")

        // ШАГ 2: проходим по assets и считаем количество каждого предмета
        // один asset = одна физическая копия предмета в инвентаре
        Map<String, Integer> countMap = new LinkedHashMap<>(); // LinkedHashMap сохраняет порядок
        Map<String, Description> hashToDesc = new HashMap<>();

        for (Asset asset : response.getAssets()) {
            String key = asset.getClassId() + "_" + asset.getInstanceId();
            Description desc = descMap.get(key);

            if (Objects.isNull(desc)) {
                log.warn("No description for asset {}", asset.getAssetId());
                continue;
            }

//            // Skipping non tradable\marketable items
//            if (desc.getTradable() != 1 /*|| desc.getMarketable() != 1*/) {
//                continue;
//            }

            if (desc.getMarketable() != 1) {
                continue;
            }

            String hashName = desc.getMarketHashName();

            // merge: если ключ уже есть — применяет функцию (old, new) -> old + new
            //        если ключа нет — кладёт значение 1
            // эквивалентно: countMap.put(key, countMap.getOrDefault(key, 0) + 1)
            countMap.merge(hashName, 1, Integer::sum);

            // putIfAbsent: кладёт только если ключа ещё нет
            // нам нужно сохранить Description для доступа к name/type
            // но достаточно одной копии — все одинаковые предметы имеют одно описание
            hashToDesc.putIfAbsent(hashName, desc);
        }

//        log.info("[InventoryParser] steamId={}: {} unique tradable items", steamId, countMap.size());

        // ШАГ 3: собираем List<CsItem> из накопленных данных
        List<Item> items = countMap.entrySet().stream()
                .map(entry -> {
                    String hashName = entry.getKey();
                    Description desc = hashToDesc.get(hashName);
                    return Item.builder()
                            .marketHashName(hashName)
                            .displayName(desc.getName())
                            .type(desc.getType())
                            .quantity(entry.getValue())
                            .build();
                })
                .collect(Collectors.toList());

        return Inventory.builder()
                .steamId(steamId)
                .items(items)
                .totalItemCount(response.getTotalInventoryCount())
                .hasMorePages(response.getMoreItems() == 1)
                .lastAssetId(response.getLastAssetid())
                .build();
    }
}