package ru.steam.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.steam.entity.Inventory;
import ru.steam.entity.dto.InventoryDto;

@Mapper(componentModel = "spring", uses = {ItemMapper.class})
public interface InventoryMapper {

    @Mapping(target = "steamName", ignore = true)
    @Mapping(source = "totalItemCount", target = "totalItems")
    InventoryDto toDto(Inventory inventory);
}

