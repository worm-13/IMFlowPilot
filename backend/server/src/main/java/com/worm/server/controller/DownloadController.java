package com.worm.server.controller;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@RestController
public class DownloadController {

    private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);

    private final String agentBaseUrl;
    private HttpClient httpClient;

    public DownloadController(@Value("${agent.base-url:http://localhost:8000}") String agentBaseUrl) {
        this.agentBaseUrl = agentBaseUrl;
    }

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @GetMapping("/api/download/{fileName}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String fileName) {
        try {
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            String agentUrl = agentBaseUrl + "/download/" + encodedFileName;

            logger.info("Proxying download: {} → {}", fileName, agentUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentUrl))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + fileName);
            }

            String contentType = response.headers().firstValue("Content-Type")
                    .orElse("application/octet-stream");

            long contentLength = response.headers().firstValueAsLong("Content-Length")
                    .orElse(-1);

            InputStreamResource resource = new InputStreamResource(response.body());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            if (contentLength > 0) {
                headers.setContentLength(contentLength);
            }

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Download proxy failed for {}: {}", fileName, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Download failed");
        }
    }
}
