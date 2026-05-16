package ru.steam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryResponse {
    private List<Asset> assets;
    private List<Description> descriptions;
    
    @JsonProperty("more_items")
    private int moreItems;
    
    @JsonProperty("last_assetid")
    private String lastAssetid;
    
    @JsonProperty("total_inventory_count")
    private int totalInventoryCount;
}