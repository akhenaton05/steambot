package ru.steam.entity.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import ru.steam.entity.dto.InventoryDto;

@Getter
public class InventoryFetchedEvent extends ApplicationEvent {
    private final InventoryDto inventory;

    public InventoryFetchedEvent(Object source, InventoryDto inventory) {
        super(source);
        this.inventory = inventory;
    }
}