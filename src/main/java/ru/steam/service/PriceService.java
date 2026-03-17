package ru.steam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.springframework.stereotype.Service;
import ru.steam.entity.Inventory;
import ru.steam.entity.Item;
import ru.steam.entity.PriceResponse;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Service
public class PriceService {

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String PRICE_URL =
        "https://steamcommunity.com/market/priceoverview/";

    private static final int DELAY_MS = 3000; // 3 секунды между запросами

    public PriceService(CloseableHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public Optional<BigDecimal> fetchPrice(String marketHashName) {
        try {
            URI uri = new URIBuilder(PRICE_URL)
                    .addParameter("appid", "730")
                    .addParameter("currency", "5")  // 5 = RUB
                    .addParameter("market_hash_name", marketHashName)
                    .build();

            HttpGet get = new HttpGet(uri);

            try (CloseableHttpResponse resp = httpClient.execute(get)) {
                if (resp.getCode() == 429) {
                    log.warn("Rate limited for item: {}", marketHashName);
                    return Optional.empty();
                }

                String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                PriceResponse price = objectMapper.readValue(body, PriceResponse.class);

                if (!price.isSuccess() || price.getLowestPrice() == null) {
                    return Optional.empty(); // предмет не продаётся
                }

                return Optional.of(parsePrice(price.getLowestPrice()));
            }

        } catch (Exception e) {
            log.error("Error fetching price for {}: {}", marketHashName, e.getMessage());
            return Optional.empty();
        }
    }

    private BigDecimal parsePrice(String priceStr) {
        // Приходит: "25,37 руб."  или  "1 234,56 руб."
        // Нужно:     25.37              1234.56

        String cleaned = priceStr
                .replace(" руб.", "")   // убираем валюту
                .replace("\u00a0", "")  // убираем неразрывный пробел (часто в числах)
                .replace(" ", "")       // убираем обычный пробел (разделитель тысяч)
                .replace(",", ".");     // запятая → точка

        // cleaned теперь "25.37"
        return new BigDecimal(cleaned);
    }

    // Главный метод — обогащает весь инвентарь ценами
    public void enrichWithPrices(Inventory inventory) throws InterruptedException {
        BigDecimal totalValue = BigDecimal.ZERO;
        int processed = 0;

        for (Item item : inventory.getItems()) {
            Optional<BigDecimal> price = fetchPrice(item.getMarketHashName());

            if (price.isPresent()) {
                BigDecimal unitPrice = price.get();
                BigDecimal itemTotal = unitPrice.multiply(
                        BigDecimal.valueOf(item.getQuantity())
                );

                item.setPrice(unitPrice);
                item.setTotalValue(itemTotal);
                totalValue = totalValue.add(itemTotal);
            }

            processed++;
            log.info("[PriceService] Fetched {}/{}: {} → {}",
                    processed, inventory.getItems().size(),
                    item.getMarketHashName(),
                    price.map(BigDecimal::toString).orElse("no price"));

            // пауза ПОСЛЕ каждого запроса, кроме последнего
            if (processed < inventory.getItems().size()) {
                Thread.sleep(DELAY_MS);
            }
        }

        inventory.setTotalValue(totalValue);
    }
}