package com.schediflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.integration.holiday.CalendarificHolidayFeedClient;
import com.schediflow.integration.holiday.HolidayFeedClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

@Configuration
public class CalendarificClientConfig {

    private static final Logger log = LoggerFactory.getLogger(CalendarificClientConfig.class);

    @Value("${app.calendarific.api-key:}")
    private String apiKey;

    @PostConstruct
    void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("CALENDARIFIC_API_KEY is not configured. POST /api/v1/holidays/import will return 400 until it is set.");
        }
    }

    @Bean
    public RestClient calendarificRestClient(
            @Value("${app.calendarific.base-url:https://calendarific.com/api/v2}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(new ApiKeyRedactingInterceptor())
                .build();
    }

    @Bean
    public HolidayFeedClient holidayFeedClient(
            RestClient calendarificRestClient,
            ObjectMapper objectMapper,
            @Value("${app.calendarific.api-key:}") String feedApiKey) {
        return new CalendarificHolidayFeedClient(calendarificRestClient, objectMapper, feedApiKey);
    }

    /**
     * Strips {@code api_key=…} from the URI before it appears in any log output,
     * so the Calendarific secret is never written to access logs or debug traces.
     */
    static class ApiKeyRedactingInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                ClientHttpRequestExecution execution) throws IOException {
            return execution.execute(new RedactedRequest(request), body);
        }

        private static class RedactedRequest extends org.springframework.http.client.support.HttpRequestWrapper {
            RedactedRequest(HttpRequest request) {
                super(request);
            }

            @Override
            public URI getURI() {
                String raw = super.getURI().toString();
                String redacted = raw.replaceAll("(api_key=)[^&]*", "$1***");
                return URI.create(redacted);
            }
        }
    }
}
