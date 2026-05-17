package ru.steam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class PriceService {

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String PRICE_URL = "https://steamcommunity.com/market/priceoverview/";

    private static final int DELAY_MS = 3000; //Delay before next query

    public Optional<BigDecimal> fetchPrice(String marketHashName) throws InterruptedException {
        Thread.sleep(DELAY_MS);
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

                if (!price.isSuccess() || price.getMedianPrice() == null) {
                    if (price.getLowestPrice() != null) {
                        price.setMedianPrice(price.getLowestPrice());
                    }
                    else return Optional.empty();
                }

                return Optional.of(parsePrice(price.getMedianPrice()));
            }

        } catch (Exception e) {
            log.error("Error fetching price for {}: {}", marketHashName, e.getMessage());
            return Optional.empty();
        }
    }

    private BigDecimal parsePrice(String priceStr) {
        //API response: "25,37 руб.", "1 234,56 руб."
        //Parsed: 25.37, 1234.56
        String cleaned = priceStr
                .replace(" руб.", "")
                .replace("\u00a0", "")
                .replace(" ", "")
                .replace(",", ".");

        return new BigDecimal(cleaned);
    }

    public void setItemPrice(Inventory inventory) throws InterruptedException {
        BigDecimal totalValue = BigDecimal.ZERO;
        int count = 0;

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

            count++;

            log.info("[PriceService] Fetched {}/{}: {} → {}",
                    count, inventory.getItems().size(),
                    item.getMarketHashName(),
                    price.map(BigDecimal::toString).orElse("no price"));
        }

        inventory.setTotalValue(totalValue);
    }
}