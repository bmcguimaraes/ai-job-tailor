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

        try {
            String text = tika.parseToString(file.getInputStream());
            Resume r = new Resume(file.getOriginalFilename(), text);
            Resume saved = resumeRepository.save(r);
            return ResponseEntity.ok(Map.of("id", saved.getId(), "filename", saved.getFilename(), "message", "Resume uploaded successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/analyze/{id}")
    public ResponseEntity<?> analyzeForJob(
            @PathVariable("id") Long resumeId,
            @RequestParam(value = "vacancyUrl", required = false) String vacancyUrl,
            @RequestBody(required = false) Map<String, String> body) {

        if (vacancyUrl == null || vacancyUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "vacancyUrl is required"));
        }

        Resume resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "resume not found"));
        }

        try {
            // 1. Extract job description from URL
            Map<String, Object> jobExtraction = jobDescriptionService.extractJobDescription(vacancyUrl);
            
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
            int yearsRequired = (int) jobExtraction.getOrDefault("yearsOfExperience", 0);
            String position = (String) jobExtraction.getOrDefault("position", "Unknown");

            Map<String, Object> scoreResult = matchingService.computeScore(
                    resume.getOriginalText(), 
                    jobExtraction.toString(),
                    techSkills, 
                    softSkills, 
                    yearsRequired, 
                    position,
                    mainFunctions
            );

            int score = (int) scoreResult.getOrDefault("score", 0);
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

            // 4. Save to resume for later tailoring
            resume.setVacancyUrl(vacancyUrl);
            resume.setCompany((String) jobExtraction.getOrDefault("company", "Unknown"));
            resume.setPosition(position);
            resume.setMatchScore((double) score);
            resumeRepository.save(resume);

            // 5. Return combined analysis
            Map<String, Object> response = new HashMap<>();
            response.put("resumeId", resume.getId());
            response.put("jobExtraction", jobExtraction);
            response.put("matchingScore", scoreResult);
            response.put("improvements", improvementsResult);
            response.put("ready_for_tailoring", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/tailor/{id}")
    public ResponseEntity<?> tailorResume(
            @PathVariable("id") Long resumeId,
            @RequestBody Map<String, Object> request) {

        Resume resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "resume not found"));
        }

        try {
            Object selectedKeywordsObj = request.get("selectedKeywords");
            @SuppressWarnings("unchecked")
            List<String> selectedKeywords = selectedKeywordsObj instanceof List ? (List<String>) selectedKeywordsObj : new ArrayList<>();
            int maxDeviation = (int) request.getOrDefault("maxDeviationPercent", 40);

            String jobText = resume.getVacancyUrl() != null ? resume.getVacancyUrl() : "No job description";

            // Generate tailored resume
            Map<String, Object> tailoringResult = improveService.generateTailoredResume(
                    resume.getOriginalText(),
                    jobText,
                    selectedKeywords,
                    maxDeviation
            );

            String tailoredText = (String) tailoringResult.getOrDefault("tailoredText", resume.getOriginalText());

            // Generate DOCX
            byte[] docxBytes = docxService.generateDocx(tailoredText);

            // Save tailored version
            resume.setTailoredText(tailoredText);
            resumeRepository.save(resume);

            Map<String, Object> response = new HashMap<>();
            response.put("resumeId", resume.getId());
            response.put("tailoredText", tailoredText);
            response.put("deviationPercent", tailoringResult.getOrDefault("deviationPercent", 0));
            response.put("docxBase64", Base64.getEncoder().encodeToString(docxBytes));
            response.put("docxFileName", "tailored_resume.docx");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approveAndSave(
            @PathVariable("id") Long resumeId,
            @RequestBody Map<String, Object> request) {

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

            return ResponseEntity.ok(storageResult);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getResume(@PathVariable("id") Long resumeId) {
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
