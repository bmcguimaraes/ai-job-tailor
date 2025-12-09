package com.bg.resume_analyser.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

@Service
public class ImproveService {

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> generateImprovements(String resumeText, String jobText, List<String> missingKeywords, int currentScore) {
        Map<String, Object> result = new HashMap<>();
        Set<String> allKeywords = new LinkedHashSet<>();
        List<String> bulletSuggestions = new ArrayList<>();
        List<String> summarySuggestions = new ArrayList<>();
        boolean fallback = false;
        try {
            if (openaiApiKey == null || openaiApiKey.isBlank()) {
                result.put("improvements", new ArrayList<>());
                result.put("selectedKeywords", new ArrayList<>());
                result.put("bulletPointSuggestions", new ArrayList<>());
                result.put("personalSummarySuggestions", new ArrayList<>());
                result.put("projectedScore", currentScore);
                return result;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openaiApiKey);

            String prompt = "You are an expert resume improvement advisor. Given the resume and job description below, output ONLY the actionable content needed to tailor the resume for a perfect match.\n" +
                "Instructions: Return a JSON object with these fields: keywordsToAdd (array of keywords/skills to add for Skills & Abilities section), skillsAndAbilitiesToAdd (array of skills/abilities to add or emphasize for Skills & Abilities section), bulletPointSuggestions (array of new or rewritten bullet points for Experience section), personalSummarySuggestions (array of concise summary sentences to add or improve for Personal Summary section).\n" +
                "Do NOT include generic advice, explanations, or any text outside the JSON.\n" +
                "Use UK English spelling, grammar, and conventions.\n" +
                "Only include content that is relevant, concise, and improves the match score.\n" +
                "Do not repeat existing resume content unless it needs improvement.\n" +
                "MISSING KEYWORDS: " + String.join(", ", missingKeywords) + "\n" +
                "MISSING TECH STACK: " + (jobText.contains("Tech Stack:") ? jobText.substring(jobText.indexOf("Tech Stack:"), jobText.indexOf("\n", jobText.indexOf("Tech Stack:"))) : "") + "\n" +
                "MISSING MAIN FUNCTIONS: " + (jobText.contains("Main Functions:") ? jobText.substring(jobText.indexOf("Main Functions:"), jobText.indexOf("\n", jobText.indexOf("Main Functions:"))) : "") + "\n" +
                "Resume:\n" + resumeText + "\n" +
                "Job Description:\n" + jobText + "\n" +
                "Example output: {" +
                "\\\"keywordsToAdd\\\": [\\\"Spring Boot\\\", \\\"TypeScript\\\"], " +
                "\\\"skillsAndAbilitiesToAdd\\\": [\\\"Team leadership\\\", \\\"Agile development\\\"], " +
                "\\\"bulletPointSuggestions\\\": [\\\"Led a team of 5 engineers to deliver a cloud migration project.\\\", \\\"Implemented CI/CD pipelines using Jenkins and Docker.\\\"], " +
                "\\\"personalSummarySuggestions\\\": [\\\"Experienced software engineer with a focus on scalable backend systems.\\\", \\\"Proven track record in leading cross-functional teams.\\\"]}";

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", "gpt-4-turbo");
            payload.put("messages", java.util.List.of(
                Map.of("role", "system", "content", "You are a resume improvement advisor. Provide concrete, actionable suggestions. Return valid JSON only."),
                Map.of("role", "user", "content", prompt)
            ));
            payload.put("temperature", 0.5);
            payload.put("max_tokens", 800);

            // Set timeout for the LLM API call
            int timeoutMillis = 60000; // 1 minute
            org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(timeoutMillis);
            requestFactory.setReadTimeout(timeoutMillis);
            RestTemplate timedRestTemplate = new RestTemplate(requestFactory);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> resp;
            try {
                resp = timedRestTemplate.postForEntity("https://api.openai.com/v1/chat/completions", request, String.class);
            } catch (Exception timeoutEx) {
                result.put("improvements", new ArrayList<>());
                result.put("selectedKeywords", new ArrayList<>());
                result.put("bulletPointSuggestions", new ArrayList<>());
                result.put("personalSummarySuggestions", new ArrayList<>());
                result.put("projectedScore", currentScore);
                result.put("error", "[FAIL] LLM API call timed out after 1 minute");
                System.out.println("\u001B[1m[FAIL]\u001B[0m LLM API call timed out after 1 minute.");
                return result;
            }

            JsonNode respNode = objectMapper.readTree(resp.getBody());
            String assistantMsg = respNode.at("/choices/0/message/content").asText();

            int jsonStart = assistantMsg.indexOf('{');
            int jsonEnd = assistantMsg.lastIndexOf('}') + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = assistantMsg.substring(jsonStart, jsonEnd);
                JsonNode parsed = objectMapper.readTree(jsonStr);

                JsonNode keywordsArray = parsed.get("keywordsToAdd");
                if (keywordsArray != null && keywordsArray.isArray()) {
                    for (JsonNode keyword : keywordsArray) {
                        allKeywords.add(keyword.asText());
                    }
                }
                JsonNode skillsArray = parsed.get("skillsAndAbilitiesToAdd");
                if (skillsArray != null && skillsArray.isArray()) {
                    for (JsonNode skill : skillsArray) {
                        allKeywords.add(skill.asText());
                    }
                }
                JsonNode bulletsArray = parsed.get("bulletPointSuggestions");
                if (bulletsArray != null && bulletsArray.isArray()) {
                    for (JsonNode bullet : bulletsArray) {
                        bulletSuggestions.add(bullet.asText());
                    }
                }
                JsonNode summaryArray = parsed.get("personalSummarySuggestions");
                if (summaryArray != null && summaryArray.isArray()) {
                    for (JsonNode summary : summaryArray) {
                        summarySuggestions.add(summary.asText());
                    }
                }
                // Fallback if all actionable fields are empty
                if (allKeywords.isEmpty() && bulletSuggestions.isEmpty() && summarySuggestions.isEmpty()) {
                    fallback = true;
                }
            } else {
                fallback = true;
            }

            // Fallback: force LLM to analyze job post directly
            if (fallback) {
                String fallbackPrompt = "You are an expert resume improvement advisor. The previous analysis did not yield actionable suggestions. Analyze the job description below and generate actionable improvements for tailoring the resume. Return a JSON object with: keywordsToAdd, skillsAndAbilitiesToAdd, bulletPointSuggestions, personalSummarySuggestions.\nJob Description:\n" + jobText + "\n";
                Map<String, Object> fallbackPayload = new HashMap<>();
                fallbackPayload.put("model", "gpt-4-turbo");
                fallbackPayload.put("messages", java.util.List.of(
                    Map.of("role", "system", "content", "You are a resume improvement advisor. Provide actionable suggestions. Return valid JSON only."),
                    Map.of("role", "user", "content", fallbackPrompt)
                ));
                fallbackPayload.put("temperature", 0.5);
                fallbackPayload.put("max_tokens", 800);
                HttpEntity<Map<String, Object>> fallbackRequest = new HttpEntity<>(fallbackPayload, headers);
                ResponseEntity<String> fallbackResp = restTemplate.postForEntity("https://api.openai.com/v1/chat/completions", fallbackRequest, String.class);
                String fallbackMsg = objectMapper.readTree(fallbackResp.getBody()).at("/choices/0/message/content").asText();
                int fbJsonStart = fallbackMsg.indexOf('{');
                int fbJsonEnd = fallbackMsg.lastIndexOf('}') + 1;
                if (fbJsonStart >= 0 && fbJsonEnd > fbJsonStart) {
                    String fbJsonStr = fallbackMsg.substring(fbJsonStart, fbJsonEnd);
                    JsonNode fbParsed = objectMapper.readTree(fbJsonStr);
                    allKeywords.clear();
                    bulletSuggestions.clear();
                    summarySuggestions.clear();
                    JsonNode fbKeywordsArray = fbParsed.get("keywordsToAdd");
                    if (fbKeywordsArray != null && fbKeywordsArray.isArray()) {
                        for (JsonNode keyword : fbKeywordsArray) {
                            allKeywords.add(keyword.asText());
                        }
                    }
                    JsonNode fbSkillsArray = fbParsed.get("skillsAndAbilitiesToAdd");
                    if (fbSkillsArray != null && fbSkillsArray.isArray()) {
                        for (JsonNode skill : fbSkillsArray) {
                            allKeywords.add(skill.asText());
                        }
                    }
                    JsonNode fbBulletsArray = fbParsed.get("bulletPointSuggestions");
                    if (fbBulletsArray != null && fbBulletsArray.isArray()) {
                        for (JsonNode bullet : fbBulletsArray) {
                            bulletSuggestions.add(bullet.asText());
                        }
                    }
                    JsonNode fbSummaryArray = fbParsed.get("personalSummarySuggestions");
                    if (fbSummaryArray != null && fbSummaryArray.isArray()) {
                        for (JsonNode summary : fbSummaryArray) {
                            summarySuggestions.add(summary.asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            result.put("improvements", new ArrayList<>());
            result.put("selectedKeywords", new ArrayList<>());
            result.put("bulletPointSuggestions", new ArrayList<>());
            result.put("personalSummarySuggestions", new ArrayList<>());
            result.put("projectedScore", currentScore);
            result.put("error", e.getMessage());
            System.out.println("\u001B[1m[FAIL]\u001B[0m Resume improvement failed: " + e.getMessage());
            return result;
        }
        // Populate results and calculate score after try/catch
        result.put("improvements", new ArrayList<>()); // No detailed improvements, just actionable lists
        result.put("selectedKeywords", new ArrayList<>(allKeywords));
        result.put("bulletPointSuggestions", bulletSuggestions);
        result.put("personalSummarySuggestions", summarySuggestions);
        int projectedScore = Math.min(100, currentScore + allKeywords.size() + bulletSuggestions.size() + summarySuggestions.size());
        result.put("projectedScore", projectedScore);
        return result;
    }

    /**
     * Generate tailored resume text using actionable improvements and original resume.
     * This is a stub implementation. Replace with actual LLM or tailoring logic as needed.
     */
    public com.fasterxml.jackson.databind.JsonNode generateEditPlan(
            String originalText,
            String jobText,
            List<String> skillsAndAbilitiesToAdd,
            List<String> bulletPointSuggestions,
            List<String> personalSummarySuggestions) {
        
        System.out.println("[DEBUG] Entered generateEditPlan");

        String prompt = "You are an expert resume editor. Your goal is to make minimal, high-impact changes. Below is an original resume and a list of potential improvements. Your task is to decide which of these improvements are crucial and where they should be placed.\n\n" +
            "Instructions:\n" +
            "1. Review the 'Personal Summary Suggestions'. If a suggestion significantly improves the summary, integrate it by replacing a phrase or adding a sentence. Otherwise, ignore it.\n" +
            "2. Review the 'Skills to Add'. Add only the most critical keywords to the existing 'Skills & Abilities' section. Do not add more than 3-4 keywords.\n" +
            "3. Review the 'Experience Bullet Points'. Add or rewrite only the most vital bullet points to the 'Experience' section to match the job description. Do not add more than 1-2 new bullet points. It is preferred if you change any existing ones rather than creating new ones.\n" +
            "4. Your output must be ONLY a JSON object that specifies the exact text to add or replace. Do not rewrite the whole resume.\n\n" +
            "Original Resume:\n" + originalText + "\n\n" +
            "Potential Improvements:\n" +
            "- Personal Summary Suggestions: " + personalSummarySuggestions + "\n" +
            "- Skills to Add: " + skillsAndAbilitiesToAdd + "\n" +
            "- Experience Bullet Points: " + bulletPointSuggestions + "\n\n" +
            "Example JSON Output:\n" +
            "{\n" +
            "  \"personal_summary_edits\": [\n" +
            "    { \"type\": \"REPLACE\", \"original\": \"Software engineer with 3 years of backend development experience\", \"updated\": \"Backend software engineer with 3 years of experience in AI-driven systems\" },\n" +
            "    { \"type\": \"ADD\", \"after_sentence\": \"Certified in Java (OCA) and Azure Fundamentals (AZ-900).\", \"new_sentence\": \"Passionate about building scalable, agentic pipelines.\" }\n" +
            "  ],\n" +
            "  \"skills_to_add\": [\"TypeScript\", \"Agentic Pipelines\"],\n" +
            "  \"experience_edits\": [\n" +
            "    { \"type\": \"REPLACE\", \"original\": \"Contributed to multiple agile projects...\", \"updated\": \"Led backend development on 2 agile projects...\" },\n" +
            "    { \"type\": \"ADD\", \"after_bullet\": \"Streamlined error handling processes...\", \"new_bullet\": \"Designed and implemented AI behaviours...\" }\n" +
            "  ]\n" +
            "}";

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "gpt-4-turbo");
        payload.put("messages", java.util.List.of(
            Map.of("role", "system", "content", "You are a resume editor. You will be given a resume and potential improvements. Your task is to generate a JSON object specifying the exact edits to make. Follow the user's instructions precisely."),
            Map.of("role", "user", "content", prompt)
        ));
        payload.put("temperature", 0.2);
        payload.put("max_tokens", 1500);
        payload.put("response_format", Map.of("type", "json_object"));


        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openaiApiKey);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            System.out.println("[DEBUG] Sending request to OpenAI for edit plan...");
            ResponseEntity<String> resp = restTemplate.postForEntity("https://api.openai.com/v1/chat/completions", request, String.class);
            System.out.println("[DEBUG] Received response from OpenAI.");

            JsonNode respNode = objectMapper.readTree(resp.getBody());
            String jsonText = respNode.at("/choices/0/message/content").asText();
            
            System.out.println("[DEBUG] AI-generated Edit Plan:\n" + jsonText);
            
            return objectMapper.readTree(jsonText);

        } catch (Exception e) {
            System.err.println("[FAIL] Error calling OpenAI for edit plan: " + e.getMessage());
            e.printStackTrace();
            // Return an empty JSON object on failure
            return objectMapper.createObjectNode();
        }
    }

}


