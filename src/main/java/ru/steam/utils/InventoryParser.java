package ru.steam.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.steam.entity.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InventoryParser {

    /* classId - item description
       assetId - unique item in inventory(for counting)
       instanceId - 0 - default, 1 - StatTrack, etc.
     */
    public Inventory parse(InventoryResponse response, String steamId) {
        Map<String, Description> descriptions = new HashMap<>();
        for (Description desc : response.getDescriptions()) {
            if (desc.getMarketable() == 1) {  // Filtering marketable items only
                //String key = desc.getClassId() + "_" + desc.getInstanceId();
                descriptions.putIfAbsent(desc.getClassId(), desc);
            }
        }

        //Filtering + counting quantity - by Assets
        Map<String, Integer> itemsCount = new HashMap<>();
        for (Asset asset : response.getAssets()) {
            itemsCount.merge(asset.getClassId(), 1, Integer::sum);
        }

        List<Item> items = descriptions.values().stream()
                .map(description -> {
                    return Item.builder()
                            .marketHashName(description.getMarketHashName())
                            .displayName(description.getName())
                            .type(description.getType())
                            .quantity(itemsCount.getOrDefault(description.getClassId(), 0))
                            .build();
                })
                .toList();

        return Inventory.builder()
                .steamId(steamId)
                .items(items)
                .totalItemCount(response.getTotalInventoryCount())
                .hasMorePages(response.getMoreItems() == 1)
                .lastAssetId(response.getLastAssetid())
                .build();
    }
}