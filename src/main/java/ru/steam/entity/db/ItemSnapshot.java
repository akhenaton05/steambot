package ru.steam.entity.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "items")
@AllArgsConstructor
@NoArgsConstructor
public class ItemSnapshot {
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
    @Column(name = "amount")
    private int quantity;
    @Column(name = "today_price")
    private BigDecimal priceNow;
    @Column
    private String difference;
    @Column(name = "update_date")
    private LocalDate date;
    @Column(name = "hold_time")
    private Integer holdTime;
    @Column(name = "purchase_date")
    private LocalDate purchaseDate;
}
