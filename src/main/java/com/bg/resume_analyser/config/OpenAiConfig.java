package com.bg.resume_analyser.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration to disable OpenAI audio models that aren't used in this application.
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.openai.speech.enabled", havingValue = "false")
public class OpenAiConfig {
    // This class helps disable audio-related beans
}
