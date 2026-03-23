package ru.steam.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.steam.entity.db.ItemSnapshot;
import ru.steam.entity.dto.ItemDto;

@Mapper(componentModel = "spring")
public interface ItemSnapshotMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "date", ignore = true)
    @Mapping(target = "priceInitial", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "difference", ignore = true)
    @Mapping(target = "purchaseDate", ignore = true)
    @Mapping(target = "holdTime", ignore = true)
    @Mapping(source = "price", target = "priceNow")
    @Mapping(source = "quantity", target = "quantity")
    ItemSnapshot toItemSnapshot(ItemDto dto);
}
