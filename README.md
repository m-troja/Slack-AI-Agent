# Slack AI Agent

**Slack AI Agent** is a Spring Boot-based web application that integrates OpenAI's ChatGPT with **Slack**, **Jira**, **GitHub** and [TaskStorm](https://github.com/m-troja/TaskStorm) APIs. Designed as a modular and extensible backend, this application facilitates AI-powered task automation, conversational interfaces, and project management workflows.

---

## Features

- Slack: integration with Slack workspace. Agent answer questions over OpenAI API or completes task with function calls.
- TaskStorm: retrieve issue info per user with custom filters like Status or Priority over Slack channel. Create issues by AI query, assign tasks and see real-time notifications after actions has been performed in the issue.
- Jira integration: retrieve issue info, post a new issue according to user requirements written in Slack channel.
- Requests and answers are being assigned to specific Slack user and stored in the database.


---

## Technology Stack

| Component           |  Details                               |
| --------------------|----------------------------------------|
| **Language**        | Java 21                                |
| **Framework**       | Spring Boot 3.5.7                      |
| **Build Tool**      | Maven                                  |
| **Logging**         | SLF4J with Lombok                      |
|**Rest API integration** | [TaskStorm](https://github.com/m-troja/TaskStorm), Jira, Github, OpenAI |


## Requirements

- public static IP address
- OpenAI account and API token
- Jira account and API token
- Slack account and API token, SlackBot installed into workspace

## Config

Requires env vars config in ```.env.example```.


## Logging

App saves incoming and outgoing JSON files. Env variables needed to configure file path:

CHAT_LOG_DIR=C:\\tmp\\log
CHAT_JSON_DIR=C:\\tmp\\JSON
CHAT_LOG_FILENAME=chatgpt
