package com.silenker.parkingrecommendation.service;

public interface IAiService {
    String chat(String userMessage);

    default String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt + "\n\n" + userMessage);
    }
}
