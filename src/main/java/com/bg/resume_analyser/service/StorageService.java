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
            // Create folder: {company}_{position}
            String folderName = sanitizeFolderName(company + "_" + position);
            Path folderPath = Paths.get(BASE_FOLDER, folderName);
            Files.createDirectories(folderPath);

            // Save original resume text (for reference)
            String originalFileName = "original_resume_" + LocalDate.now() + ".txt";
            Files.write(folderPath.resolve(originalFileName), originalResumeText.getBytes());

            // Save tailored resume as text file
            String tailoredFileName = "tailored_resume_" + LocalDate.now() + ".txt";
            Files.write(folderPath.resolve(tailoredFileName), tailoredResumeText.getBytes());

            // Save DOCX file
            String docxFileName = "tailored_resume_" + LocalDate.now() + ".docx";
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
                    
                    Note: Original resume and tailored resume saved. Review both before applying.
                    """,
                    company, position, vacancyUrl, LocalDate.now(), matchScore,
                    formatImprovements(improvements));

            Files.write(folderPath.resolve("metadata.txt"), metadata.getBytes());

            result.put("success", true);
            result.put("folderPath", folderPath.toString());
            result.put("resumeFile", docxFileName);
            result.put("metadataFile", "metadata.txt");
            result.put("message", "Resume and metadata saved successfully");

        } catch (IOException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
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
}
