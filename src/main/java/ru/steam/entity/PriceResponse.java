package ru.steam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PriceResponse {
    private boolean success;

    @JsonProperty("lowest_price")
    private String lowestPrice;   // "25,37 руб." — строка, не число

    @JsonProperty("median_price")
    private String medianPrice;

    private String volume;        // "1 234" — тоже строка, с пробелом
}