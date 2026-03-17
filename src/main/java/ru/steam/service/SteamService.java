package ru.steam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import ru.steam.config.SteamConfig;
import ru.steam.entity.Asset;
import ru.steam.entity.Description;
import ru.steam.entity.Inventory;
import ru.steam.entity.InventoryResponse;
import ru.steam.entity.dto.InventoryDto;
import ru.steam.mapper.InventoryMapper;
import ru.steam.utils.InventoryParser;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@AllArgsConstructor
public class SteamService {

    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final InventoryParser inventoryParser;
    private final PriceService priceService;
    private final SteamConfig steamConfig;
    private final InventoryMapper inventoryMapper;

    private static final String INVENTORY_URL =
            "https://steamcommunity.com/inventory/{steamId}/730/2";
    private static final String USERNAME_URL = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/";

    private InventoryResponse fetchPage(String steamId, String lastAssetId) {
        try {
            URIBuilder builder = new URIBuilder(
                    "https://steamcommunity.com/inventory/" + steamId + "/730/2")
                    .addParameter("l", "english")
                    .addParameter("count", "2000"); // максимум за запрос

            // добавляем start_assetid только если это не первая страница
            if (lastAssetId != null) {
                builder.addParameter("start_assetid", lastAssetId);
            }

            URI uri = builder.build();
            log.info("[SteamService] GET {}", uri);

            HttpGet get = new HttpGet(uri);
            try (CloseableHttpResponse resp = httpClient.execute(get)) {
                int statusCode = resp.getCode();

                if (statusCode == 429) {
                    log.error("[SteamService] Rate limited (429) for steamId={}", steamId);
                    return null;
                }
                if (statusCode == 403) {
                    log.error("[SteamService] Inventory is private for steamId={}", steamId);
                    return null;
                }

                String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                return objectMapper.readValue(body, InventoryResponse.class);
            }
        } catch (Exception e) {
            log.error("[SteamService] Error fetching inventory for steamId={}", steamId, e);
            return null;
        }
    }

    public InventoryDto getInventory(String steamId) throws InterruptedException {
        List<Asset> allAssets = new ArrayList<>();
        List<Description> allDescriptions = new ArrayList<>();
        String lastAssetId = null;
        int page = 1;
        int totalCount = 0;

        do {
            InventoryResponse pageResponse = fetchPage(steamId, lastAssetId);
            if (pageResponse == null) break;

            allAssets.addAll(pageResponse.getAssets());
            allDescriptions.addAll(pageResponse.getDescriptions());

            if (page == 1) {
                totalCount = pageResponse.getTotalInventoryCount();
            }

            if (pageResponse.getMoreItems() != 1) break;

            lastAssetId = pageResponse.getLastAssetid();
            page++;
            Thread.sleep(4000);
        } while (true);

        InventoryResponse fullResponse = InventoryResponse.builder()
                .assets(allAssets)
                .descriptions(allDescriptions)
                .totalInventoryCount(totalCount)
                .build();

        Inventory inventory = inventoryParser.parse(fullResponse, steamId);

        priceService.enrichWithPrices(inventory);

        InventoryDto dto = inventoryMapper.toDto(inventory);
        dto.setSteamName(steamIdToName(steamId));

        return dto;
    }

    public String steamIdToName(String steamId) {
        log.info("[SteamService]Fetching Steam username for steamId: {}", steamId);

        String url = UriComponentsBuilder.fromHttpUrl(USERNAME_URL)
                .queryParam("key", steamConfig.getApiKey())
                .queryParam("steamids", steamId)
                .toUriString();
        log.debug("[SteamService]Username URL: {}", url);

        HttpGet get = new HttpGet(url);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            if (Objects.isNull(response)) {
                log.error("[SteamService]Empty response from Steam API for steamId: {}", steamId);
                return null;
            }
            log.debug("[SteamService]Response received: {}", response);

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            log.debug("[SteamService]Response body: {}", body);

            JsonNode root = objectMapper.readTree(body);
            JsonNode players = root.path("response").path("players");

            if (players.isArray() && !players.isEmpty()) {
                String personaname = players.get(0).path("personaname").asText();
                log.info("[SteamService]Steam username for {}: {}", steamId, personaname);
                return personaname;
            } else {
                log.warn("[SteamService]No player data found for steamId: {}", steamId);
                return null;
            }
        } catch (Exception e) {
            log.error("[SteamService]Error fetching steam username for {}: {}", steamId, e.getMessage(), e);
            return null;
        }
    }
}