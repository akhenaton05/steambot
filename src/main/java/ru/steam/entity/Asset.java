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
public class Asset {
    @JsonProperty("assetid")
    private String assetId;
    @JsonProperty("classid")
    private String classId;
    @JsonProperty("instanceid")
    private String instanceId;
    private String amount;
}
