package com.michal.openai.slack;

import com.michal.openai.slack.entity.SlackRequest;
import com.michal.openai.slack.entity.SlackUser;

import java.util.List;

public interface SlackService {

    SlackRequest parseSlackRequest(String requestBody);

    String registerUser(SlackUser user);

    SlackUser getSlackUserBySlackId(String slackid);

    List<SlackUser> getAllSlackUsers();

    void sendMessageToSlack(String message, String channelId);
    void triggerGetUsers();
}
