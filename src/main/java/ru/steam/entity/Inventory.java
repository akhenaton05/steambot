package ru.steam.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {
    private String steamId;
    private List<Item> items;
    private int totalItemCount;
    private boolean hasMorePages;        // true если нужна следующая страница
    private String lastAssetId;          // для передачи в следующий запрос
    private BigDecimal totalValue;
}