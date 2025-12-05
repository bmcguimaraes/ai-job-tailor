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

    public Map<String, Object> generateImprovements(String resumeText, String jobText, 
                                                   List<String> missingKeywords, 
                                                   int currentScore) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (openaiApiKey == null || openaiApiKey.isBlank()) {
                result.put("improvements", new ArrayList<>());
                result.put("selectedKeywords", new ArrayList<>());
                result.put("projectedScore", currentScore);
                return result;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openaiApiKey);

            String prompt = """
                Given this resume and job description, suggest up to 6 specific improvements to make the resume better match the job.
                Focus on:
                1. Missing keywords/skills that could be added (you can add them naturally)
                2. New bullet points with job-relevant functions/responsibilities that could be added or emphasized
                3. Bullet point rewrites to align experience descriptions with the job's main functions
                4. Sections that need emphasis or improvement
                5. How these changes would impact the match score
                
                Missing keywords that should be considered: """ + String.join(", ", missingKeywords) + """
                
                Resume:
                """ + resumeText + """
                
                Job Description:
                """ + jobText + """
                
                Return a JSON object with:
                - improvements (array of objects with: improvement, section, impact_score_increase (0-5), keywords_added (array), bullet_point_suggestion (for experience/function improvements), why)
                
                Example format:
                {
                  "improvements": [
                    {"improvement": "Add Spring Boot to skills", "section": "Skills", "impact_score_increase": 3, "keywords_added": ["Spring Boot"], "bullet_point_suggestion": null, "why": "Required skill"},
                    {"improvement": "Rewrite to emphasize team leadership", "section": "Experience", "impact_score_increase": 4, "keywords_added": ["led team", "coordination"], "bullet_point_suggestion": "Led a team of X developers to deliver Y project", "why": "Role requires team leadership"}
                  ]
                }
                """;

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", "gpt-3.5-turbo");
            payload.put("messages", java.util.List.of(
                    Map.of("role", "system", "content", "You are a resume improvement advisor. Provide concrete, actionable suggestions. Return valid JSON only."),
                    Map.of("role", "user", "content", prompt)
            ));
            payload.put("temperature", 0.5);
            payload.put("max_tokens", 800);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity("https://api.openai.com/v1/chat/completions", request, String.class);

            JsonNode respNode = objectMapper.readTree(resp.getBody());
            String assistantMsg = respNode.at("/choices/0/message/content").asText();

            int jsonStart = assistantMsg.indexOf('{');
            int jsonEnd = assistantMsg.lastIndexOf('}') + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = assistantMsg.substring(jsonStart, jsonEnd);
                JsonNode parsed = objectMapper.readTree(jsonStr);
                
                List<Map<String, Object>> improvements = new ArrayList<>();
                JsonNode impArray = parsed.get("improvements");
                if (impArray != null && impArray.isArray()) {
                    for (int i = 0; i < Math.min(6, impArray.size()); i++) {
                        JsonNode imp = impArray.get(i);
                        Map<String, Object> impMap = new HashMap<>();
                        impMap.put("improvement", imp.get("improvement").asText(""));
                        impMap.put("section", imp.get("section").asText(""));
                        impMap.put("impactScoreIncrease", imp.get("impact_score_increase").asInt(2));
                        impMap.put("keywordsAdded", toList(imp.get("keywords_added")));
                        impMap.put("bulletPointSuggestion", imp.get("bullet_point_suggestion").asText(null));
                        impMap.put("why", imp.get("why").asText(""));
                        improvements.add(impMap);
                    }
                }
                
                result.put("improvements", improvements);
                
                // Estimate projected score with all improvements
                int totalImpact = improvements.stream()
                        .mapToInt(imp -> (int) imp.get("impactScoreIncrease"))
                        .sum();
                int projectedScore = Math.min(100, currentScore + totalImpact);
                result.put("projectedScore", projectedScore);

            }

        } catch (Exception e) {
            result.put("improvements", new ArrayList<>());
            result.put("projectedScore", currentScore);
            result.put("error", e.getMessage());
        }

        result.put("selectedKeywords", new ArrayList<>());
        return result;
    }

    public Map<String, Object> generateTailoredResume(String resumeText, String jobText, 
                                                     List<String> selectedKeywords, int maxDeviationPercent) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (openaiApiKey == null || openaiApiKey.isBlank()) {
                result.put("tailoredText", resumeText);
                result.put("deviationPercent", 0);
                return result;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openaiApiKey);

            String keywordInstructions = selectedKeywords.isEmpty() 
                    ? "Use your best judgment for keywords." 
                    : "Prioritize adding these keywords naturally: " + String.join(", ", selectedKeywords);

            String prompt = "Rewrite this resume to better match the job description while preserving facts and truthfulness.\n" +
                    "You can change up to " + maxDeviationPercent + "% of the content.\n" +
                    "\n" +
                    "Guidelines:\n" +
                    "1. Keep all facts, dates, and responsibilities accurate\n" +
                    "2. Rephrase bullet points to use job-relevant language and keywords\n" +
                    "3. Emphasize skills and experience that match the job\n" +
                    "4. Maintain the same structure (name, experience, education, skills, etc.)\n" +
                    "5. " + keywordInstructions + "\n" +
                    "\n" +
                    "Job Description:\n" + jobText + "\n" +
                    "\n" +
                    "Original Resume:\n" + resumeText + "\n" +
                    "\n" +
                    "Return ONLY the rewritten resume text, preserving structure and formatting.";

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", "gpt-3.5-turbo");
            payload.put("messages", java.util.List.of(
                    Map.of("role", "system", "content", "You are a professional resume writer. Rewrite the resume to match the job while keeping it truthful. Return only the resume text."),
                    Map.of("role", "user", "content", prompt)
            ));
            payload.put("temperature", 0.3);
            payload.put("max_tokens", 1500);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity("https://api.openai.com/v1/chat/completions", request, String.class);

            JsonNode respNode = objectMapper.readTree(resp.getBody());
            String tailoredText = respNode.at("/choices/0/message/content").asText();

            // Estimate deviation
            int deviationPercent = estimateDeviation(resumeText, tailoredText);

            result.put("tailoredText", tailoredText);
            result.put("deviationPercent", deviationPercent);
            result.put("success", true);

        } catch (Exception e) {
            result.put("tailoredText", resumeText);
            result.put("deviationPercent", 0);
            result.put("error", e.getMessage());
            result.put("success", false);
        }

        return result;
    }

    private List<String> toList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }

    private int estimateDeviation(String original, String tailored) {
        // Simple heuristic: count different lines
        String[] origLines = original.split("\n");
        String[] tailLines = tailored.split("\n");
        
        int maxLines = Math.max(origLines.length, tailLines.length);
        if (maxLines == 0) return 0;
        
        int differentLines = 0;
        for (int i = 0; i < maxLines; i++) {
            String origLine = i < origLines.length ? origLines[i].trim() : "";
            String tailLine = i < tailLines.length ? tailLines[i].trim() : "";
            if (!origLine.equals(tailLine)) {
                differentLines++;
            }
        }
        
        return (differentLines * 100) / maxLines;
    }
}
