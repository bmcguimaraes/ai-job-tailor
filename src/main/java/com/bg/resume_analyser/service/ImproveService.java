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

    public Map<String, Object> generateEditPlan(String resumeText, String jobText) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (openaiApiKey == null || openaiApiKey.isBlank()) {
                result.put("edit_plan", new ArrayList<>());
                result.put("skills_to_add", new HashMap<>());
                return result;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openaiApiKey);

            String prompt = "You are an expert resume editor. Your task is to surgically edit a resume to perfectly match a job description, preserving the original formatting. " +
                "You MUST return ONLY a valid JSON object. Do not include any text, explanations, or markdown before or after the JSON object.\\n\\n" +
                "**INSTRUCTIONS:**\\n" +
                "1.  **Extract Key Info**: From the job description, you MUST extract the `company_name`, `position_title`, and `contact_person`.\\n" +
                "2.  **Semantic Check**: Before suggesting an addition, verify that the skill or concept is not already present in the resume. Do not suggest redundant additions.\\n" +
                "3.  **Comprehensive Review**: On every run, you MUST review the 'Personal Summary' and 'Experience' sections for potential improvements, and the 'Skills & Abilities' section to identify skills to add. Your suggestions must be comprehensive and consistent.\\n" +
                "4.  **Suggestion Strategy**: Prioritize suggestions based on their impact and relevance to the job description.\\n" +
                "    - **Critical Functions**: If a main function from the job description is missing and critical, incorporate it into the `Personal Summary` via the `edit_plan`.\\n" +
                "    - **Important Functions**: If a function is important but not critical, incorporate it into the `Experience` section via the `edit_plan`.\\n" +
                "    - **High-Impact Skills**: Add high-impact missing skills and abilities to the `skills_to_add` object.\\n" +
                "5.  **JSON Output Structure**: The JSON object must have five root keys: `company_name`, `position_title`, `contact_person`, `edit_plan`, and `skills_to_add`.\\n" +
                "    - `company_name`: A string containing the name of the company hiring. If not found, use `\"N/A\"`.\\n" +
                "    - `position_title`: A string containing the title of the role. If not found, use `\"N/A\"`.\\n" +
                "    - `contact_person`: A string containing the name of the recruiter or hiring manager. If not found, use an empty string `\"\"`.\\n" +
                "    - The `edit_plan` is an array of objects for edits to 'Personal Summary' and 'Experience' ONLY.\\n" +
                "    - The `skills_to_add` is an object for adding new, categorized skills to the 'Skills & Abilities' section.\\n" +
                "6.  **Edit Object Structure (`edit_plan`)**: Each object in the `edit_plan` array must have these keys:\\n" +
                "    - `action`: Must be `\"REPLACE\"`. (Use this to rewrite sentences or bullet points).\\n" +
                "    - `section`: The resume section to edit. Must be one of `\"Personal Summary\"` or `\"Experience\"`.\\n" +
                "    - `original_text`: The exact, original text to be replaced. For bullet points, this must be the complete and exact text of the bullet point.\\n" +
                "    - `new_text`: The new, improved text.\\n" +
                "7.  **Skills Categorization (`skills_to_add`)**: This object must categorize all new skills to be added. The allowed categories are:\\n" +
                "    - `Languages`, `Frameworks & Libraries`, `Cloud & DevOps`, `Databases`, `Professional Skills & Methodologies`.\\n" +
                "8.  **Rules & Constraints**:\\n" +
                "    - **Truthfulness**: You MUST NOT invent or exaggerate experience. Do not change the user's job title (e.g., from 'Software Developer' to 'Senior Software Developer'). Your role is to align existing experience with the job description, not to create new qualifications. The user is changing careers and is not a senior yet, so you must not label them as such.\\n" +
                "    - **Skill Categorization**: Before adding a skill, you MUST first identify its nature (e.g., 'DAML' is a language). Then, place it in the most accurate category. Do not miscategorize skills.\\n" +
                "    - **Targeting Bullet Points**: To replace a bullet point in the 'Experience' section, the `original_text` MUST match the bullet point's text exactly.\\n" +
                "    - **Bullet Point Logic**: When editing a bullet point in the 'Experience' section, first evaluate if the new information can be logically and grammatically appended. If appending would sound awkward or disrupt the flow, you MUST instead replace the entire bullet point with a rewritten, coherent version that incorporates the new information. Prefer rewriting for clarity and impact.\\n" +
                "    - **Capitalization**: For skills, capitalize proper nouns (e.g., 'Java', 'Azure', 'Spring Boot'). For all other skills, use sentence case (e.g., 'Performance tuning', 'Prompt engineering').\\n" +
                "    - Use UK English spelling and grammar.\\n" +
                "    - Be concise and relevant. Do not add fluff.\\n" +
                "    - The `edit_plan` MUST NOT target the 'Skills & Abilities' section. All direct additions to the 'Skills & Abilities' section are handled by `skills_to_add`.\\n\\n" +
                "**CONTEXT:**\\n" +
                "- **Resume**:\\n```\\n" + resumeText + "\\n```\\n" +
                "- **Job Description**:\\n```\\n" + jobText + "\\n```\\n" +
                "- **Key Job Requirements**: Pay close attention to the 'Tech Stack' and 'Main Functions' or 'Responsibilities' sections of the job description. Your suggestions should prioritize aligning the resume with these key requirements.\\n\\n" +
                "**EXAMPLE OUTPUT:**\\n" +
                "```json\\n" +
                "{\\n" +
                "  \\\"company_name\\\": \\\"Innovate Inc.\\\",\\n" +
                "  \\\"position_title\\\": \\\"Senior AI Developer\\\",\\n" +
                "  \\\"contact_person\\\": \\\"Jane Doe\\\",\\n" +
                "  \\\"edit_plan\\\": [\\n" +
                "    {\\n" +
                "      \\\"action\\\": \\\"REPLACE\\\",\\n" +
                "      \\\"section\\\": \\\"Experience\\\",\\n" +
                "      \\\"original_text\\\": \\\"• Enhanced batch chain performance, reducing the time spent on routine operations by 27% and significantly improving overall processing efficiency and throughput.\\\",\\n" +
                "      \\\"new_text\\\": \\\"• Enhanced batch chain performance using Spring Batch, reducing routine operations time by 27% and significantly improving overall processing efficiency and throughput.\\\"\\n" +
                "    }\\n" +
                "  ],\\n" +
                "  \\\"skills_to_add\\\": {\\n" +
                "    \\\"Languages\\\": [\\\"TypeScript\\\"],\\n" +
                "    \\\"soft_skills_to_add\\\": [\\\"AI-driven development environments\\\"]\\n" +
                "  }\\n" +
                "}\\n" +
                "```";

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", "gpt-4-turbo");
            payload.put("response_format", Map.of("type", "json_object"));
            payload.put("messages", java.util.List.of(
                Map.of("role", "system", "content", "You are a resume editing assistant that returns only valid JSON."),
                Map.of("role", "user", "content", prompt)
            ));
            payload.put("temperature", 0.2);
            payload.put("max_tokens", 1500);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity("https://api.openai.com/v1/chat/completions", request, String.class);

            JsonNode respNode = objectMapper.readTree(resp.getBody());
            String assistantMsg = respNode.at("/choices/0/message/content").asText();
            JsonNode parsed = objectMapper.readTree(assistantMsg);

            result.put("company_name", parsed.has("company_name") ? parsed.get("company_name").asText() : "N/A");
            result.put("position_title", parsed.has("position_title") ? parsed.get("position_title").asText() : "N/A");
            result.put("contact_person", parsed.has("contact_person") ? parsed.get("contact_person").asText() : "");
            result.put("edit_plan", parsed.has("edit_plan") ? parsed.get("edit_plan") : new ArrayList<>());
            result.put("skills_to_add", parsed.has("skills_to_add") ? parsed.get("skills_to_add") : new HashMap<>());

        } catch (Exception e) {
            System.err.println("Error generating edit plan: " + e.getMessage());
            result.put("edit_plan", new ArrayList<>());
            result.put("skills_to_add", new HashMap<>());
            result.put("error", "[FAIL] Could not generate edit plan from LLM.");
        }
        return result;
    }
}


