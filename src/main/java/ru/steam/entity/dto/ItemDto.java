package ru.steam.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ItemDto {
    private String displayName;
    private String type;
    private int quantity;
    private BigDecimal price;
    private BigDecimal totalValue;
}
