package com.michal.openai.gpt.service;

import com.michal.openai.slack.SlackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SlackGptCoordinator {

    private final SlackService slackService;
    private final GptService gptService;
    private final String slackChannelId;

    public SlackGptCoordinator(SlackService slackService, GptService gptService) {
        this.slackService = slackService;
        this.gptService = gptService;
        this.slackChannelId =
                System.getenv().getOrDefault("SLACK_CHANNEL_ID", "C08RLDBCRB9");

    }

    @Async("defaultExecutor")
    public void processMention(String requestBody) {
        try {
            var slackRequest = slackService.parseSlackRequest(requestBody);
            var text = slackRequest.event().text();
            var textCleaned = cleanMention(text);
            var slackUserId = slackRequest.event().user();
            var type = slackRequest.event().type();
            log.debug("Parsed Slack message from {}: {}, type: {}", slackUserId, textCleaned , type);
            String response = gptService.getAnswerWithSlack(textCleaned, slackUserId);
            slackService.sendMessageToSlack(response, slackChannelId);
        } catch (Exception e) {
            log.error("Error processing Slack mention asynchronously", e);
        }
    }

    private String cleanMention(String query)
    {
        return query.replaceAll("<@.*?>", "").trim();
    }
}