package ru.steam;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.steam.service.TelegramService;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

@Slf4j
@EnableScheduling
@SpringBootApplication
public class SteamApplication {
    public static void main(String[] args) {
        System.setProperty("java.net.socks.username", "user46073");
        System.setProperty("java.net.socks.password", "uhomz8");

        // Authenticator for SOCKS5
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("user46073", "uhomz8".toCharArray());
            }
        });

        ApplicationContext context = SpringApplication.run(SteamApplication.class, args);
        TelegramService telegramService = context.getBean(TelegramService.class);

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramService);
            log.info("Telegram bot registered successfully!");
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot: " + e.getMessage());
            if (!e.getMessage().contains("404")) {
                e.printStackTrace();
            }
        }
    }
}
