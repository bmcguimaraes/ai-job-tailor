package com.bg.resume_analyser.service;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class StorageService {

    private static final String BASE_FOLDER = "/Users/brunoguimaraes/Documents/JA";

    public Map<String, Object> saveApprovedResume(String company, String position, 
                                                  String tailoredResumeText, byte[] docxBytes,
                                                  String vacancyUrl, int matchScore,
                                                  Map<String, Object> improvements,
                                                  String originalResumeText) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Ensure company and position are not empty
            String safeCompany = (company == null || company.isBlank()) ? "UnknownCompany" : company;
            String safePosition = (position == null || position.isBlank()) ? "UnknownPosition" : position;
            String folderName = sanitizeFolderName(safeCompany + "_" + safePosition);
            Path folderPath = Paths.get(BASE_FOLDER, folderName);
            Files.createDirectories(folderPath);

            // Extract user's name from resume text (first non-empty line)
            String extractedUserName = extractUserNameFromResumeText(originalResumeText);
            if (extractedUserName == null || extractedUserName.isBlank()) {
                extractedUserName = "User";
            }
            String safeUserName = sanitizeNameForFilename(extractedUserName);
            String safePositionFile = sanitizeFolderName(safePosition).toLowerCase();
            String docxFileName = safeUserName + "_" + safePositionFile + ".docx";
            Files.write(folderPath.resolve(docxFileName), docxBytes);

            // Save metadata (vacancy URL, date, score, improvements)
                                String metadata = String.format("""
                                    Job Application Metadata
                                    ========================
                                    Company: %s
                                    Position: %s
                                    Vacancy URL: %s
                                    Applied Date: %s
                                    Match Score: %d/100

                                    --- Improvements Overview ---
                                    %s

                                    --- Detailed Improvement Suggestions ---
                                    %s

                                    Note: Only tailored resume (DOCX) and metadata are saved. PDF output is planned for future releases.
                                    """,
                                    safeCompany, safePosition, vacancyUrl, LocalDate.now(), matchScore,
                                    formatImprovements(improvements),
                                    formatDetailedImprovements(improvements));



            Files.write(folderPath.resolve("metadata.txt"), metadata.getBytes());

            result.put("success", true);
            result.put("folderPath", folderPath.toString());
            result.put("resumeFile", docxFileName);
            result.put("metadataFile", "metadata.txt");
            result.put("message", "Tailored resume (DOCX) and metadata saved successfully");
        } catch (IOException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    /**
     * Sanitizes a name for use in a filename, preserving ASCII and common accented letters by converting them to their closest ASCII equivalent.
     */
    private String sanitizeNameForFilename(String name) {
        if (name == null) return "User";
        // Normalize to NFD and remove diacritics (accents)
        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD);
        normalized = normalized.replaceAll("[^\\p{ASCII}]", ""); // Remove non-ASCII
        normalized = normalized.replaceAll("[^a-zA-Z0-9]", ""); // Remove any remaining non-alphanumeric
        return normalized;
    }

    private String sanitizeFolderName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
    }

    private String formatImprovements(Map<String, Object> improvements) {
        if (improvements == null || improvements.isEmpty()) {
            return "No specific improvements recorded.";
        }
        StringBuilder sb = new StringBuilder();
        if (improvements.containsKey("topMissingKeywords")) {
            sb.append("Missing Keywords: ");
            sb.append(improvements.get("topMissingKeywords")).append("\n");
        }
        if (improvements.containsKey("missingTechStack")) {
            sb.append("Missing Tech Stack: ");
            sb.append(improvements.get("missingTechStack")).append("\n");
        }
        if (improvements.containsKey("missingMainFunctions")) {
            sb.append("Missing Main Functions: ");
            sb.append(improvements.get("missingMainFunctions")).append("\n");
        }
        if (improvements.containsKey("requirements")) {
            sb.append("Other Requirements: ");
            sb.append(improvements.get("requirements")).append("\n");
        }
        // List detailed improvement suggestions if present
        // (Handled by formatDetailedImprovements for metadata)
        return sb.toString();
    }


    /**
     * Extracts user name from resume text (first non-empty line, removes ~ if present).
     */
    private String extractUserNameFromResumeText(String resumeText) {
        if (resumeText == null) return null;
        for (String line : resumeText.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                // Remove ~ and keep letters (including accents) and spaces
                String name = trimmed.replace("~", "").replaceAll("[^\\p{L} ]", "").replaceAll(" +", " ");
                // Use only first two words if more than two
                String[] parts = name.split(" ");
                if (parts.length > 2) {
                    name = parts[0] + " " + parts[1];
                }
                return name.trim();
            }
        }
        return null;
    }
    /**
     * Formats detailed improvement suggestions for metadata file readability.
     */
    private String formatDetailedImprovements(Map<String, Object> improvements) {
        if (improvements == null || !improvements.containsKey("improvements")) {
            return "No detailed suggestions.";
        }
        Object imps = improvements.get("improvements");
        StringBuilder sb = new StringBuilder();
        if (imps instanceof Iterable) {
            for (Object imp : (Iterable<?>) imps) {
                if (imp instanceof Map) {
                    Map<?,?> map = (Map<?,?>) imp;
                    sb.append("- ");
                    Object improvement = map.get("improvement");
                    sb.append(improvement != null ? improvement.toString() : "");
                    Object section = map.get("section");
                    sb.append(" | Section: ").append(section != null ? section.toString() : "");
                    Object impact = map.get("impactScoreIncrease");
                    sb.append(" | Impact: +").append(impact != null ? impact.toString() : "0");
                    Object keywords = map.get("keywordsAdded");
                    sb.append(" | Keywords: ").append(keywords != null ? keywords.toString() : "[]");
                    Object bullet = map.get("bulletPointSuggestion");
                    if (bullet != null) {
                        sb.append(" | Bullet: ").append(bullet.toString());
                    }
                    Object why = map.get("why");
                    sb.append(" | Why: ").append(why != null ? why.toString() : "");
                    sb.append("\n");
                } else {
                    sb.append("- ").append(imp.toString()).append("\n");
                }
            }
        } else {
            sb.append(imps.toString()).append("\n");
        }
        return sb.toString();
    }
}
