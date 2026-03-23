package ru.steam.entity.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "pnl")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PnlRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Column
    private String owner;
    @Column(name = "item")
    private String displayName;
    @Column
    private String type;
    @Column(name = "initial_price")
    private BigDecimal priceInitial;
    @Column(name = "sell_price")
    private BigDecimal sellPrice;
    @Column(name = "amount")
    private int quantity;
    @Column(name = "realized_pnl")
    private BigDecimal realizedProfit;
    @Column
    private String difference;
    @Column(name = "hold_time")
    private Integer holdTime;
}
