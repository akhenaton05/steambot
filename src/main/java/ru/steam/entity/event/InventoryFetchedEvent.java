package ru.steam.entity.event;

import ru.steam.entity.dto.InventoryDto;

public record InventoryFetchedEvent(InventoryDto inventory) {
}