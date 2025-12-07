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

    /**
     * Extract job description from plain text (no URL fetch).
     */
    public Map<String, Object> extractJobDescriptionFromText(String jobText) {
        Map<String, Object> result = new HashMap<>();
        if (jobText == null || jobText.isBlank()) {
            result.put("success", false);
            result.put("message", "No job text provided.");
            return result;
        }
        try {
            // Extract posting date from jobText (look for 'X days ago', 'X hours ago', or date)
            String postingDate = null;
            String[] lines = jobText.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.matches(".*\\d+ days ago.*")) {
                    // Extract number of days and calculate date
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+) days ago").matcher(line);
                    if (m.find()) {
                        int days = Integer.parseInt(m.group(1));
                        postingDate = java.time.LocalDate.now().minusDays(days).toString();
                        break;
                    }
                } else if (line.matches(".*\\d+ hours ago.*")) {
                    postingDate = java.time.LocalDate.now().toString();
                    break;
                } else if (line.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(line);
                    if (m.find()) {
                        postingDate = m.group(1);
                        break;
                    }
                }
            }

            // Extract years of experience from jobText
            int yearsOfExperience = 0;
            for (String line : lines) {
                line = line.toLowerCase();
                if (line.contains("less than two years") || line.contains("less than 2 years")) {
                    yearsOfExperience = 1;
                    break;
                } else if (line.contains("at least one year") || line.contains("at least 1 year")) {
                    yearsOfExperience = 1;
                    break;
                } else if (line.contains("at least two years") || line.contains("at least 2 years")) {
                    yearsOfExperience = 2;
                    break;
                } else if (line.contains("junior")) {
                    yearsOfExperience = 0;
                }
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+) years? of experience").matcher(line);
                if (m.find()) {
                    yearsOfExperience = Integer.parseInt(m.group(1));
                    break;
                }
            }

            Map<String, Object> parsed = parseJobWithLLM(jobText);
            result.put("success", true);
            result.putAll(parsed);
            if (postingDate != null) {
                result.put("postingDate", postingDate);
            }
            result.put("yearsOfExperience", yearsOfExperience);
            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error analyzing job text: " + e.getMessage());
            return result;
        }
    }

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
            String jobText = extractMainText(doc);
            if (jobText == null || jobText.isBlank()) {
                result.put("success", false);
                result.put("message", "Could not extract job description. Please paste manually.");
                result.put("url", vacancyUrl);
                return result;
            }
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
        // Prioritize LinkedIn right-column job details selectors
        String[] selectors = {
            "div.jobs-search__job-details--wrapper", // LinkedIn right column wrapper
            "div.scaffold_layout__detail.overflow-x-hidden.jobs-search__job-details", // LinkedIn right column main
            "section.jobs-description", // Sometimes used for job description
            "div[data-testid='jobDescriptionText']",
            "div.jobsearch-JobComponent-description",
            "div.job-description",
            "div[class*='description']",
            "article",
            "main"
        };
        for (String selector : selectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                // Try to find the largest text block inside the selected element
                Element mainEl = elements.first();
                String text = mainEl.text();
                // If the text is too short, try to find a deeper div with more content
                if (text.length() < 100) {
                    Elements deepDivs = mainEl.select("div, section, p");
                    String maxText = text;
                    for (Element e : deepDivs) {
                        String t = e.text();
                        if (t.length() > maxText.length()) {
                            maxText = t;
                        }
                    }
                    text = maxText;
                }
                return text;
            }
        }
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
            result.put("requiresUKPassport", false);
            return result;
        }
        try {
            RestTemplate rest = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openaiApiKey);
            String prompt =
                "Extract structured information from this job posting. Return a JSON object with these fields:\n" +
                "- position (string): job title\n" +
                "- company (string): company name\n" +
                "- location (string): job location (city, country, remote/hybrid/on-site)\n" +
                "- salaryRange (string): salary range or 'Not specified'\n" +
                "- employmentType (string): e.g. Full-time, Part-time, Contract, Remote, Hybrid\n" +
                "- summary (string): short summary/about the job/company\n" +
                "- mainFunctions (array of strings): main job functions/responsibilities (e.g., 'developing X', 'leading team', 'managing Y')\n" +
                "- requirements (array of strings): required experience, skills, education, mindset\n" +
                "- techStack (array of strings): technologies used (e.g., Python, React, AWS)\n" +
                "- benefits (array of strings): salary, equity, perks, growth, learning\n" +
                "- uniqueSellingPoints (array of strings): what makes this role/company unique\n" +
                "- specialRequirements (array of strings): security clearance, citizenship, etc.\n" +
                "- postingDate (string): posting date in YYYY-MM-DD format, default today's date. If not explicit, infer from phrases like 'X days ago', 'X hours ago', or date patterns near the top of the post.\n" +
                "\nJob posting:\n" + jobText;
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
            int jsonStart = assistantMsg.indexOf('{');
            int jsonEnd = assistantMsg.lastIndexOf('}') + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = assistantMsg.substring(jsonStart, jsonEnd);
                JsonNode parsed = objectMapper.readTree(jsonStr);
                result.put("position", parsed.get("position").asText("Unknown Position"));
                result.put("company", parsed.get("company").asText("Unknown Company"));
                result.put("location", parsed.get("location").asText("Unknown Location"));
                result.put("salaryRange", parsed.get("salaryRange").asText("Not specified"));
                result.put("employmentType", parsed.get("employmentType").asText("Not specified"));
                result.put("summary", parsed.get("summary").asText(""));
                result.put("mainFunctions", toStringArray(parsed.get("mainFunctions")));
                result.put("requirements", toStringArray(parsed.get("requirements")));
                result.put("techStack", toStringArray(parsed.get("techStack")));
                result.put("benefits", toStringArray(parsed.get("benefits")));
                result.put("uniqueSellingPoints", toStringArray(parsed.get("uniqueSellingPoints")));
                result.put("specialRequirements", toStringArray(parsed.get("specialRequirements")));
                result.put("postingDate", parsed.get("postingDate").asText(LocalDate.now().toString()));
                // For backward compatibility, also set security clearance and UK passport flags if present
                if (parsed.has("securityClearanceRequired")) {
                    result.put("securityClearanceRequired", parsed.get("securityClearanceRequired").asBoolean(false));
                }
                if (parsed.has("requiresUKPassport")) {
                    result.put("requiresUKPassport", parsed.get("requiresUKPassport").asBoolean(false));
                }
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

    private static String[] toStringArray(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) return new String[]{};
        String[] arr = new String[node.size()];
        for (int i = 0; i < node.size(); i++) {
            arr[i] = node.get(i).asText("");
        }
        return arr;
    }
}

