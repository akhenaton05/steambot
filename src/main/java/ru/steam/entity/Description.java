package ru.steam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Description {
    @JsonProperty("classid")
    private String classId;
    @JsonProperty("instanceid")
    private String instanceId;

    @JsonProperty("market_hash_name")
    private String marketHashName;  // "Tec-9 | Tiger Stencil (Field-Tested)"
    private String name;            // "Tec-9 | Tiger Stencil"
    private String type;            // "Industrial Grade Pistol"
    private int tradable;           // 1 = tradable
    private int marketable;         // 1 = marketable
}
