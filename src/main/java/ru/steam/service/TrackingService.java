package ru.steam.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TrackingService {

    private final Map<Long, Set<String>> trackedIds = new HashMap<>();

    public void addTrackedId(Long userID, String steamId) {
        trackedIds.computeIfAbsent(userID, k -> new HashSet<>()).add(steamId);
    }

    public void removeTrackedId(Long userID, String steamId) {
        trackedIds.getOrDefault(userID, Collections.emptySet()).remove(steamId);
    }

    public Set<String> getTrackedIds(Long userID) {
        return trackedIds.getOrDefault(userID, Collections.emptySet());
    }

    public Set<Long> getAllChatIds() {
        return trackedIds.keySet();
    }

    public Set<String> getAllSteamIds() {
        return trackedIds.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }
}