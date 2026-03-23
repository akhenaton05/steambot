package ru.steam.mapper;

import lombok.Data;
import ru.steam.entity.db.ItemSnapshot;
import ru.steam.entity.db.PnlRecord;

import java.math.BigDecimal;

@Data
public class SnapshotToRecordMapper {

    private static final Double STEAM_FEE = 13.04;

    public static PnlRecord toRecord(ItemSnapshot snapshot) {
        BigDecimal realizedPnl = snapshot.getPriceNow().multiply(BigDecimal.valueOf(snapshot.getQuantity()))
                .subtract((snapshot.getPriceInitial().multiply(BigDecimal.valueOf(snapshot.getQuantity()))));

        return PnlRecord.builder()
                .displayName(snapshot.getDisplayName())
                .owner(snapshot.getOwner())
                .difference(snapshot.getDifference())
                .sellPrice(snapshot.getPriceNow())
                .type(snapshot.getType())
                .holdTime(snapshot.getHoldTime())
                .priceInitial(snapshot.getPriceInitial())
                .quantity(snapshot.getQuantity())
                .realizedProfit(realizedPnl.subtract(realizedPnl.multiply(BigDecimal.valueOf(STEAM_FEE / 100))))
                .build();
    }
}
