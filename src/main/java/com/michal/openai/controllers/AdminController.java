package com.michal.openai.controllers;


import com.michal.openai.functions.entity.GptTool;
import com.michal.openai.gpt.service.GptService;
import com.michal.openai.gpt.tool.registry.GptToolRegistry;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RequestMapping("/api/v1/admin")
@RestController
@RequiredArgsConstructor
public class AdminController {

	private final GptService gptService;
    private final GptToolRegistry gptToolRegistry;


    /* Endpoint to trigger truncating all tables in DB */
	
	@DeleteMapping(value = "clear-database")
	@Transactional
	public String clearDatabase()
	{
        log.info("GET /api/v1/admin/clear-database");
        gptService.clearDatabase();

		return "Cleared database";
	}

    @GetMapping(value = "allowed-tools")
    public List<GptTool> getAllowedTools() {
            log.debug("getAllowedTools:");

            var tools = gptToolRegistry.allAllowedGptTools();

            log.debug("{}", tools);

            return tools;
    }

    @GetMapping(value = "all-tools")
    public List<GptTool> getAllTools() {
        log.debug("getAllTools:");

        var tools = gptToolRegistry.allGptTools();

        log.debug("{}", tools);

        return tools;
    }

}