package com.michal.openai.config;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BeansConfiguration {

	@Value("${SLACK_BOT_OAUTH_TOKEN}")
	private String slackSecurityTokenBot ;

    @Bean("slackBotClient")
    public MethodsClient slackMethodClientBot() {
        return Slack.getInstance().methods(slackSecurityTokenBot);
    }

    private final ApplicationContext context;

    public BeansConfiguration(ApplicationContext context) {
        this.context = context;
    }

}