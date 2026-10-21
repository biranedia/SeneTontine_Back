package com.senetontine.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

// Client de l'API NabooPay v2 (paiements Wave / Orange Money / carte au Senegal).
// La cle API et le secret de webhook ne quittent jamais le serveur.
@Component
public class NabooPayClient {

    private final String baseUrl;
    private final String apiKey;
    private final String webhookSecret;
    private final JsonMapper jsonMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public NabooPayClient(@Value("${naboopay.base-url:https://api.naboopay.com}") String baseUrl,
                          @Value("${naboopay.api-key:}") String apiKey,
                          @Value("${naboopay.webhook-secret:}") String webhookSecret,
                          JsonMapper jsonMapper) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
        this.jsonMapper = jsonMapper;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !webhookSecret.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transaction(@JsonProperty("order_id") String orderId,
                              @JsonProperty("checkout_url") String checkoutUrl,
                              @JsonProperty("transaction_status") String status,
                              BigDecimal amount) {}

    public Transaction createTransaction(String productName, BigDecimal amount, String firstName, String lastName,
                                         String phone, String successUrl, String errorUrl) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "method_of_payment", List.of("wave", "orange_money"),
                "products", List.of(Map.of("name", productName, "price", amount.longValue(), "quantity", 1,
                        "description", productName)),
                "customer", Map.of("first_name", firstName, "last_name", lastName, "phone", phone),
                "success_url", successUrl,
                "error_url", errorUrl,
                "fees_customer_side", true, // frais NabooPay payes par le createur : le prix est recu en entier
                "is_escrow", false,
                "is_merchant", false);
        HttpRequest req = request("/api/v2/transactions")
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)))
                .build();
        return send(req);
    }

    public Transaction getTransaction(String orderId) throws IOException, InterruptedException {
        HttpRequest req = request("/api/v2/transactions/" + URLEncoder.encode(orderId, StandardCharsets.UTF_8)).GET().build();
        return send(req);
    }

    // Signature du webhook : HMAC-SHA256 (hex) du corps JSON, en-tete X-Signature. On verifie
    // sur le corps brut recu ; et, par prudence (la doc NabooPay signe le JSON "compact"), sur
    // sa forme compacte re-serialisee. Comparaison a temps constant.
    public boolean isValidSignature(String rawBody, String signature) {
        if (signature == null || signature.isBlank() || webhookSecret.isBlank()) return false;
        if (matches(hmac(rawBody), signature)) return true;
        try {
            String compact = jsonMapper.writeValueAsString(jsonMapper.readTree(rawBody));
            return matches(hmac(compact), signature);
        } catch (Exception e) {
            return false;
        }
    }

    public String extractOrderId(String rawBody) {
        return jsonMapper.readValue(rawBody, Transaction.class).orderId();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    private Transaction send(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("NabooPay a repondu " + res.statusCode() + " : " + res.body());
        }
        return jsonMapper.readValue(res.body(), Transaction.class);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean matches(String expected, String provided) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
