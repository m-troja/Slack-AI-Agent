package com.michal.openai.gpt.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.michal.openai.exception.GptCommunicationException;
import com.michal.openai.gpt.entity.GptMessage;
import com.michal.openai.gpt.entity.GptRequest;
import com.michal.openai.gpt.entity.GptResponse;
import com.michal.openai.gpt.entity.cnv.GptMessageCnv;
import com.michal.openai.gpt.entity.dto.RequestDto;
import com.michal.openai.gpt.entity.dto.ResponseDto;
import com.michal.openai.gpt.service.GptService;
import com.michal.openai.gpt.tool.factory.ToolInvoker;
import com.michal.openai.gpt.tool.registry.GptToolRegistry;
import com.michal.openai.log.JsonSaver;
import com.michal.openai.persistence.RequestDtoRepo;
import com.michal.openai.persistence.ResponseDtoRepo;
import com.michal.openai.persistence.SlackRepo;
import com.michal.openai.slack.entity.SlackUser;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.michal.openai.gpt.tool.executor.ToolExecutor;

@Data
@Slf4j
@Service
public class GptServiceImpl implements GptService {

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_TOOL = "tool";

    @Value("${CHAT_MODEL}")
    private String model;
    @Value("${gpt.chat.temperature}")
    private Double temperature;
    @Value("${CHAT_MAX_TOKENS}")
    private Integer maxTokens;
    @Value("${gpt.chat.sendrequest.retryattempts}")
    private Integer retryAttempts;
    @Value("${gpt.chat.sendrequest.waitforretry.seconds}")
    private Integer waitSeconds;
    @Value("${gpt.chat.qty.context.messages}")
    private Integer qtyContextMessagesInRequestOrResponse;
    private Integer totalQtyMessagesInContext; // Context + System/Initial Msg + query
    @Value("${GPT_CHAT_SYSTEM_INITIAL_MESSAGE}")
    private String systemInitialMessage;
    @Value("${CHAT_JSON_DIR}")
    private String jsonDir;
    @Qualifier("gptRestClient")
    private final RestClient restClient;
    private final RequestDtoRepo requestDtoRepo;
    private final ResponseDtoRepo responseDtoRepo;
    private final SlackRepo slackRepo;
    private final ObjectMapper objectMapper;
    private final GptToolRegistry gptToolRegistry;
    private final ToolInvoker toolInvoker;

    // Constructor needed because of @Qualifier("gptRestClient")

    public GptServiceImpl(@Qualifier("gptRestClient") RestClient restClient ,RequestDtoRepo requestDtoRepo, ResponseDtoRepo responseDtoRepo, SlackRepo slackRepo, ObjectMapper objectMapper,
                          GptToolRegistry gptToolRegistry, ToolInvoker toolInvoker) {
        this.restClient = restClient;
        this.requestDtoRepo = requestDtoRepo;
        this.responseDtoRepo = responseDtoRepo;
        this.slackRepo = slackRepo;
        this.objectMapper = objectMapper;
        this.gptToolRegistry = gptToolRegistry;
        this.toolInvoker = toolInvoker;
    }

    @PostConstruct
    public void init() {
        // Context of 2 last messages + 2 last responses + system message + last query
        totalQtyMessagesInContext = qtyContextMessagesInRequestOrResponse * 2 + 2;
    }
	/*
	 * Called by SlackAPI controller.
	 * Builds object of GPTRequest and forwards it to send to GPT.
	 */
    @Override
    public String getAnswerWithSlack(String query, String slackUserId) {

        SlackUser user = slackRepo.findBySlackUserId(slackUserId);

        if (user == null) {
            log.warn("Slack user not found: {}", slackUserId);
        }

        GptRequest request = new GptRequest();

        request.setModel(model);
        request.setTemperature(temperature);
        request.setMaxOutputTokens(maxTokens);

        request.setTools(gptToolRegistry.allAllowedGptTools());

        request.setMessages(
                buildLastMessagesContextOfUserSlackId(slackUserId, query)
        );

        log.debug("Built GPT request with {} context messages",
                request.getMessages().size());

        return runConversation(request, user);
    }

    @Override
    public void clearDatabase() {
        try {
            log.debug("Trying to clear database...");
            responseDtoRepo.deleteAll();
            requestDtoRepo.deleteAll();
            log.debug("Database cleared successfully");
        } catch (Exception e) {
            log.error("Error clearing database: ", e);
            throw new RuntimeException(e);
        }
    }

    private String runConversation(GptRequest request, SlackUser user) {

        while (true) {

            GptResponse response = sendRequestWithRetry(request);

            if (response.getChoices() == null || response.getChoices().isEmpty()) {
                log.error("Empty GPT response");
                throw new GptCommunicationException("Empty GPT response");
            }

            GptMessage assistantMessage =
                    response.getChoices().getFirst().getMessage();

            saveDto(request, response, user);

            if (assistantMessage.getToolCalls() == null ||
                    assistantMessage.getToolCalls().isEmpty()) {

                log.debug("GPT returned final answer");
                return assistantMessage.getContent();
            }

            request.getMessages().add(assistantMessage);

            assistantMessage.getToolCalls().forEach(toolCall -> {

                String functionName = toolCall.getFunctionCall().getName();

                log.debug("GPT requested tool: {}", functionName);

                ToolExecutor<?> executor = gptToolRegistry.get(functionName);
                Object result;

                try {

                    log.debug("functionCall: {}", toolCall.getFunctionCall());
                    log.debug("arguments: {}", toolCall.getFunctionCall().getArguments());

                     result = toolInvoker.invoke(
                            executor,
                            toolCall.getFunctionCall().getArguments()
                    );
                     log.debug("result: {}", result);

                } catch (Exception e) {

                    log.error("Tool execution failed: {}", functionName, e);

                    result = "Tool execution error";
                }

                GptMessage toolMessage = new GptMessage();

                toolMessage.setRole(ROLE_TOOL);
                toolMessage.setToolCallId(toolCall.getId());
                try {
                    toolMessage.setContent(objectMapper.writeValueAsString(result));
                } catch (JsonProcessingException e) {
                    toolMessage.setContent(String.valueOf(result));
                    throw new RuntimeException(e);
                }

                request.getMessages().add(toolMessage);

                log.debug("Tool result added to conversation");
            });
        }
    }


    private GptResponse sendRequestWithRetry(GptRequest request) {

        int attempt = 0;

        while (true) {
            try {
                log.debug("Sending GPT request attempt {}", attempt + 1);
                logPrettyJson(request);
                String responseString = restClient
                        .post()
                        .body(request)
                        .retrieve()
                        .body(String.class);

                GptResponse response = objectMapper.readValue(responseString, GptResponse.class);

                saveGptRequest(request);
                saveGptResponse(response);

                return response;

            } catch (JsonProcessingException e) {

                throw new GptCommunicationException("GPT response parsing error");

            } catch (Exception e) {

                attempt++;

                log.error("GPT request failed attempt {}", attempt, e);

                if (attempt >= retryAttempts) {
                    throw new GptCommunicationException("GPT request failed");
                }

                sleep(waitSeconds);
            }
        }
    }
    private void saveDto(GptRequest request, GptResponse response, SlackUser slackUserRequestAuthor) {

        if (slackUserRequestAuthor == null) {
            log.warn("Skipping DTO save because Slack user is null");
            return;
        }

        log.debug("Saving DTOs... ");

        var messageCnv = new GptMessageCnv();
        var requestMessage = request.getMessages().getLast();
        var responseMessage = response.getChoices().getFirst().getMessage();

        RequestDto requestDto;
        ResponseDto responseDto;

        if (requestMessage.getToolCallId() == null
                && requestMessage.getToolCalls() == null
                && !requestMessage.getRole().equals("tool")) {

            requestDto = messageCnv.requestEntityToDto(requestMessage, slackUserRequestAuthor);
            requestDtoRepo.save(requestDto);
            saveMessageJson(requestDto);

        } else {
            log.debug("Tool call found in requestMessage, skipping RequestDTO creation");
        }

        if (responseMessage.getToolCalls() == null) {

            responseDto = messageCnv.responseEntityToDto(responseMessage, slackUserRequestAuthor);
            responseDtoRepo.save(responseDto);
            saveMessageJson(responseDto);

        } else {
            log.debug("Tool call found in responseMessage, skipping ResponseDTO creation");
        }

        saveGptResponse(response);
        saveGptRequest(request);
    }

    private void sleep(int seconds) {
        try {
            log.debug("Sleeping for {}s", seconds);
            TimeUnit.SECONDS.sleep(seconds);

        } catch (InterruptedException e) {
            log.error("Sleeping interrupted ", e);
            throw new RuntimeException(e);
        }
    }

	private void saveGptRequest(GptRequest gptRequest)  {
        log.debug("Request to GPT:");
//        logPrettyJson(gptRequest);
        JsonSaver jsonSaver = new JsonSaver(jsonDir);
        jsonSaver.saveRequest(gptRequest);
	}

    private void saveGptResponse(GptResponse gptResponse) {
        JsonSaver jsonSaver = new JsonSaver(jsonDir);
        jsonSaver.saveResponse(gptResponse);
	}

    private void saveMessageJson(Object obj) {
        JsonSaver jsonSaver = new JsonSaver(jsonDir);
        jsonSaver.saveMessage(obj);
    }

	/* Get last messages of user to build context for GPT */

    private List<GptMessage> getLastRequestsOfUser(SlackUser user) {
		log.debug("Calling getLastRequestsOfUser with user={}, max allowed context= {}", user.getSlackUserId(), qtyContextMessagesInRequestOrResponse);
        PageRequest limit = PageRequest.of(0, qtyContextMessagesInRequestOrResponse);
        log.debug("Limit of requests: {}", limit);

        List<RequestDto> dtos = requestDtoRepo.findByUserSlackIdOrderByTimestampDesc(user.getSlackUserId(), limit);
		if (dtos.isEmpty()) {
            log.debug("Not found any request");
        }

        List<GptMessage> messages = new ArrayList<>();
		for (RequestDto dto : dtos)
		{
            var gptMessage = new GptMessage(dto.getRole(), dto.getContent());
			log.debug("Found request: {}, authorSlackID: {}", dto.getContent(), user.getSlackUserId() );
            messages.add(gptMessage);
		}
		return messages;
	}

	/* Get last responses to user to build context for GPT */

    private List<GptMessage> getLastResponsesToUser(SlackUser user) {
        log.debug("Calling getLastResponsesToUser with user={}, max allowed context= {}", user.getSlackUserId(), qtyContextMessagesInRequestOrResponse);
        PageRequest limit = PageRequest.of(0, qtyContextMessagesInRequestOrResponse);
        log.debug("Limit of responses: {}", limit);

        List<ResponseDto> dtos = responseDtoRepo.findByUserSlackIdOrderByTimestampDesc(user.getSlackUserId(), limit);
        if (dtos.isEmpty()) {
            log.debug("Not found any responses");
        }

        List<GptMessage> messages = new ArrayList<>();
        for (ResponseDto dto : dtos)
        {
            var gptMessage = new GptMessage(dto.getRole(), dto.getContent());
            log.debug("Found response: {}, authorSlackID: {}", dto.getContent(), user.getSlackUserId() );
            messages.add(gptMessage);
        }
        return messages;
	}

    private List<GptMessage> buildLastMessagesContextOfUserSlackId(String userSlackId, String query) {

        SlackUser user = getSlackUserBySlackId(userSlackId);

        List<GptMessage> messages = new ArrayList<>();

        if (user != null) {

            List<GptMessage> requests = getLastRequestsOfUser(user);
            List<GptMessage> responses = getLastResponsesToUser(user);

            int contextSize = Math.min(requests.size(), responses.size());

            for (int i = contextSize - 1; i >= 0; i--) {
                messages.add(responses.get(i));
                messages.add(requests.get(i));
            }
        }

        messages.add(getInitialSystemMessage(userSlackId));
        messages.add(new GptMessage(ROLE_USER, query));

        while (messages.size() > totalQtyMessagesInContext) {
            messages.removeFirst();
        }

        return messages;
    }
	private GptMessage getInitialSystemMessage(String userSlackId) {
		String content = String.format(
			    "%sYou received message from %s. Type <@%s> to mention them.",
			    systemInitialMessage,
			    userSlackId,
			    userSlackId
			);
			return new GptMessage(ROLE_SYSTEM, content);
	}

    private void logPrettyJson(Object obj) {
        try {
            String prettyJson = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(obj);
            log.debug(prettyJson);
        } catch (Exception e) {
            log.error("Error creating pretty JSON: ", e);
        }
    }

    public SlackUser getSlackUserBySlackId(String slackId) {
        return slackRepo.findBySlackUserId(slackId);
    }
}