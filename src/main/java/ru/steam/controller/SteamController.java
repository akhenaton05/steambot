package ru.steam.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.steam.entity.dto.InventoryDto;
import ru.steam.service.InventoryService;
import ru.steam.service.SteamService;

@Slf4j
@RestController
@RequestMapping("/api/steam")
@AllArgsConstructor
public class SteamController {
    private final SteamService steamService;
    private final InventoryService itemsService;

//    @GetMapping
//    @ResponseStatus(HttpStatus.ACCEPTED)
//    public String getInventory(@RequestParam(name = "steamId") String steamId) {
//        //76561198158734100
//        return steamService.getInventory(steamId);
//    }

    // Меняем возвращаемый тип: был String (сырой JSON), стал CsInventory
    // Spring автоматически сериализует CsInventory → JSON через Jackson
    @GetMapping("/inventory")
    public ResponseEntity<InventoryDto> getInventory(
            @RequestParam(name = "steamId") String steamId) {
        try {
            InventoryDto inventory = steamService.getInventory(steamId);
            if (inventory == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            return ResponseEntity.ok(inventory);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // восстанавливаем флаг прерывания потока
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/portfolio")
    public void getPortfolio() {
        try {
            steamService.takeInventoriesSnapshot();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
