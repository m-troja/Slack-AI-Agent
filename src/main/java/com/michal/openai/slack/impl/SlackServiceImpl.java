package com.michal.openai.slack.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.michal.openai.persistence.SlackRepo;
import com.michal.openai.slack.SlackService;
import com.michal.openai.slack.entity.SlackRequest;
import com.michal.openai.slack.entity.SlackUser;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.users.UsersListRequest;
import com.slack.api.methods.response.users.UsersListResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class SlackServiceImpl implements SlackService {

    public static final String SUCCESSFULL_REGISTRATION_MESSAGE = "User is registered!";

    private final MethodsClient slackBotClient;
    private final ObjectMapper objectMapper;
    private final SlackRepo slackRepo;
    private final String slackChannelId;

    public SlackServiceImpl(
            @Qualifier("slackBotClient") MethodsClient slackBotClient,
            ObjectMapper objectMapper,
            SlackRepo slackRepo
    ) {
        this.slackBotClient = slackBotClient;
        this.objectMapper = objectMapper;
        this.slackRepo = slackRepo;

        this.slackChannelId =
                System.getenv().getOrDefault("SLACK_CHANNEL_ID", "C08RLDBCRB9");

        log.info("SLACK_CHANNEL_ID={}", slackChannelId);
    }

    @Override
    public SlackRequest parseSlackRequest(String requestBody) {

        SlackRequest slackRequest;

        try {
            slackRequest = objectMapper.readValue(requestBody, SlackRequest.class);
        }
        catch (JsonProcessingException e) {
            log.error("Cannot parse Slack request", e);
            return null;
        }

        checkSlackUserInDb(slackRequest.event().user());

        return slackRequest;
    }

    private void checkSlackUserInDb(String slackUserId) {

        if (slackRepo.findBySlackUserId(slackUserId) == null) {
            extractUsersIntoDatabase();
        }
    }

    private void extractUsersIntoDatabase() {

        try {

            log.debug("Fetching Slack users");

            UsersListResponse response =
                    slackBotClient.usersList(
                            UsersListRequest.builder().build()
                    );

            response.getMembers().forEach(user -> {

                if (getSlackUserBySlackId(user.getId()) == null) {

                    registerUser(
                            new SlackUser(user.getId(), user.getRealName())
                    );
                }
            });

        }
        catch (Exception e) {
            log.error("Error importing Slack users", e);
        }
    }

    public void sendMessageToSlack(String message, String channelId) {

        String text = message != null ? message : "[No response]";

        ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(channelId)
                .text(text)
                .build();

        try {

            log.info("Sending Slack message: {}", text);

            slackBotClient.chatPostMessage(request);

        }
        catch (IOException | SlackApiException e) {

            log.error("Slack message failed", e);

        }
    }

    @Override
    public void triggerGetUsers() {
        extractUsersIntoDatabase();
    }

    public SlackUser getSlackUserBySlackId(String slackId) {
        return slackRepo.findBySlackUserId(slackId);
    }

    public List<SlackUser> getAllSlackUsers() {

        List<SlackUser> users =
                slackRepo.findAllByOrderBySlackUserId();

        log.debug("All slack users: {}", users);

        return users;
    }

    @Override
    public String registerUser(SlackUser user) {

        slackRepo.save(user);

        log.info("Registered user: {}", user);

        return SUCCESSFULL_REGISTRATION_MESSAGE;
    }
}
