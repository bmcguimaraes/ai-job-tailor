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
                    
                    Improvements Applied:
                    %s
                    
                    Note: Only tailored resume (DOCX) and metadata are saved. PDF output is planned for future releases.
                    """,
                    safeCompany, safePosition, vacancyUrl, LocalDate.now(), matchScore,
                    formatImprovements(improvements));

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
        for (Map.Entry<String, Object> entry : improvements.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
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
}
