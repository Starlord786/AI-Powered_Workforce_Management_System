package com.workforce.workforce.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.workforce.workforce.entity.FaceData;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Calls the Python AI service (FastAPI, face_recognition). */
@Component
public class FaceClient {

    public record Match(boolean matched, Long employeeId, double confidence, String reason) {}

    public static class FaceException extends RuntimeException {
        private final int status;
        public FaceException(int status, String message) {
            super(message);
            this.status = status;
        }
        public int getStatus() { return status; }
    }

    private static final Pattern DETAIL = Pattern.compile("\"detail\"\\s*:\\s*\"([^\"]*)\"");

    private final RestClient http;

    public FaceClient(@Value("${app.ai.url:http://localhost:8000}") String baseUrl) {
        // force HTTP/1.1, uvicorn does not like the HTTP/2 upgrade attempt
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Photo -> encoding as a JSON array string, e.g. "[-0.15,0.08,...]". */
    public String encode(byte[] image) {
        Map<?, ?> res = call(() -> http.post().uri("/face/encode")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(imagePart(image))
                .retrieve().body(Map.class));
        List<?> enc = (List<?>) res.get("encoding");
        return enc.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    /** Compare a photo with every stored encoding. */
    public Match match(byte[] image, List<FaceData> faces) {
        MultiValueMap<String, Object> body = imagePart(image);
        body.add("known", knownJson(faces));
        Map<?, ?> res = call(() -> http.post().uri("/face/match")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve().body(Map.class));
        if (Boolean.TRUE.equals(res.get("match"))) {
            return new Match(true,
                    ((Number) res.get("employee_id")).longValue(),
                    ((Number) res.get("confidence")).doubleValue(), null);
        }
        Object reason = res.get("reason");
        return new Match(false, null, 0, reason == null ? "Face not recognised" : reason.toString());
    }

    /** [{"employee_id":1,"encoding":[...]}, ...] built by hand, encodings are already JSON. */
    private static String knownJson(List<FaceData> faces) {
        return faces.stream()
                .map(f -> "{\"employee_id\":" + f.getEmployee().getId() + ",\"encoding\":" + f.getEncoding() + "}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static MultiValueMap<String, Object> imagePart(byte[] image) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(image) {
            @Override
            public String getFilename() { return "face.jpg"; }
        });
        return body;
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            Matcher m = DETAIL.matcher(e.getResponseBodyAsString());
            String msg = m.find() ? m.group(1) : "Face service error";
            int status = e.getStatusCode().value();
            throw new FaceException(status >= 500 ? 502 : status, msg);
        } catch (ResourceAccessException e) {
            throw new FaceException(503, "Face service is offline. Start the AI service (port 8000).");
        }
    }
}
