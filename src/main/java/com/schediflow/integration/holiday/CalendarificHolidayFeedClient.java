package com.schediflow.integration.holiday;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.exception.BadGatewayException;
import com.schediflow.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calendarific v2 holidays API — free tier (TD-06). Uses {@code type=national} for public holidays.
 * Registered as {@link HolidayFeedClient} from {@link com.schediflow.config.CalendarificClientConfig}.
 */
public class CalendarificHolidayFeedClient implements HolidayFeedClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public CalendarificHolidayFeedClient(
            RestClient calendarificRestClient,
            ObjectMapper objectMapper,
            @Value("${app.calendarific.api-key:}") String apiKey) {
        this.restClient = calendarificRestClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public List<HolidayFeedItem> fetchPublicHolidays(String country, int year, String region) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("Calendarific API key is not configured. Set CALENDARIFIC_API_KEY.");
        }

        String body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> holidaysUri(uriBuilder, country, year, region))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            throw new BadGatewayException(
                    "The holiday provider is temporarily unavailable. Please try again later.");
        }

        if (body == null || body.isBlank()) {
            throw new BadGatewayException(
                    "The holiday provider is temporarily unavailable. Please try again later.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new BadGatewayException(
                    "The holiday provider returned an unexpected response. Please try again later.");
        }

        int metaCode = root.path("meta").path("code").asInt(-1);
        if (metaCode != 200) {
            throw new BadGatewayException(
                    "Unable to fetch public holidays from the provider. Please try again later.");
        }

        JsonNode holidaysNode = root.path("response").path("holidays");
        if (!holidaysNode.isArray()) {
            return List.of();
        }

        List<HolidayFeedItem> raw = new ArrayList<>();
        for (JsonNode h : holidaysNode) {
            String name = h.path("name").asText(null);
            String iso = h.path("date").path("iso").asText(null);
            if (name == null || name.isBlank() || iso == null || iso.isBlank()) {
                continue;
            }
            try {
                raw.add(new HolidayFeedItem(name.trim(), parseIsoDate(iso)));
            } catch (DateTimeParseException ignored) {
                // skip malformed rows
            }
        }

        return mergeByDate(raw);
    }

    private URI holidaysUri(UriBuilder uriBuilder, String country, int year, String region) {
        UriBuilder b = uriBuilder.path("/holidays")
                .queryParam("api_key", apiKey)
                .queryParam("country", country)
                .queryParam("year", year)
                .queryParam("type", "national");
        if (region != null && !region.isBlank()) {
            b.queryParam("location", region.trim());
        }
        return b.build();
    }

    /**
     * Parses {@code date.iso} from Calendarific, which may be a plain date ({@code "2026-01-01"})
     * or a full datetime ({@code "2026-01-01T00:00:00+05:30"}). Falls back to extracting the
     * date portion of an {@link OffsetDateTime} when plain {@link LocalDate#parse} fails.
     */
    private static LocalDate parseIsoDate(String iso) {
        try {
            return LocalDate.parse(iso);
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.parse(iso).toLocalDate();
        }
    }

    /**
     * Enforces at most one row per calendar date (matches DB unique index); merges names if the feed
     * returns multiple national holidays on the same day.
     */
    private static final int MAX_NAME_LENGTH = 100;

    static List<HolidayFeedItem> mergeByDate(List<HolidayFeedItem> items) {
        Map<LocalDate, String> byDate = new LinkedHashMap<>();
        for (HolidayFeedItem item : items) {
            byDate.merge(item.date(), item.name(), (a, b) -> a.equals(b) ? a : a + " / " + b);
        }
        List<HolidayFeedItem> out = new ArrayList<>(byDate.size());
        for (Map.Entry<LocalDate, String> e : byDate.entrySet()) {
            String name = e.getValue();
            if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
                name = name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH));
            }
            out.add(new HolidayFeedItem(name, e.getKey()));
        }
        return out;
    }
}
