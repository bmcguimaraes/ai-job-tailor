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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MatchingService {

    /**
     * Analyze resume text for sections, skills, and summary (basic extraction).
     */
    public Map<String, Object> analyzeResumeText(String resumeText) {
        Map<String, Object> result = new HashMap<>();
        if (resumeText == null || resumeText.isBlank()) {
            result.put("success", false);
            result.put("message", "No resume text provided.");
            return result;
        }
        // Simple extraction: look for sections and skills
        String[] lines = resumeText.split("\n");
        List<String> skills = new ArrayList<>();
        List<String> experience = new ArrayList<>();
        String summary = "";
        boolean inSkills = false, inExperience = false, inSummary = false;
        for (String line : lines) {
            String l = line.trim().toLowerCase();
            if (l.contains("skills")) { inSkills = true; inExperience = false; inSummary = false; continue; }
            if (l.contains("experience")) { inSkills = false; inExperience = true; inSummary = false; continue; }
            if (l.contains("summary") || l.contains("profile")) { inSkills = false; inExperience = false; inSummary = true; continue; }
            if (inSkills && !l.isEmpty()) skills.add(line.trim());
            if (inExperience && !l.isEmpty()) experience.add(line.trim());
            if (inSummary && !l.isEmpty()) summary += line.trim() + " ";
        }
        result.put("skills", skills);
        result.put("experience", experience);
        result.put("summary", summary.trim());
        result.put("success", true);
        return result;
    }

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> computeScore(String resumeText, String jobText, 
                                           String[] requiredTechnicalSkills, 
                                           String[] requiredSoftSkills,
                                           int yearsRequired,
                                           String jobPosition,
                                           String[] mainFunctions) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. Embedding similarity (general match)
            double embeddingSim = getEmbeddingSimilarity(resumeText, jobText);

            // 2. Soft skills match (20% weight)
            double softSkillsScore = computeSkillsMatch(resumeText, requiredSoftSkills);

            // 3. Technical skills match (30% weight)
            double technicalSkillsScore = computeSkillsMatch(resumeText, requiredTechnicalSkills);

            // 4. Job functions/responsibilities match (20% weight)
            double jobFunctionsScore = computeJobFunctionsMatch(resumeText, mainFunctions);

            // 5. Role & experience match (20% weight) - adjusted from 40%
            double roleExperienceScore = computeRoleExperienceMatch(resumeText, jobPosition, yearsRequired);

            // 6. Top keywords match (10% weight)
            List<String> topKeywords = extractTopKeywords(jobText, 5);
            double keywordScore = computeKeywordMatch(resumeText, topKeywords);

            // Compute final score with new weights: soft 20%, technical 30%, functions 20%, role/exp 20%, keywords 10%
            double finalScore = (
                    0.20 * softSkillsScore +
                    0.30 * technicalSkillsScore +
                    0.20 * jobFunctionsScore +
                    0.20 * roleExperienceScore +
                    0.10 * keywordScore
            ) * 100;

            result.put("score", Math.round(finalScore));
            result.put("fit", finalScore >= 75 ? "Good match" : finalScore >= 50 ? "Moderate match" : "Poor match");
            result.put("details", new HashMap<String, Object>() {{
                put("embeddingSimilarity", Math.round(embeddingSim * 100));
                put("softSkillsScore", Math.round(softSkillsScore * 100));
                put("technicalSkillsScore", Math.round(technicalSkillsScore * 100));
                put("jobFunctionsScore", Math.round(jobFunctionsScore * 100));
                put("roleExperienceScore", Math.round(roleExperienceScore * 100));
                put("keywordScore", Math.round(keywordScore * 100));
            }});

            // Missing keywords
            List<String> missingKeywords = topKeywords.stream()
                    .filter(kw -> !resumeContainsKeyword(resumeText, kw))
                    .collect(Collectors.toList());
            result.put("missingKeywords", missingKeywords);
            result.put("topKeywords", topKeywords);

        } catch (Exception e) {
            result.put("score", 0);
            result.put("fit", "Error computing score");
            result.put("error", e.getMessage());
        }

        return result;
    }

    private double getEmbeddingSimilarity(String text1, String text2) throws Exception {
        if (openaiApiKey == null || openaiApiKey.isBlank()) return 0.5;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openaiApiKey);

        Map<String, Object> embReq1 = Map.of("model", "text-embedding-3-small", "input", text1);
        ResponseEntity<String> embResp1 = restTemplate.postForEntity("https://api.openai.com/v1/embeddings", 
                new HttpEntity<>(embReq1, headers), String.class);

        Map<String, Object> embReq2 = Map.of("model", "text-embedding-3-small", "input", text2);
        ResponseEntity<String> embResp2 = restTemplate.postForEntity("https://api.openai.com/v1/embeddings", 
                new HttpEntity<>(embReq2, headers), String.class);

        JsonNode j1 = objectMapper.readTree(embResp1.getBody());
        JsonNode j2 = objectMapper.readTree(embResp2.getBody());

        List<Double> v1 = new ArrayList<>();
        List<Double> v2 = new ArrayList<>();

        for (JsonNode n : j1.at("/data/0/embedding")) v1.add(n.asDouble());
        for (JsonNode n : j2.at("/data/0/embedding")) v2.add(n.asDouble());

        return cosineSimilarity(v1, v2);
    }

    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1.isEmpty() || v2.isEmpty()) return 0.0;
        double dot = 0.0, n1 = 0.0, n2 = 0.0;
        for (int i = 0; i < Math.min(v1.size(), v2.size()); i++) {
            double a = v1.get(i), b = v2.get(i);
            dot += a * b;
            n1 += a * a;
            n2 += b * b;
        }
        return dot / (Math.sqrt(n1) * Math.sqrt(n2));
    }

    private double computeSkillsMatch(String resumeText, String[] skills) {
        if (skills == null || skills.length == 0) return 0.5;
        long matched = Arrays.stream(skills)
                .filter(skill -> resumeContainsKeyword(resumeText, skill))
                .count();
        return (double) matched / skills.length;
    }

    private double computeJobFunctionsMatch(String resumeText, String[] mainFunctions) {
        if (mainFunctions == null || mainFunctions.length == 0) return 0.5;
        long matched = Arrays.stream(mainFunctions)
                .filter(func -> resumeContainsKeywordVariant(resumeText, func))
                .count();
        return (double) matched / mainFunctions.length;
    }

    private double computeRoleExperienceMatch(String resumeText, String jobPosition, int yearsRequired) {
        double roleMatch = 0.0;
        
        // Check if job role is mentioned (e.g., "Software Developer", "Java Developer")
        if (resumeContainsKeywordVariant(resumeText, jobPosition)) {
            roleMatch = 0.8;
        } else if (resumeContainsKeywordVariant(resumeText, "developer") || 
                   resumeContainsKeywordVariant(resumeText, "programmer")) {
            roleMatch = 0.5;
        }

        // Check years of experience
        int resumeYears = extractYearsOfExperience(resumeText);
        double expMatch = 0.0;
        if (resumeYears >= yearsRequired && resumeYears <= yearsRequired + 2) {
            expMatch = 1.0;
        } else if (resumeYears >= yearsRequired) {
            expMatch = 0.9; // More experience is OK
        } else if (resumeYears >= yearsRequired - 1) {
            expMatch = 0.6; // Close to requirement
        } else {
            expMatch = 0.2; // Below requirement
        }

        return (roleMatch * 0.5) + (expMatch * 0.5);
    }

    private double computeKeywordMatch(String resumeText, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return 0.5;
        long matched = keywords.stream()
                .filter(kw -> resumeContainsKeyword(resumeText, kw))
                .count();
        return (double) matched / keywords.size();
    }

    private List<String> extractTopKeywords(String jobText, int limit) throws Exception {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            return Arrays.asList("java", "spring", "rest", "microservices", "cloud");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openaiApiKey);

        String prompt = "Extract the top " + limit + " most important technical or role keywords from this job posting. Return as a JSON array of strings only, no explanation.\n" + jobText;

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "gpt-3.5-turbo");
        payload.put("messages", java.util.List.of(
                Map.of("role", "system", "content", "You are a keyword extraction assistant. Return only a JSON array of strings."),
                Map.of("role", "user", "content", prompt)
        ));
        payload.put("temperature", 0.0);
        payload.put("max_tokens", 100);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity("https://api.openai.com/v1/chat/completions", request, String.class);

        JsonNode respNode = objectMapper.readTree(resp.getBody());
        String assistantMsg = respNode.at("/choices/0/message/content").asText();

        int arrStart = assistantMsg.indexOf('[');
        int arrEnd = assistantMsg.lastIndexOf(']') + 1;
        if (arrStart >= 0 && arrEnd > arrStart) {
            String arrStr = assistantMsg.substring(arrStart, arrEnd);
            JsonNode keywords = objectMapper.readTree(arrStr);
            List<String> result = new ArrayList<>();
            for (JsonNode kw : keywords) {
                result.add(kw.asText().toLowerCase());
            }
            return result.subList(0, Math.min(limit, result.size()));
        }

        return java.util.List.of("java", "spring", "rest", "microservices", "cloud");
    }

    private boolean resumeContainsKeyword(String text, String keyword) {
        if (keyword == null || keyword.isBlank()) return false;
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(keyword.toLowerCase()) + "\\b");
        return pattern.matcher(text.toLowerCase()).find();
    }

    private boolean resumeContainsKeywordVariant(String text, String keyword) {
        String lower = text.toLowerCase();
        return lower.contains(keyword.toLowerCase());
    }

    private int extractYearsOfExperience(String resumeText) {
        // Simple heuristic: look for numbers followed by "years" or "year"
        Pattern pattern = Pattern.compile("(\\d+)\\s*years?");
        java.util.regex.Matcher matcher = pattern.matcher(resumeText.toLowerCase());
        int maxYears = 0;
        while (matcher.find()) {
            int years = Integer.parseInt(matcher.group(1));
            if (years > maxYears) maxYears = years;
        }
        return maxYears;
    }
}
