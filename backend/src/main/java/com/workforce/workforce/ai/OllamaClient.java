package com.workforce.workforce.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Talks to the local Ollama server (default http://localhost:11434). */
@Component
public class OllamaClient {

    public static class OllamaException extends RuntimeException {
        private final int status;
        public OllamaException(int status, String message) {
            super(message);
            this.status = status;
        }
        public int getStatus() { return status; }
    }

    private final RestClient http;
    private final String model;

    public OllamaClient(@Value("${ollama.url:http://localhost:11434}") String url,
                        @Value("${ollama.model:llama3.2:3b}") String model,
                        @Value("${ollama.timeout-seconds:120}") long timeoutSeconds) {
        this.model = model;
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    /** Loads the model into memory when the backend starts, so the first real question is not slow. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread t = new Thread(() -> {
            try {
                send(List.of(Map.of("role", "user", "content", "hi")), 1);
            } catch (RuntimeException ignored) {
                // Ollama is not running yet: nothing to warm up
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /** messages: list of {role, content}. Returns the model's reply text. */
    public String chat(List<Map<String, String>> messages) {
        return send(messages, 250);
    }

    private String send(List<Map<String, String>> messages, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("keep_alive", "2h");   // keep the model in memory between questions
        body.put("options", Map.of("temperature", 0.2, "num_predict", maxTokens, "num_ctx", 2048));
        body.put("messages", messages);

        try {
            Map<String, Object> res = http.post().uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            Object message = res == null ? null : res.get("message");
            Object content = message instanceof Map<?, ?> m ? m.get("content") : null;
            if (content == null || content.toString().isBlank()) {
                throw new OllamaException(502, "The chatbot gave an empty answer. Try again.");
            }
            return content.toString().trim();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new OllamaException(503, "Chat model not found. Run: ollama pull " + model);
            }
            throw new OllamaException(502, "The chatbot returned an error.");
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof HttpTimeoutException) {
                throw new OllamaException(504, "The chatbot took too long to answer. Try a shorter question.");
            }
            throw new OllamaException(503, "Chatbot offline. Start Ollama (run: ollama serve) and try again.");
        }
    }
}
