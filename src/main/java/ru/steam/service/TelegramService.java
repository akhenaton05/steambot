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
import ru.steam.entity.dto.InventoryDto;
import ru.steam.entity.dto.ItemDto;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TelegramService extends TelegramLongPollingBot {

    private final SteamService steamService;
    private final InventoryService inventoryService;
    private final Set<String> chatIds = new HashSet<>();
    private final TelegramBotConfig telegramBotConfig;

    public TelegramService(SteamService steamService,
                           TelegramBotConfig telegramBotConfig,
                           DefaultBotOptions botOptions, InventoryService inventoryService) {
        super(botOptions); // ← прокси передаётся в библиотеку
        this.steamService = steamService;
        this.telegramBotConfig = telegramBotConfig;
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
                    checkInventory(chatId, "");
                } else {
                    checkInventory(chatId, args);
                }
                break;

            case "/portfolio":
                handlePortfolio(chatId);
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
                "\uD83D\uDCE6 Use /inventory <SteamID> to check total inventory value \n \n"+
                "\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8\uD83D\uDCB8",
                "img_start.png");
    }

    private void checkInventory(Long chatId, String messageText) throws InterruptedException {
        String steamId = messageText.replace("/inventory", "").trim();
        if (steamId.isEmpty()) {
            sendMessage(chatId, "⚠️Please provide valid SteamID! Example: `76561198207609671`");
            return;
        }

        log.info("Sending request to steamService with {}", steamId);
        InventoryDto inventory = steamService.getInventory(steamId);
        List<String> messages = formatInventoryMessages(inventory);
        for(String s : messages) {
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
                    } catch (Exception ignored) {}
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
            case CASE    -> "📦";
            case CAPSULE -> "💊";
            case STICKER -> "🎨";
            case CHARM   -> "🪬";
            case SKIN    -> "🔫";
            case KNIFE   -> "🗡️";
            case GLOVES  -> "🧤";
            case PATCH   -> "🔰";
            default      -> "▪️";
        };
    }


    private void handlePortfolio(Long chatId) {
        String report = inventoryService.getPortfolioReport();
        sendMessage(chatId, report);
    }


//    public List<String> formatInventoryMessages(InventoryDto dto) {
//        List<String> messages = new ArrayList<>();
//
//        Map<ItemType, List<ItemDto>> grouped = dto.getItems().stream()
//                .collect(Collectors.groupingBy(
//                        item -> ItemType.fromType(item.getType(), item.getDisplayName())
//                ));
//
//        Map<ItemType, BigDecimal> groupSums = grouped.entrySet().stream()
//                .collect(Collectors.toMap(
//                        Map.Entry::getKey,
//                        e -> e.getValue().stream()
//                                .map(ItemDto::getTotalValue)
//                                .reduce(BigDecimal.ZERO, BigDecimal::add)
//                ));
//
//        // --- Сообщение 1: Сводка ---
//        StringBuilder summary = new StringBuilder();
//        summary.append("*🤖 [SteamBot]*\n");
//        summary.append("*👤 Profile: *").append(dto.getSteamName()).append("\n");
//        summary.append("*💰 Summary: *").append(String.format("%,.2f", dto.getTotalValue()))
//                .append(" ₽ · ").append(dto.getTotalItems()).append(" items\n\n");
//
//        summary.append("💰* Balance Total: *").append(String.format("%,.2f ₽", dto.getTotalValue())).append("\n");
//        summary.append("📦* items Total: *").append(dto.getTotalItems()).append(" pcs\n\n");
//
//        summary.append("📊* Inventory Profile:*\n");
//
//        groupSums.entrySet().stream()
//                .sorted(Map.Entry.<ItemType, BigDecimal>comparingByValue().reversed())
//                .forEach(e -> {
//                    double percent = e.getValue().doubleValue() / dto.getTotalValue().doubleValue() * 100;
//                    summary.append(String.format("%s %-15s — %3d pec · %,.2f ₽ (%,.0f%%)\n",
//                            getIcon(e.getKey()),
//                            e.getKey().getDisplayName(),
//                            grouped.get(e.getKey()).size(),
//                            e.getValue(),
//                            percent));
//                });
//
//        messages.add(summary.toString());
//
//        // --- Сообщения 2..N: по одному на каждый тип ---
//        grouped.entrySet().stream()
//                .sorted(Map.Entry.comparingByValue(
//                        Comparator.comparingDouble(list ->
//                                -list.stream().mapToDouble(i -> i.getTotalValue().doubleValue()).sum())
//                ))
//                .forEach(entry -> {
//                    ItemType type = entry.getKey();
//                    List<ItemDto> items = entry.getValue().stream()
//                            .sorted(Comparator.comparing(ItemDto::getTotalValue).reversed())
//                            .toList();
//                    BigDecimal groupSum = groupSums.get(type);
//
//                    StringBuilder msg = new StringBuilder();
//                    msg.append(getIcon(type)).append(" ")
//                            .append(type.getDisplayName())
//                            .append(": ").append(String.format("%,.2f ₽", groupSum)).append("\n\n");
//
//                    items.forEach(item ->
//                            msg.append(item.getDisplayName()).append("\n")
//                                    .append("   Qty: ").append(item.getQuantity())
//                                    .append(" | Price: ").append(String.format("%,.2f ₽", item.getPrice()))
//                                    .append(" | Total: ").append(String.format("%,.2f ₽", item.getTotalValue()))
//                                    .append("\n")
//                    );
//
//                    messages.add(msg.toString());
//                });
//
//        return messages;
//    }
//
//    private String getIcon(ItemType type) {
//        return switch (type) {
//            case CASE      -> "📦";
//            case CAPSULE   -> "\uD83D\uDC8A";
//            case STICKER   -> "\uD83D\uDD16";
//            case CHARM     -> "\uD83C\uDFAD";
//            case SKIN      -> "\uD83C\uDFAD";
//            case KNIFE     -> "🗡️";
//            case GLOVES    -> "🧤";
//            case PATCH     -> "🔰";
//            default        -> "▪️";
//        };
//    }


//    public String formatInventory(InventoryDto dto) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("*🤖 [SteamBot]*\n");
//        sb.append("👤 Profile: *").append(dto.getSteamName()).append("*\n");
//        sb.append("💰 Summary: *").append(String.format("%,.2f", dto.getTotalValue()))
//                .append(" ₽* · ").append(dto.getTotalItems()).append(" items\n");
//
//        Map<ItemType, List<ItemDto>> grouped = dto.getItems().stream()
//                .collect(Collectors.groupingBy(item -> ItemType.fromType(item.getType())));
//
//        grouped.entrySet().stream()
//                .sorted(Map.Entry.comparingByValue(
//                        Comparator.comparingDouble(list ->
//                                -list.stream().mapToDouble(i -> i.getTotalValue().doubleValue()).sum())
//                ))
//                .forEach(entry -> {
//                    ItemType type = entry.getKey();
//                    List<ItemDto> items = entry.getValue().stream()
//                            .sorted(Comparator.comparing(ItemDto::getTotalValue).reversed())
//                            .toList();
//
//                    BigDecimal groupSum = items.stream()
//                            .map(ItemDto::getTotalValue)
//                            .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//                    sb.append("\n").append(getIcon(type))
//                            .append(" ").append(type.getDisplayName().toUpperCase()).append("\n");
//
//                    items.stream().limit(MAX_ITEMS_PER_GROUP).forEach(item ->
//                            sb.append("\uD83E\uDDF1 ").append(item.getDisplayName())
//                                    .append(" — ").append(item.getQuantity()).append(" pcs")
//                                    .append(" × ").append(String.format("%,.2f ₽", item.getPrice()))
//                                    .append(" = ").append(String.format("%,.2f ₽", item.getTotalValue()))
//                                    .append("\n")
//                    );
//
//                    List<ItemDto> rest = items.stream().skip(MAX_ITEMS_PER_GROUP).toList();
//                    if (!rest.isEmpty()) {
//                        BigDecimal restSum = rest.stream()
//                                .map(ItemDto::getTotalValue)
//                                .reduce(BigDecimal.ZERO, BigDecimal::add);
//                        sb.append("▸ + ещё ").append(rest.size())
//                                .append(" · ").append(String.format("%,.2f ₽", restSum)).append("\n");
//                    }
//                });
//
//        return sb.toString();
//    }
//
//

//    public String formatInventoryGrouped(InventoryDto dto) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("*🤖 [SteamBot]*\n");
//        sb.append("👤 Profile: *").append(dto.getSteamName()).append("*\n");
//        sb.append("💰 Summary: *").append(String.format("%,.2f", dto.getTotalValue()))
//                .append(" ₽* · ").append(dto.getTotalItems()).append(" items\n");
//
//        Map<ItemType, List<ItemDto>> grouped = dto.getItems().stream()
//                .collect(Collectors.groupingBy(item -> ItemType.fromType(item.getType())));
//
//        grouped.entrySet().stream()
//                .sorted(Map.Entry.<ItemType, List<ItemDto>>comparingByValue(
//                        Comparator.comparingDouble(list ->
//                                -list.stream().mapToDouble(i -> i.getTotalValue().doubleValue()).sum())
//                ))
//                .forEach(entry -> {
//                    ItemType type = entry.getKey();
//                    List<ItemDto> items = entry.getValue();
//
//                    BigDecimal groupSum = items.stream()
//                            .map(ItemDto::getTotalValue)
//                            .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//                    sb.append("\n").append(getIcon(type.name()))
//                            .append(" *").append(type.getDisplayName().toUpperCase())
//                            .append("*  —  ").append(items.size())
//                            .append(" pcs · ").append(String.format("%,.2f ₽", groupSum)).append("\n");
//
//                    List<ItemDto> sorted = items.stream()
//                            .sorted(Comparator.comparing(ItemDto::getTotalValue).reversed())
//                            .toList();
//
//                    for (int i = 0; i < sorted.size(); i++) {
//                        ItemDto item = sorted.get(i);
//                        boolean isLast = i == sorted.size() - 1;
//                        sb.append(isLast ? "└ " : "├ ");
//                        sb.append(String.format("%-22s x%-3d → %,.2f ₽\n",
//                                item.getDisplayName(), item.getQuantity(), item.getTotalValue()));
//                    }
//                });
//
//        return sb.toString();
//    }
//
//    private String getIcon(String typeName) {
//        ItemType type = ItemType.fromType(typeName);
//        return switch (type) {
//            case CONTAINER -> "📦";
//            case STICKER   -> "🎨";
//            case CHARM     -> "🪬";
//            case SKIN      -> "🔫";
//            case KNIFE     -> "🗡";
//            case GLOVES    -> "🧤";
//            case PATCH     -> "🔰";
//            default        -> "▪️";
//        };
//    }


//
//    private void trackAction(Long chatId, String messageText, long userId) {
//        String steamId = messageText.replace("/track", "").trim();
//        if (steamId.isEmpty()) {
//            sendMessage(chatId, "⚠️Please provide valid SteamID! Example: `76561198207609671`");
//            return;
//        }
//
//        log.info("Sending request to steamService with {}", steamId);
//        steamPortfolioService.trackInventory(steamId, userId);
//        sendMessageWithPhoto(chatId, "📦 Portfolio for user *" + steamService.getSteamNameBySteamId(steamId) + "* is now tracking", "img_count2.png");
//    }
//
//    private void checkPortfolio(Long chatId, Long userId, String messageText) {
//        String steamId = messageText.replace("/inventory", "").trim();
//        List<UserPortfolioDto> userPortfolioList = steamPortfolioService.getUserPortfolio(userId);
//
//        int differenceCounter = 0;
//        StringBuilder message = new StringBuilder();
//
//        Double pnlPersent = 0.0;
//        Double invested = 0.0;
//        Double value = 0.0;
//        for (UserPortfolioDto dto : userPortfolioList) {
//            if (dto.getDifference() >= 0) {
//                differenceCounter++;
//            } else differenceCounter--;
//
//            String header = "📦 Portfolio for user *" + dto.getSteamUsername() + "* \n";
//
//            // Приведение к BigDecimal для точности
//            BigDecimal initialPrice = BigDecimal.valueOf(dto.getInitialPrice());
//            invested = initialPrice.doubleValue();
//            BigDecimal priceNow = BigDecimal.valueOf(dto.getPriceNow());
//            value = priceNow.doubleValue();
//            BigDecimal difference = priceNow.subtract(initialPrice).setScale(2, RoundingMode.HALF_UP);
//
//            // Процентное изменение
//            BigDecimal percentage = BigDecimal.ZERO;
//            if (initialPrice.compareTo(BigDecimal.ZERO) != 0) {
//                percentage = difference.divide(initialPrice, 6, RoundingMode.HALF_UP)
//                        .multiply(BigDecimal.valueOf(100))
//                        .setScale(1, RoundingMode.HALF_UP);
//            }
//            pnlPersent = percentage.doubleValue();
//
//            String body = "Cost: " + initialPrice.setScale(2, RoundingMode.HALF_UP) + " руб. | " +
//                    "Price now: " + priceNow.setScale(2, RoundingMode.HALF_UP) + " руб. \n" +
//                    "Difference: " + (difference.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + difference + " руб. | " +
//                    "%: " + (difference.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + percentage + "%\n\n";
//
//            message.append(header).append(body);
//        }
//
////        sendMessageWithPhoto(chatId, String.valueOf(message), differenceCounter >= 0 ? "img_2.png" : "img_2_loss.png");
////        private void sendPnlImage(Long chatId, double pnlAmount, double pnlPercent, double invested, double value)
////        private void sendPnlImage (Long chatId, BigDecimal pnlAmount, BigDecimal pnlPercent, BigDecimal
////        invested, BigDecimal value)
////        sendPnlImage(chatId, steamId, pnlAmount, pnlPersent, invested, value);
//        sendMessageWithPhoto(chatId, String.valueOf(message), getPnlImage(chatId, steamId,  value - invested, pnlPersent, invested, value));
//    }
//
//    public void sendSteamItemPrice(SteamItem steamItem, Long chatId) {
//        if (steamItem == null || steamItem.getItemPrice() == null) {
//            sendMessage(chatId, "⚠️ *Price data not available for this item.* Try another item or check later.");
//            return;
//        }
//
//        String cleanedPrice = steamItem.getItemPrice().replace(" руб.", "");
//        String messageText = String.format("*Item*: %s\n*Price*: %s руб.",
//                steamItem.getItemName(), cleanedPrice);
//        sendMessage(chatId, messageText);
//    }
//
//    public void sendTotalInventoryPrice(InventoryValueDto valueDto, Long chatId) {
//        if (valueDto.getTotalPriceStr() == null || valueDto.getTotalPriceStr().isEmpty()) {
//            sendMessage(chatId, "⚠️ Total price data not available.");
//            return;
//        }
//
//        String header = "<b>💰 Total inventory price:</b> " + valueDto.getTotalPriceStr() + "\n\n"
//                + "<b>📦 Inventory items:</b>\n\n";
//
//        // HTML безопасный текст
//        String body = valueDto.getInventoryComposition()
//                .replace("&", "&amp;")
//                .replace("<", "&lt;")
//                .replace(">", "&gt;");
//
//        String tail = "\n\n<b>💰 And other items with less than 50 RUB total value:</b> ";
//
//        SendMessage message = new SendMessage();
//        message.setChatId(chatId);
//        message.setText(header + body + tail);
//        message.setParseMode("HTML");
//
//        sendMessage(message);
//    }

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

    private void sendMessageWithPhoto(Long chatId, String text, InputFile image) {
        SendPhoto msg = SendPhoto
                .builder()
                .chatId(chatId)
                .photo(image)
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

    private void sendMessage(SendMessage message) {
        try {
            execute(message);
            log.info("Sent message to Telegram chat");
        } catch (TelegramApiException e) {
            log.error("Failed to send message to Telegram chat");
        }
    }

//    private void sendPnlImage(Long chatId, String steamId, Double pnlAmount, Double pnlPercent, Double invested, Double value) {
//        try {
//            // Генерируем изображение
//            byte[] imageBytes = pnlGenerator.generatePnlImage(steamId, pnlAmount, pnlPercent, invested, value);
//
//            // Отправляем как InputFile из байтов
//            InputFile inputFile = new InputFile();
//            inputFile.setMedia(new ByteArrayInputStream(imageBytes), "pnl.png");
//
//            SendPhoto sendPhoto = SendPhoto.builder()
//                    .chatId(chatId.toString())
//                    .photo(inputFile)
//                    .caption("Твой PNL! 💰 Обезьянка в деле.")
//                    .build();
//
//            execute(sendPhoto);
//        } catch (IOException | TelegramApiException e) {
//            sendMessage(chatId, "Ошибка генерации изображения: " + e.getMessage());
//            e.printStackTrace();  // Для логов
//        }
//    }
//
//    private InputFile getPnlImage(Long chatId, String steamId, Double pnlAmount, Double pnlPercent, Double invested, Double value) {
//        try {
//            // Генерируем изображение
//            byte[] imageBytes = pnlGenerator.generatePnlImage(steamId, pnlAmount, pnlPercent, invested, value);
//
//            // Отправляем как InputFile из байтов
//            InputFile inputFile = new InputFile();
//            inputFile.setMedia(new ByteArrayInputStream(imageBytes), "pnl.png");
//            return inputFile;
//
////            SendPhoto sendPhoto = SendPhoto.builder()
////                    .chatId(chatId.toString())
////                    .photo(inputFile)
////                    .caption("Твой PNL! 💰 Обезьянка в деле.")
////                    .build();
////
////            execute(sendPhoto);
////        } catch (IOException | TelegramApiException e) {
////            sendMessage(chatId, "Ошибка генерации изображения: " + e.getMessage());
////            e.printStackTrace();  // Для логов
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
}