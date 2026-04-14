package com.michal.openai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.HashMap;
import java.util.Map;
@EnableAsync(proxyTargetClass = true)
@SpringBootApplication
@PropertySources({
	@PropertySource("classpath:application.properties")
	})
public class ChatGptIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ChatGptIntegrationApplication.class);
        Map<String, Object> props = new HashMap<>();
        String dbName = System.getenv().getOrDefault("CHAT_DB_NAME", "chatgpt-integration");
        String dbHost = System.getenv().getOrDefault("CHAT_DB_HOST", "localhost");
        String dbPort = System.getenv().getOrDefault("CHAT_DB_PORT", "5432");
        props.put("server.address", System.getenv().getOrDefault("CHAT_SERVER_ADDRESS", "0.0.0.0"));
        props.put("server.port", System.getenv().getOrDefault("CHAT_SERVER_PORT", "6969"));
        props.put("spring.datasource.url",  String.format("jdbc:postgresql://%s:%s/%s", dbHost, dbPort, dbName));
        props.put("spring.datasource.username", System.getenv().getOrDefault("CHAT_DB_USERNAME", "chatgpt"));
        props.put("spring.datasource.password", System.getenv().getOrDefault("CHAT_DB_PASSWORD", "chatgptPW"));
        app.setDefaultProperties(props);
        app.run(args);
    }
}
