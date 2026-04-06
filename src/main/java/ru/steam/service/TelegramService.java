package ru.steam.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.steam.config.TelegramBotConfig;
import ru.steam.entity.ItemType;
import ru.steam.entity.db.ItemSnapshot;
import ru.steam.entity.dto.InventoryDto;
import ru.steam.entity.dto.ItemDto;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TelegramService extends TelegramLongPollingBot {

    private final TrackingService trackingService;
    private final SteamService steamService;
    private final InventoryService inventoryService;
    private final Set<String> chatIds = new HashSet<>();
    private final TelegramBotConfig telegramBotConfig;

    public TelegramService(SteamService steamService,
                           TelegramBotConfig telegramBotConfig,
                           DefaultBotOptions botOptions, TrackingService trackingService, InventoryService inventoryService) {
        super(botOptions);
        this.steamService = steamService;
        this.telegramBotConfig = telegramBotConfig;
        this.trackingService = trackingService;
        this.inventoryService = inventoryService;
    }

    @Override
    public String getBotUsername() {
        return telegramBotConfig.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return telegramBotConfig.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            Long userId = update.getMessage().getChat().getId();
            chatIds.add(String.valueOf(chatId));
            log.info("Received message: {} from chatId: {}", messageText, chatId);

            if (messageText.startsWith("/")) {
                try {
                    handleCommand(chatId, messageText, userId);
                } catch (TelegramApiException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void handleCommand(Long chatId, String fullMessage, Long userId) throws TelegramApiException, InterruptedException {
        if (fullMessage == null || fullMessage.trim().isEmpty()) {
            sendMessage(chatId, "Пустое сообщение. Используйте /start для справки.");
            return;
        }

        String[] parts = fullMessage.trim().split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "/start":
                sendStartMessage(chatId);
                break;

            case "/inventory":
                if (args.isEmpty()) {
                    checkInventory(chatId, userId, "");
                } else {
                    checkInventory(chatId, userId, args);
                }
                break;

            case "/portfolio":
                handlePortfolio(chatId, userId);
                break;

            case "/track":
                if (args.isEmpty()) {
                    sendMessage(chatId, "*Set Steam ID:* /track 76561198158734100");
                } else {
                    trackSteamId(userId, args.trim());
                    sendMessage(chatId, "*Added ID*: " + args.trim());
                }
                break;

            default:
                sendMessage(chatId, "Неизвестная команда: " + command + "\n\n" +
                        "Доступные команды:\n" +
                        "/start - справка по использованию\n" +
                        "/inventory [id/vanity] - просмотр инвентаря (опционально Steam ID)\n" +
                        "/portfolio - просмотр портфолио\n" +
                        "/track <id/vanity> - отслеживание юзера");
        }
    }

    private void sendStartMessage(Long chatId) {
        sendMessageWithPhoto(chatId,
                "\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11\uD83E\uDD11 \n \n" +
                        "WELCOME TO *DELETZ BOT* \n \n" +
                        "\uD83E\uDDF1 Use /price <ItemName> to check item price on Steam Market \n" +
                        "\uD83E\uDDEE Use /portfolio <SteamID> to track your steam inventory \n" +
                        "\uD83D\uDCE6 Use /inventory <SteamID> to check total inventory value \n \n" +
                        "\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8",
                "img_start.png");
    }

    private void trackSteamId(Long userId, String steamId) {
        trackingService.addTrackedId(userId, steamId);
    }

    private void checkInventory(Long chatId, Long userId, String messageText) throws InterruptedException {
        String steamId = messageText.replace("/inventory", "").trim();
        if (steamId.isEmpty()) {
            Set<String> ids = trackingService.getTrackedIds(userId);

            if (ids.isEmpty()) {
                sendMessage(chatId, "No tracked IDs. Use /track <steamId> to add one.");
                return;
            }

            for (String id : ids) {
                InventoryDto inventory = steamService.getInventory(id);
                List<String> messages = formatInventoryMessages(inventory);
                for (String s : messages) {
                    sendMessage(chatId, s);
                }
            }
            return;
        }

        log.info("Sending request to steamService with {}", steamId);
        InventoryDto inventory = steamService.getInventory(steamId);
        List<String> messages = formatInventoryMessages(inventory);
        for (String s : messages) {
            sendMessage(chatId, s);
        }
    }

    public List<String> formatInventoryMessages(InventoryDto dto) {
        List<String> messages = new ArrayList<>();

        Map<ItemType, List<ItemDto>> grouped = dto.getItems().stream()
                .collect(Collectors.groupingBy(item -> {
                    // item.getType() уже содержит displayName: "Container", "Sticker", etc.
                    // item.getDisplayName() содержит имя предмета: "Fracture Case", "Austin Capsule"
                    try {
                        // Пробуем найти по displayName
                        for (ItemType t : ItemType.values()) {
                            if (t.getDisplayName().equalsIgnoreCase(item.getType())) {
                                // Уточняем Container → CASE или CAPSULE по имени предмета
                                if (t == ItemType.CASE) {
                                    String nameLower = item.getDisplayName().toLowerCase();
                                    if (nameLower.contains("capsule") || nameLower.contains("package"))
                                        return ItemType.CAPSULE;
                                    return ItemType.CASE;
                                }
                                return t;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    return ItemType.UNKNOWN;
                }));

        // Остальное без изменений
        Map<ItemType, BigDecimal> groupSums = grouped.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(ItemDto::getTotalValue)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                ));

        StringBuilder summary = new StringBuilder();
        summary.append("*🤖 [SteamBot]*\n");
        summary.append("*👤 Profile: *").append(dto.getSteamName()).append("\n");
        summary.append("*💰 Summary: *").append(String.format("%,.2f", dto.getTotalValue()))
                .append(" ₽ · ").append(dto.getTotalItems()).append(" items\n\n");
        summary.append("💰* Balance Total: *").append(String.format("%,.2f ₽", dto.getTotalValue())).append("\n");
        summary.append("📦* items Total: *").append(dto.getTotalItems()).append(" pcs\n\n");
        summary.append("📊* Inventory Profile:*\n");

        groupSums.entrySet().stream()
                .sorted(Map.Entry.<ItemType, BigDecimal>comparingByValue().reversed())
                .forEach(e -> {
                    double percent = e.getValue().doubleValue() / dto.getTotalValue().doubleValue() * 100;
                    summary.append(String.format("%s %-15s — %3d pcs · %,.2f ₽ (%,.0f%%)\n",
                            getIcon(e.getKey()),
                            e.getKey().getDisplayName(),
                            grouped.get(e.getKey()).size(),
                            e.getValue(),
                            percent));
                });

        messages.add(summary.toString());

        grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(
                        Comparator.comparingDouble(list ->
                                -list.stream().mapToDouble(i -> i.getTotalValue().doubleValue()).sum())
                ))
                .forEach(entry -> {
                    ItemType type = entry.getKey();
                    List<ItemDto> items = entry.getValue().stream()
                            .sorted(Comparator.comparing(ItemDto::getTotalValue).reversed())
                            .toList();
                    BigDecimal groupSum = groupSums.get(type);

                    StringBuilder msg = new StringBuilder();
                    msg.append(getIcon(type)).append(" ")
                            .append(type.getDisplayName())
                            .append(": ").append(String.format("%,.2f ₽", groupSum)).append("\n\n");

                    items.forEach(item ->
                            msg.append("🧱 ").append(item.getDisplayName()).append("\n")
                                    .append("Qty: ").append(item.getQuantity())
                                    .append(" | Price: ").append(String.format("%,.2f ₽", item.getPrice()))
                                    .append(" | Total: ").append(String.format("%,.2f ₽", item.getTotalValue()))
                                    .append("\n\n")
                    );

                    messages.add(msg.toString());
                });

        return messages;
    }

    private String getIcon(ItemType type) {
        return switch (type) {
            case CASE -> "📦";
            case CAPSULE -> "💊";
            case STICKER -> "🎨";
            case CHARM -> "🪬";
            case SKIN -> "🔫";
            case KNIFE -> "🗡️";
            case GLOVES -> "🧤";
            case PATCH -> "🔰";
            default -> "▪️";
        };
    }

    private void handlePortfolio(Long chatId, Long userId) {
        Set<String> ids = trackingService.getTrackedIds(userId);
        if (ids.isEmpty()) {
            sendMessage(chatId, "No tracked IDs. Use /track <steamId> to add one.");
            return;
        }

        for (String id : ids) {
            String report = validatePortfolioMessage(inventoryService.getPortfolioReport(steamService.steamIdToName(id)));
            sendMessage(chatId, report);
        }
    }

    private String validatePortfolioMessage(List<ItemSnapshot> items) {
        if (items.isEmpty()) return "📭 No data";
        String steamName = items.getFirst().getOwner();

        BigDecimal totalNow = items.stream()
                .map(i -> i.getPriceNow().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInitial = items.stream()
                .map(i -> i.getPriceInitial().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiff = totalNow.subtract(totalInitial);
        String totalSign = totalDiff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";

        StringBuilder sb = new StringBuilder();
        sb.append("*🤖 [SteamBot]*\n");
        sb.append("*📊 Portfolio Report*\n");
        sb.append("*\uD83D\uDDFF Profile: *").append(steamName).append("\n\n");
        sb.append(String.format("💰 Now:     *%,.1f ₽*\n", totalNow));
        sb.append(String.format("📌 Initial: *%,.1f ₽*\n", totalInitial));
        sb.append(String.format("\uD83D\uDC51 PnL:     *%s%,.1f ₽*\n\n", totalSign, totalDiff));

        // Топ 3 gainers
        sb.append("📈 *Top Gainers:*\n");
        items.stream()
                .sorted(Comparator.<ItemSnapshot, BigDecimal>comparing(i ->
                        i.getPriceNow().subtract(i.getPriceInitial())
                                .multiply(BigDecimal.valueOf(i.getQuantity()))).reversed())
                .limit(3)
                .forEach(i -> {
                    BigDecimal pnl = i.getPriceNow()
                            .subtract(i.getPriceInitial())
                            .multiply(BigDecimal.valueOf(i.getQuantity()));
                    String sign = pnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                    sb.append(String.format("*%s*  %s  (%s%,.1f ₽)\n",
                            i.getDisplayName(), i.getDifference(), sign, pnl));
                });

        // Топ 3 losers
        sb.append("\n📉 *Top Losers:*\n");
        items.stream()
                .sorted(Comparator.comparing(i ->
                        i.getPriceNow().subtract(i.getPriceInitial())
                                .multiply(BigDecimal.valueOf(i.getQuantity()))))
                .limit(3)
                .forEach(i -> {
                    BigDecimal pnl = i.getPriceNow()
                            .subtract(i.getPriceInitial())
                            .multiply(BigDecimal.valueOf(i.getQuantity()));
                    String sign = pnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                    sb.append(String.format("*%s*  %s  (%s%,.1f ₽)\n",
                            i.getDisplayName(), i.getDifference(), sign, pnl));
                });

        // По типам
        sb.append("\n📦 *By Type:*\n");
        items.stream()
                .collect(Collectors.groupingBy(ItemSnapshot::getType))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(
                        Comparator.comparingDouble(list ->
                                -list.stream()
                                        .mapToDouble(i -> i.getPriceNow().doubleValue() * i.getQuantity())
                                        .sum())
                ))
                .forEach(e -> {
                    double sum = e.getValue().stream()
                            .mapToDouble(i -> i.getPriceNow().doubleValue() * i.getQuantity())
                            .sum();
                    int totalPcs = e.getValue().stream()
                            .mapToInt(ItemSnapshot::getQuantity)
                            .sum();
                    sb.append(String.format("*%-10s* %,.1f ₽  (%d pcs)\n",
                            e.getKey(), sum, totalPcs));
                });

        return sb.toString();
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("Markdown");

        try {
            execute(message);
            log.info("Sent message to Telegram chat {}: {}", chatId, text);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to Telegram chat {}: {}", chatId, e.getMessage());
        }
    }

    private void sendMessageWithPhoto(Long chatId, String text, String image) {
        SendPhoto msg = SendPhoto
                .builder()
                .chatId(chatId)
                .photo(new InputFile(new File(image)))
                .caption(text)
                .parseMode("Markdown")
                .build();
        try {
            execute(msg);
            log.info("Sent message to Telegram chat {}: {}", chatId, text);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to Telegram chat {}: {}", chatId, e.getMessage());
        }
    }
}