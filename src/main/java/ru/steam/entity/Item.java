package ru.steam.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Item {
    private String marketHashName;
    private String displayName;
    private String type;            // "Base Grade Container"
    private int quantity;
    private BigDecimal price;
    private BigDecimal totalValue;
}