package ru.steam.entity;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Getter
@Slf4j
public enum ItemType {
    SKIN("Skin"),
    GLOVES("Gloves"),
    KNIFE("Knife"),
    STICKER("Sticker"),
    CHARM("Charm"),
    CASE("Case"),        // ← кейсы
    CAPSULE("Capsule"),  // ← капсулы
    PATCH("Patch"),
    UNKNOWN("Unknown");

    private final String displayName;
    ItemType(String displayName) { this.displayName = displayName; }

    private static final Set<String> WEAPON_TYPES = Set.of(
            "Pistol", "Rifle", "Shotgun", "SMG", "Sniper Rifle",
            "Machinegun", "Machine Gun"
    );
    private static final Set<String> GLOVE_KEYWORDS = Set.of("Gloves", "Wraps");

    // Основной метод — используй везде вместо старого
    public static ItemType fromType(String type, String name) {
        if (type == null || type.isBlank()) return UNKNOWN;

        String lower = type.toLowerCase();
        String nameLower = name != null ? name.toLowerCase() : "";

        // Сначала проверяем имя предмета — оно точнее
        if (nameLower.contains("capsule") || nameLower.contains("package")) return CAPSULE;

        if (lower.contains("sticker") || nameLower.startsWith("sticker")) return STICKER;
        if (lower.contains("charm")   || nameLower.startsWith("charm"))   return CHARM;
        if (lower.contains("patch"))  return PATCH;

        if (lower.equals("case") || lower.contains("container")) return CASE;
        if (lower.equals("capsule"))  return CAPSULE;
        if (lower.equals("skin"))     return SKIN;
        if (lower.equals("gloves"))   return GLOVES;
        if (lower.equals("knife"))    return KNIFE;

        if (lower.contains("extraordinary")) {
            if (GLOVE_KEYWORDS.stream().anyMatch(nameLower::contains)) return GLOVES;
            return KNIFE;
        }

        boolean hasWeaponType = WEAPON_TYPES.stream()
                .anyMatch(w -> lower.contains(w.toLowerCase()));
        if (hasWeaponType) return SKIN;

        log.debug("fromType unresolved: type='{}', name='{}'", type, name);
        return UNKNOWN;
    }
//    public static ItemType fromType(String type, String name) {
//        if (type == null || type.isBlank()) return UNKNOWN;
//
//        String lower = type.toLowerCase();
//        String nameLower = name != null ? name.toLowerCase() : "";
//
//        if (nameLower.contains("capsule") || nameLower.contains("package")) return CAPSULE;
//        if (lower.contains("sticker"))   return STICKER;
//        if (lower.contains("charm"))     return CHARM;
//        if (lower.contains("patch"))     return PATCH;
//
//        // Container — уточняем по имени: капсула или кейс
//        if (lower.equals("case") || lower.contains("container")) {
//            if (nameLower.contains("capsule") || nameLower.contains("package"))
//                return CAPSULE;
//            return CASE;
//        }
//
//        if (lower.contains("extraordinary")) {
//            if (GLOVE_KEYWORDS.stream().anyMatch(lower::contains)) return GLOVES;
//            return KNIFE;
//        }
//
//        if (lower.contains("covert") && lower.contains("knife")) return KNIFE;
//
//        boolean hasWeaponType = WEAPON_TYPES.stream()
//                .anyMatch(w -> lower.contains(w.toLowerCase()));
//        if (hasWeaponType) return SKIN;
//
//        log.debug("fromType unresolved: type='{}', name='{}'", type, name);
//        return UNKNOWN;
//    }

    // Перегрузка для обратной совместимости
    public static ItemType fromType(String type) {
        return fromType(type, null);
    }
}
