package ru.steam.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.bots.DefaultBotOptions;

import java.util.concurrent.TimeUnit;

@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "proxy")
public class ProxyConfig {
    private boolean enabled;
    private String host;
    private int port;
    private String username;
    private String password;

    @Bean
    public CloseableHttpClient httpClient() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(20);

        var clientBuilder = HttpClients.custom()
                .setConnectionManager(connectionManager);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(30, TimeUnit.SECONDS))
                .setResponseTimeout(Timeout.of(60, TimeUnit.SECONDS))
                .setConnectTimeout(Timeout.of(10, TimeUnit.SECONDS))
                .build();
        clientBuilder.setDefaultRequestConfig(requestConfig);

        if (enabled) {
            log.info("Proxy ENABLED: {}:{}", host, port);
            HttpHost proxy = new HttpHost(host, port);
            clientBuilder.setProxy(proxy);

            if (username != null && !username.isEmpty() &&
                    password != null && !password.isEmpty()) {
                log.info("Using proxy authentication for user: {}", username);
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        new AuthScope(host, port),
                        new UsernamePasswordCredentials(username, password.toCharArray())
                );
                clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            } else {
                log.info("Proxy without authentication");
            }
        } else {
            log.info("Proxy DISABLED");
        }

        CloseableHttpClient httpClient = clientBuilder.build();
        log.info("🚀 HttpClient configured successfully");
        return httpClient;
    }

    @Bean
    public RestTemplate restTemplate(CloseableHttpClient httpClient) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        RestTemplate restTemplate = new RestTemplate(factory);

        restTemplate.getInterceptors().add((request, body, execution) -> {
            HttpHeaders headers = request.getHeaders();
            headers.set(HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set(HttpHeaders.ACCEPT, "application/json");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");

            log.debug("Request to: {} with proxy: {}", request.getURI(), enabled ? host + ":" + port : "direct");
            return execution.execute(request, body);
        });

        log.info("RestTemplate configured with custom HttpClient" + (enabled ? " and PROXY" : ""));
        return restTemplate;
    }

    @Bean
    public DefaultBotOptions telegramBotOptions() {
        DefaultBotOptions options = new DefaultBotOptions();
        options.setProxyHost("156.236.107.125");
        options.setProxyPort(16356); // ← SOCKS5 порт
        options.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);
        return options;
    }
}