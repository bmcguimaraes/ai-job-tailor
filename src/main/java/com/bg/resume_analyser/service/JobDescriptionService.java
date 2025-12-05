package com.bg.resume_analyser.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class JobDescriptionService {

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> extractJobDescription(String vacancyUrl) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Fetch the webpage
            Document doc = Jsoup.connect(vacancyUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            // Extract main job text (common selectors for LinkedIn, Indeed, company sites)
            String jobText = extractMainText(doc);
            
            if (jobText == null || jobText.isBlank()) {
                result.put("success", false);
                result.put("message", "Could not extract job description. Please paste manually.");
                result.put("url", vacancyUrl);
                return result;
            }

            // Use LLM to parse structured fields from the job text
            Map<String, Object> parsed = parseJobWithLLM(jobText);
            
            result.put("success", true);
            result.put("url", vacancyUrl);
            result.putAll(parsed);
            return result;

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error fetching job description: " + e.getMessage() + ". Please paste manually.");
            result.put("url", vacancyUrl);
            return result;
        }
    }

    private String extractMainText(Document doc) {
        // Try common selectors for job postings
        String[] selectors = {
            "div[data-testid='jobDescriptionText']",  // LinkedIn
            "div.jobsearch-JobComponent-description", // Indeed
            "div.job-description",
            "div[class*='description']",
            "article",
            "main"
        };

        for (String selector : selectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                return elements.first().text();
            }
        }

        // Fallback: get all text from body
        Element body = doc.body();
        return body != null ? body.text() : null;
    }

    private Map<String, Object> parseJobWithLLM(String jobText) {
        Map<String, Object> result = new HashMap<>();
        
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            result.put("position", "Unknown Position");
            result.put("salary", "Not specified");
            result.put("yearsOfExperience", 0);
            result.put("technicalSkills", new String[]{});
            result.put("softSkills", new String[]{});
            result.put("mainFunctions", new String[]{});
            result.put("postingDate", LocalDate.now().toString());
            result.put("securityClearanceRequired", false);
            return result;
        }

        try {
            RestTemplate rest = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openaiApiKey);

            String prompt = """
                Extract structured information from this job posting. Return a JSON object with these fields:
                - position (string): job title
                - salary (string): salary range or "Not specified"
                - yearsOfExperience (number): minimum years required, default 0
                - technicalSkills (array of strings): technical skills required
                - softSkills (array of strings): soft skills required
                - mainFunctions (array of strings): main job functions/responsibilities (e.g., "developing X", "leading team", "managing Y")
                - postingDate (string): posting date in YYYY-MM-DD format, default today's date
                - securityClearanceRequired (boolean): true if SC/security clearance is mentioned
                - requiresUKPassport (boolean): true if UK passport or British citizenship is required
                
                Job posting:
                """ + jobText;

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", "gpt-3.5-turbo");
            payload.put("messages", java.util.List.of(
                    Map.of("role", "system", "content", "You are a job posting analyzer. Extract information and return only valid JSON."),
                    Map.of("role", "user", "content", prompt)
            ));
            payload.put("temperature", 0.0);
            payload.put("max_tokens", 500);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> resp = rest.postForEntity("https://api.openai.com/v1/chat/completions", request, String.class);

            JsonNode respNode = objectMapper.readTree(resp.getBody());
            String assistantMsg = respNode.at("/choices/0/message/content").asText();
            
            // Extract JSON from response
            int jsonStart = assistantMsg.indexOf('{');
            int jsonEnd = assistantMsg.lastIndexOf('}') + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = assistantMsg.substring(jsonStart, jsonEnd);
                JsonNode parsed = objectMapper.readTree(jsonStr);
                
                result.put("position", parsed.get("position").asText("Unknown Position"));
                result.put("salary", parsed.get("salary").asText("Not specified"));
                result.put("yearsOfExperience", parsed.get("yearsOfExperience").asInt(0));
                result.put("technicalSkills", toStringArray(parsed.get("technicalSkills")));
                result.put("softSkills", toStringArray(parsed.get("softSkills")));
                result.put("mainFunctions", toStringArray(parsed.get("mainFunctions")));
                result.put("postingDate", parsed.get("postingDate").asText(LocalDate.now().toString()));
                result.put("securityClearanceRequired", parsed.get("securityClearanceRequired").asBoolean(false));
                result.put("requiresUKPassport", parsed.get("requiresUKPassport").asBoolean(false));
            }

        } catch (Exception e) {
            result.put("position", "Unknown Position");
            result.put("salary", "Not specified");
            result.put("yearsOfExperience", 0);
            result.put("technicalSkills", new String[]{});
            result.put("softSkills", new String[]{});
            result.put("mainFunctions", new String[]{});
            result.put("postingDate", LocalDate.now().toString());
            result.put("securityClearanceRequired", false);
            result.put("requiresUKPassport", false);
        }

        return result;
    }

    private String[] toStringArray(JsonNode node) {
        if (node == null || !node.isArray()) return new String[]{};
        String[] arr = new String[node.size()];
        for (int i = 0; i < node.size(); i++) {
            arr[i] = node.get(i).asText();
        }
        return arr;
    }
}
