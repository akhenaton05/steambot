package ru.steam.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.steam.entity.Item;
import ru.steam.entity.ItemType;
import ru.steam.entity.dto.ItemDto;

@Mapper(componentModel = "spring")
public interface ItemMapper {

    @Mapping(source = "type", target = "type", qualifiedByName = "stringToItemType")
    ItemDto toDto(Item item);

    @Named("stringToItemType")
    default String stringToItemType(String type) {
        return ItemType.fromType(type).getDisplayName();
    }
}
