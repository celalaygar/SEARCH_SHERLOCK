package com.sherlock.search.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sherlock.search.model.QueryStatus;
import com.sherlock.search.model.SiteResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@Service
public class SearchService {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:129.0) Gecko/20100101 Firefox/129.0";
    private static final List<String> WAF_HIT_MESSAGES = Arrays.asList(
            ".loading-spinner{visibility:hidden}body.no-js .challenge-running{display:none}body.dark{background-color:#222;color:#d9d9d9}body.dark a{color:#fff}body.dark a:hover{color:#ee730a;text-decoration:underline}body.dark .lds-ring div{border-color:#999 transparent transparent}body.dark .font-red{color:#b20f03}body.dark", // 2024-05-13 Cloudflare
            "<span id=\"challenge-error-text\">", // 2024-11-11 Cloudflare error page
            "AwsWafIntegration.forceRefreshToken", // 2024-11-11 Cloudfront (AWS)
            "{return l.onPageView}}),Object.defineProperty(r,\"perimeterxIdentifiers\",{enumerable:" // 2024-04-09 PerimeterX / Human Security
    );

    private JsonNode siteData;
    private final int timeout = 60;
    private ExecutorService executorService;
    private HttpClient httpClient;


    @PostConstruct
    private void init(){

        ClassPathResource resource = new ClassPathResource("data.json");
        try (InputStream inputStream = resource.getInputStream()) {

            siteData = objectMapper.readTree(inputStream);
            this.executorService = Executors.newFixedThreadPool(Math.min(20, siteData.size()));
            // Proxy handling would require additional setup with HttpClient.Builder
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeout))
                    .build();
        } catch (IOException e) {
            System.err.println("JSON dosyası yüklenirken hata oluştu: " + e.getMessage());
        }  catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }




    public List<SiteResult> searchByName(String name) throws ExecutionException, InterruptedException {

        List<SiteResult> results = search(name);
        //shutdown();
        return results;
    }


    private String interpolateString(String input, String username) {
        return input.replace("{}", username);
    }

    public void shutdown() {
        executorService.shutdown();
    }

    // search metodunda CompletableFuture'ları birleştirirken
    public List<SiteResult> search(String username) throws InterruptedException, ExecutionException {
        List<CompletableFuture<SiteResult>> futures = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = siteData.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String siteName = entry.getKey();
            JsonNode siteInfo = entry.getValue();

            // checkSite zaten asenkron hale getirildiyse, burada tekrar supplyAsync'e gerek kalmaz
            // Veya checkSite'ın kendisini CompletableFuture döndürecek şekilde refactor edebilirsiniz
            CompletableFuture<SiteResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return checkSite(username, siteName, siteInfo);
                } catch (Exception e) {
                    if(!ObjectUtils.isEmpty(siteInfo.get("urlMain")))
                        return new SiteResult(siteName, siteInfo.get("urlMain").asText(), "", QueryStatus.UNKNOWN, "" );
                    return null;
                }
            }, executorService); // Mevcut ExecutorService ile devam edin

            futures.add(future);
        }

        List<SiteResult> results = new ArrayList<>();
        for (CompletableFuture<SiteResult> future : futures) {
            SiteResult siteResult = future.get(); // get() metodu CompletableFuture'ı tamamlanana kadar bloklar
            if (!ObjectUtils.isEmpty(siteResult) && siteResult.getStatus() == QueryStatus.CLAIMED) {
                results.add(siteResult);
            }
        }
        return results;
    }


    private SiteResult checkSite(String username, String siteName, JsonNode siteInfo) {
        String urlMain = siteInfo.has("urlMain") ? siteInfo.get("urlMain").asText().trim() : "";
        String regexCheck = siteInfo.has("regexCheck") ? siteInfo.get("regexCheck").asText() : null;

        if (regexCheck != null && !Pattern.matches(regexCheck, username)) {
            return new SiteResult(siteName, urlMain, "", QueryStatus.ILLEGAL, "" );
        }

        String url = interpolateString(siteInfo.get("url").asText(), username.replace(" ", "%20"));
        String urlProbe = siteInfo.has("urlProbe") ? interpolateString(siteInfo.get("urlProbe").asText(), username) : url;

        String requestMethod = siteInfo.has("request_method") ? siteInfo.get("request_method").asText() : "GET";
        JsonNode requestPayloadNode = siteInfo.has("request_payload") ? siteInfo.get("request_payload") : null;
        String requestPayload = (requestPayloadNode != null) ? interpolateString(requestPayloadNode.toString(), username) : null;

        boolean allowRedirects = !"response_url".equals(siteInfo.get("errorType").asText());

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(urlProbe))
                .timeout(Duration.ofSeconds(timeout))
                .header("User-Agent", USER_AGENT);

        if (siteInfo.has("headers")) {
            JsonNode headersNode = siteInfo.get("headers");
            if (headersNode.isObject()) {
                headersNode.fields().forEachRemaining(header ->
                        requestBuilder.header(header.getKey(), header.getValue().asText())
                );
            }
        }

        if ("HEAD".equalsIgnoreCase(requestMethod)) {
            requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        } else if ("POST".equalsIgnoreCase(requestMethod)) {
            requestBuilder.POST(requestPayload != null ? HttpRequest.BodyPublishers.ofString(requestPayload) : HttpRequest.BodyPublishers.noBody());
        } else if ("PUT".equalsIgnoreCase(requestMethod)) {
            requestBuilder.PUT(requestPayload != null ? HttpRequest.BodyPublishers.ofString(requestPayload) : HttpRequest.BodyPublishers.noBody());
        } else {
            requestBuilder.GET(); // Default to GET
        }

        long startTime = System.nanoTime();
        try {
            // httpClient.sendAsync() kullanın
            HttpResponse<String> response = httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .join(); // Veya .get() ile handle edin, join() exception fırlatır
            long endTime = System.nanoTime();
            double responseTime = (endTime - startTime) / 1_000_000_000.0; // Convert to seconds

            String responseBody = response.body();
            int statusCode = response.statusCode();

            QueryStatus status = determineStatus(siteInfo, responseBody, statusCode);
            return !ObjectUtils.isEmpty(status) ? new SiteResult(siteName, urlMain, url, status, String.valueOf(statusCode)) : null;

        } catch (Exception e) {
            long endTime = System.nanoTime();
            double responseTime = (endTime - startTime) / 1_000_000_000.0;
            return new SiteResult(siteName, urlMain, url, QueryStatus.UNKNOWN, "");
        }
    }

    private QueryStatus determineStatus(JsonNode siteInfo, String responseBody, int statusCode) {
        String errorType = siteInfo.get("errorType").asText();

        // Check for WAF hits first
        for (String wafMsg : WAF_HIT_MESSAGES) {
            if (responseBody.contains(wafMsg)) {
                return null;
            }
        }

        switch (errorType) {
            case "message":
                JsonNode errorMsgNode = siteInfo.get("errorMsg");
                if (errorMsgNode != null) {
                    if (errorMsgNode.isTextual()) {
                        return responseBody.contains(errorMsgNode.asText()) ? QueryStatus.AVAILABLE : QueryStatus.CLAIMED;
                    } else if (errorMsgNode.isArray()) {
                        for (JsonNode errorMsg : errorMsgNode) {
                            if (responseBody.contains(errorMsg.asText())) {
                                return QueryStatus.AVAILABLE;
                            }
                        }
                        return QueryStatus.CLAIMED;
                    }
                }
                return null;

            case "status_code":
                JsonNode errorCodeNode = siteInfo.get("errorCode");
                Set<Integer> errorCodes = new HashSet<>();

                if (errorCodeNode != null) {
                    if (errorCodeNode.isInt()) {
                        errorCodes.add(errorCodeNode.asInt());
                    } else if (errorCodeNode.isArray()) {
                        for (JsonNode code : errorCodeNode) {
                            errorCodes.add(code.asInt());
                        }
                    }
                }

                if (errorCodes.contains(statusCode) || statusCode >= 300 || statusCode < 200) {
                    return null;
                } else {
                    return QueryStatus.CLAIMED;
                }

            case "response_url":
                // Since we disabled redirects, a 2xx status means claimed
                if (statusCode >= 200 && statusCode < 300) {
                    return QueryStatus.CLAIMED;
                }

            default:
                return null;
        }
    }

}
