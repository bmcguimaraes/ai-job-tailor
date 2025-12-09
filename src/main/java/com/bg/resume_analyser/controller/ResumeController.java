package com.bg.resume_analyser.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.bg.resume_analyser.model.Resume;
import com.bg.resume_analyser.repository.ResumeRepository;
import com.bg.resume_analyser.service.*;

import org.apache.tika.Tika;

import java.util.*;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    // Helper to safely extract int from Map<String, Object>
    private int getIntFromMap(Map<String, Object> map, String key) {
        Object value = map.getOrDefault(key, 0);
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private final ResumeRepository resumeRepository;
    private final Tika tika = new Tika();
    private final JobDescriptionService jobDescriptionService;
    private final MatchingService matchingService;
    private final ImproveService improveService;
    private final DocxService docxService;
    private final StorageService storageService;

    @Autowired
    public ResumeController(ResumeRepository resumeRepository,
                           JobDescriptionService jobDescriptionService,
                           MatchingService matchingService,
                           ImproveService improveService,
                           DocxService docxService,
                           StorageService storageService) {
        this.resumeRepository = resumeRepository;
        this.jobDescriptionService = jobDescriptionService;
        this.matchingService = matchingService;
        this.improveService = improveService;
        this.docxService = docxService;
        this.storageService = storageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "file is required"));
        }

        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".pages")) {
            // Bold warning for .pages file
            System.out.println("\u001B[1m[WARNING]\u001B[0m .pages files are not officially supported. Extraction may be poor and formatting will not be preserved. Please export as DOCX for best results.");
        }
        if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            // Bold warning for PDF file
            System.out.println("\u001B[1m[WARNING]\u001B[0m PDF formatting cannot be preserved. Please upload DOCX for best results.");
        }

        try {
            // Diagnostic logging for file size and MIME type
            long fileSize = file.getSize();
            String mimeType = tika.detect(file.getInputStream());
            System.out.println("[ResumeController] Uploaded file: " + filename);
            System.out.println("[ResumeController] File size: " + fileSize + " bytes");
            System.out.println("[ResumeController] Detected MIME type: " + mimeType);

            // If DOCX, save to /Users/brunoguimaraes/Documents/JA/ for template-based tailoring
            if (filename != null && filename.toLowerCase().endsWith(".docx")) {
                java.nio.file.Path jaFolder = java.nio.file.Paths.get(System.getProperty("user.home"), "Documents", "JA");
                java.nio.file.Files.createDirectories(jaFolder);
                java.nio.file.Path destPath = jaFolder.resolve(filename);
                try (java.io.InputStream in = file.getInputStream()) {
                    java.nio.file.Files.copy(in, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println("[ResumeController] DOCX file saved to: " + destPath);
            }

            // Reset input stream for parsing (since detect may consume it)
            String text = tika.parseToString(file.getInputStream());
            System.out.println("[ResumeController] Extracted resume text:\n" + text);
            Resume r = new Resume(filename, text);
            // Analyze resume text immediately after upload
            Map<String, Object> resumeAnalysis = matchingService.analyzeResumeText(text);
            Resume saved = resumeRepository.save(r);
            // Bold success output
            System.out.println("\u001B[1m[SUCCESS]\u001B[0m Resume file uploaded, parsed, and initial analysis completed.");
            return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "filename", saved.getFilename(),
                "message", "Resume uploaded and analyzed successfully",
                "resumeAnalysis", resumeAnalysis,
                "extractedText", text,
                "fileSize", fileSize,
                "mimeType", mimeType
            ));
        } catch (Exception e) {
            System.out.println("\u001B[1m[FAIL]\u001B[0m Resume upload failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/analyze/{id}")
    public ResponseEntity<?> analyzeForJob(
            @PathVariable("id") Long resumeId,
            @RequestParam(value = "vacancyUrl", required = false) String vacancyUrl,
            @RequestBody(required = false) Map<String, String> body) {

        // Accept vacancyUrl from query param, JSON body, or form data
        String jobText = null;
        if ((vacancyUrl == null || vacancyUrl.isBlank()) && body != null) {
            Object urlObj = body.get("vacancyUrl");
            if (urlObj instanceof String && !((String) urlObj).isBlank()) {
                vacancyUrl = (String) urlObj;
            }
            Object jobTextObj = body.get("jobText");
            if (jobTextObj instanceof String && !((String) jobTextObj).isBlank()) {
                jobText = (String) jobTextObj;
            }
        }
        if ((vacancyUrl == null || vacancyUrl.isBlank()) && (jobText == null || jobText.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "vacancyUrl or jobText is required"));
        }

        if (resumeId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "resumeId must not be null"));
        }
        Resume resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "resume not found"));
        }

        try {
            // 1. Extract job description from URL or plain text
            Map<String, Object> jobExtraction;
            if (jobText != null && !jobText.isBlank()) {
                jobExtraction = jobDescriptionService.extractJobDescriptionFromText(jobText);
            } else {
                jobExtraction = jobDescriptionService.extractJobDescription(vacancyUrl);
            }
            if (!((Boolean) jobExtraction.getOrDefault("success", false))) {
                return ResponseEntity.ok(jobExtraction); // Return error signal for manual paste
            }

            // Check for security clearance and UK passport requirements
            boolean scRequired = (boolean) jobExtraction.getOrDefault("securityClearanceRequired", false);
            boolean ukRequired = (boolean) jobExtraction.getOrDefault("requiresUKPassport", false);

            if (scRequired || ukRequired) {
                Map<String, Object> warning = new HashMap<>(jobExtraction);
                warning.put("redFlag", true);
                warning.put("redFlagReason", scRequired ? "Security Clearance Required" : "UK Passport Required");
                return ResponseEntity.ok(warning);
            }

            // 2. Compute matching score
            Object techSkillsObj = jobExtraction.get("technicalSkills");
            Object softSkillsObj = jobExtraction.get("softSkills");
            Object mainFunctionsObj = jobExtraction.get("mainFunctions");
            String[] techSkills = techSkillsObj instanceof String[] ? (String[]) techSkillsObj : new String[]{};
            String[] softSkills = softSkillsObj instanceof String[] ? (String[]) softSkillsObj : new String[]{};
            String[] mainFunctions = mainFunctionsObj instanceof String[] ? (String[]) mainFunctionsObj : new String[]{};
            int yearsRequired = getIntFromMap(jobExtraction, "yearsOfExperience");
            String position = (String) jobExtraction.getOrDefault("position", "Unknown");
            String company = (String) jobExtraction.getOrDefault("company", "UnknownCompany");

            Map<String, Object> scoreResult = matchingService.computeScore(
                    resume.getOriginalText(), 
                    jobExtraction.toString(),
                    techSkills, 
                    softSkills, 
                    yearsRequired, 
                    position,
                    mainFunctions
            );

            int score = getIntFromMap(scoreResult, "score");
            Object missingKeywordsObj = scoreResult.get("missingKeywords");
            @SuppressWarnings("unchecked")
            List<String> missingKeywords = missingKeywordsObj instanceof List ? (List<String>) missingKeywordsObj : new ArrayList<>();

                // 3. Generate improvements preview
                Map<String, Object> improvementsResult = improveService.generateImprovements(
                    resume.getOriginalText(),
                    jobExtraction.toString(),
                    missingKeywords,
                    score
                );

                // Save improvements JSON to resume
                String improvementsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(improvementsResult);
                resume.setImprovementsJson(improvementsJson);

                // 4. Save to resume for later tailoring
                resume.setVacancyUrl(vacancyUrl);
                resume.setPosition(position);
                resume.setCompany(company); // Save the company name
                resume.setMatchScore((double) score);
                resumeRepository.save(resume);

                // 5. Return combined analysis
                Map<String, Object> response = new HashMap<>();
                response.put("resumeId", resume.getId());
                response.put("jobExtraction", jobExtraction);
                response.put("matchingScore", scoreResult);
                response.put("improvements", improvementsResult);
                response.put("ready_for_tailoring", true);

                // Print only jobExtraction success and actionable improvements to terminal
                boolean extractionSuccess = (Boolean) jobExtraction.getOrDefault("success", false);
                System.out.println("\n==============================");
                System.out.println("[ANALYZE RESULT]");
                System.out.println("Job Extraction Success: " + extractionSuccess);
                // Print actionable improvements for tailoring
                System.out.println("LLM Improvements for Tailoring:");
                if (improvementsResult != null) {
                    System.out.println("skillsAndAbilitiesToAdd: " + improvementsResult.getOrDefault("skillsAndAbilitiesToAdd", "[]"));
                    System.out.println("bulletPointSuggestions: " + improvementsResult.getOrDefault("bulletPointSuggestions", "[]"));
                    System.out.println("personalSummarySuggestions: " + improvementsResult.getOrDefault("personalSummarySuggestions", "[]"));
                    System.out.println("selectedKeywords: " + improvementsResult.getOrDefault("selectedKeywords", "[]"));
                }
                System.out.println("==============================\n");
                System.out.println("\u001B[1m[SUCCESS]\u001B[0m Job post analyzed successfully.");
                return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/tailor/{id}")
    public ResponseEntity<?> tailorResume(@PathVariable("id") Long resumeId) {
        if (resumeId == null) {
            System.out.println("\u001B[1m[FAIL]\u001B[0m Tailoring failed: resumeId must not be null");
            return ResponseEntity.badRequest().body(Map.of("error", "resumeId must not be null"));
        }
        Resume resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            System.out.println("\u001B[1m[FAIL]\u001B[0m Tailoring failed: resume not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "resume not found"));
        }
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<ResponseEntity<?>> future = executor.submit(() -> {
                try {
                    System.out.println("[IN-PROGRESS] Starting resume tailoring...");
                    // --- Begin tailoring logic ---
                    List<String> selectedKeywords = new ArrayList<>();
                    String jobText = resume.getVacancyUrl() != null ? resume.getVacancyUrl() : "No job description";
                    List<String> skillsAndAbilitiesToAdd = new ArrayList<>();
                    List<String> bulletPointSuggestions = new ArrayList<>();
                    List<String> personalSummarySuggestions = new ArrayList<>();
                    try {
                        String improvementsJson = resume.getImprovementsJson();
                        if (improvementsJson != null && !improvementsJson.isBlank()) {
                            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(improvementsJson);
                            if (node.has("selectedKeywords")) {
                                node.get("selectedKeywords").forEach(n -> selectedKeywords.add(n.asText()));
                            }
                            if (node.has("skillsAndAbilitiesToAdd")) {
                                node.get("skillsAndAbilitiesToAdd").forEach(n -> skillsAndAbilitiesToAdd.add(n.asText()));
                            }
                            if (node.has("bulletPointSuggestions")) {
                                node.get("bulletPointSuggestions").forEach(n -> bulletPointSuggestions.add(n.asText()));
                            }
                            if (node.has("personalSummarySuggestions")) {
                                node.get("personalSummarySuggestions").forEach(n -> personalSummarySuggestions.add(n.asText()));
                            }
                        }
                    } catch (Exception e) {
                        // If improvementsJson is missing or malformed, fallback to empty lists
                    }
                    // Debug log improvements lists
                    System.out.println("[DEBUG] skillsAndAbilitiesToAdd: " + skillsAndAbilitiesToAdd);
                    System.out.println("[DEBUG] bulletPointSuggestions: " + bulletPointSuggestions);
                    System.out.println("[DEBUG] personalSummarySuggestions: " + personalSummarySuggestions);
                    System.out.println("[DEBUG] selectedKeywords: " + selectedKeywords);

                    System.out.println("[IN-PROGRESS] Generating AI edit plan...");
                    // Generate an edit plan from the AI
                    com.fasterxml.jackson.databind.JsonNode editPlan = improveService.generateEditPlan(
                        resume.getOriginalText(),
                        jobText,
                        skillsAndAbilitiesToAdd,
                        bulletPointSuggestions,
                        personalSummarySuggestions
                    );
                    System.out.println("[IN-PROGRESS] AI edit plan generation complete.");
                    System.out.println("[DEBUG] Generated Edit Plan: " + editPlan.toPrettyString());


                    // --- New Folder and Metadata Logic ---
                    System.out.println("[IN-PROGRESS] Creating application-specific folder and metadata...");
                    String company = resume.getCompany() != null ? resume.getCompany().replaceAll("[^a-zA-Z0-9.-]", "_") : "UnknownCompany";
                    String position = resume.getPosition() != null ? resume.getPosition().replaceAll("[^a-zA-Z0-9.-]", "_") : "UnknownPosition";
                    String folderName = company + "_" + position;
                    java.nio.file.Path appFolder = java.nio.file.Paths.get(System.getProperty("user.home"), "Documents", "JA", folderName);
                    java.nio.file.Files.createDirectories(appFolder);

                    // Create metadata file with expanded details
                    String metadataContent = "Company: " + (resume.getCompany() != null ? resume.getCompany() : "N/A") + "\n" +
                                             "Role: " + (resume.getPosition() != null ? resume.getPosition() : "N/A") + "\n" +
                                             "Applied Date: " + java.time.format.DateTimeFormatter.ISO_DATE.format(java.time.LocalDate.now()) + "\n" +
                                             "State: waiting for response\n" +
                                             "Contact: \n" + // Leave blank for user to fill
                                             "Vacancy URL: " + (resume.getVacancyUrl() != null ? resume.getVacancyUrl() : "N/A");
                    java.nio.file.Path metadataPath = appFolder.resolve("metadata.txt");
                    java.nio.file.Files.writeString(metadataPath, metadataContent);
                    System.out.println("[IN-PROGRESS] Application folder and metadata.txt created at: " + appFolder);
                    // --- End New Folder Logic ---

                    System.out.println("[IN-PROGRESS] Updating DOCX file based on the edit plan...");
                    // Update the DOCX file, saving it in the new application-specific folder
                    java.nio.file.Path tailoredPath = docxService.updateDocx(
                        resume.getFilename(),
                        editPlan, // Pass the edit plan
                        appFolder // Pass the new folder path
                    );
                    System.out.println("[IN-PROGRESS] DOCX file update complete.");

                    System.out.println("[IN-PROGRESS] Saving tailored resume path...");
                    resume.setTailoredResumePath(tailoredPath.toString());
                    resumeRepository.save(resume);
                    System.out.println("[IN-PROGRESS] Tailored resume path saved.");
                    // --- End tailoring logic ---
                    System.out.println("\u001B[1m[SUCCESS]\u001B[0m Resume tailored successfully.");
                    return ResponseEntity.ok(Map.of("message", "Resume tailored successfully", "editPlan", editPlan));
                } catch (Exception e) {
                    System.out.println("\u001B[1m[FAIL]\u001B[0m Resume tailoring failed: " + e.getMessage());
                    e.printStackTrace();
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Resume tailoring failed: " + e.getMessage());
                }
            });

            try {
                return future.get(2, java.util.concurrent.TimeUnit.MINUTES); // Increased timeout to 2 minutes
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                System.out.println("\u001B[1m[FAIL]\u001B[0m Resume tailoring failed: process exceeded 2 minute timeout.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Resume tailoring failed: process exceeded 2 minute timeout.");
            } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                future.cancel(true);
                System.out.println("\u001B[1m[FAIL]\u001B[0m Resume tailoring failed: " + e.getMessage());
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Resume tailoring failed: " + e.getMessage());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approveAndSave(
            @PathVariable("id") Long resumeId,
            @RequestBody Map<String, Object> request) {

        if (resumeId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "resumeId must not be null"));
        }
        Resume resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "resume not found"));
        }

        try {
            String company = (String) request.getOrDefault("company", resume.getCompany() != null ? resume.getCompany() : "Unknown");
            String position = (String) request.getOrDefault("position", resume.getPosition() != null ? resume.getPosition() : "Unknown");
            String docxBase64Str = request.getOrDefault("docxBase64", "").toString();
            byte[] docxBytes = docxBase64Str.isEmpty() ? new byte[]{} : Base64.getDecoder().decode(docxBase64Str);
            Object improvementsObj = request.get("improvements");
            @SuppressWarnings("unchecked")
            Map<String, Object> improvements = improvementsObj instanceof Map ? (Map<String, Object>) improvementsObj : new HashMap<>();

            // Save to folder (pass both original and tailored)
            Map<String, Object> storageResult = storageService.saveApprovedResume(
                    company,
                    position,
                    resume.getTailoredText() != null ? resume.getTailoredText() : resume.getOriginalText(),
                    docxBytes,
                    resume.getVacancyUrl() != null ? resume.getVacancyUrl() : "",
                    resume.getMatchScore() != null ? resume.getMatchScore().intValue() : 0,
                    improvements,
                    resume.getOriginalText()
            );

            resume.setApplied(true);
            resumeRepository.save(resume);

            System.out.println("\u001B[1m[SUCCESS]\u001B[0m Resume approved and saved successfully.");

            return ResponseEntity.ok(storageResult);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getResume(@PathVariable("id") Long resumeId) {
        if (resumeId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "resumeId must not be null"));
        }
        Resume resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "resume not found"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", resume.getId());
        response.put("filename", resume.getFilename());
        response.put("matchScore", resume.getMatchScore());
        response.put("vacancyUrl", resume.getVacancyUrl());
        response.put("company", resume.getCompany());
        response.put("position", resume.getPosition());
        response.put("applied", resume.isApplied());

        return ResponseEntity.ok(response);
    }
}
