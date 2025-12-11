package com.bg.resume_analyser.controller;

import com.bg.resume_analyser.model.Resume;
import com.bg.resume_analyser.model.request.TailorRequest;
import com.bg.resume_analyser.repository.ResumeRepository;
import com.bg.resume_analyser.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeRepository resumeRepository;
    private final Tika tika = new Tika();
    private final ImproveService improveService;
    private final DocxService docxService;
    private final StorageService storageService;
    private final JobDescriptionService jobDescriptionService;

    @Autowired
    public ResumeController(ResumeRepository resumeRepository,
                           ImproveService improveService,
                           DocxService docxService,
                           StorageService storageService,
                           JobDescriptionService jobDescriptionService) {
        this.resumeRepository = resumeRepository;
        this.improveService = improveService;
        this.docxService = docxService;
        this.storageService = storageService;
        this.jobDescriptionService = jobDescriptionService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "file is required"));
        }

        String filename = file.getOriginalFilename();
        String text;

        try (var inputStream = file.getInputStream()) {
            text = tika.parseToString(inputStream);
        } catch (Exception e) {
            System.err.println("[FAIL] Resume parsing failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to parse file content: " + e.getMessage()));
        }

        if (filename != null && filename.toLowerCase().endsWith(".docx")) {
            try {
                Path jaFolder = java.nio.file.Paths.get(System.getProperty("user.home"), "Documents", "JA");
                java.nio.file.Files.createDirectories(jaFolder);
                Path destPath = jaFolder.resolve(filename);
                // We need a new InputStream because the first one was consumed by Tika
                try (var inputStream = file.getInputStream()) {
                    java.nio.file.Files.copy(inputStream, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println("[ResumeController] DOCX file saved to: " + destPath);
            } catch (IOException e) {
                System.err.println("[FAIL] Could not save DOCX template: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Could not save DOCX file."));
            }
        }

        try {
            Resume r = new Resume(filename, text);
            Resume saved = resumeRepository.save(r);
            System.out.println("\u001B[1m[SUCCESS]\u001B[0m Resume file uploaded and parsed.");
            return ResponseEntity.ok(Map.of("id", saved.getId(), "filename", saved.getFilename()));
        } catch (Exception e) {
            System.err.println("[FAIL] Resume database save failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/tailor/{id}")
    public ResponseEntity<?> tailorResume(@PathVariable Long id, @RequestBody TailorRequest tailorRequest) {
        System.out.println("[IN-PROGRESS] Starting resume tailoring...");
        Optional<Resume> optionalResume = resumeRepository.findById(java.util.Objects.requireNonNull(id));
        if (optionalResume.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Resume not found with ID: " + id));
        }
        Resume resume = optionalResume.get();

        try {
            String jobText;
            String vacancyUrl = tailorRequest.getVacancyUrl();
            String jobDescription = tailorRequest.getJobDescription();

            if (vacancyUrl != null && !vacancyUrl.isBlank()) {
                System.out.println("[IN-PROGRESS] Fetching job description from URL: " + vacancyUrl);
                jobText = jobDescriptionService.getJobDescriptionFromUrl(vacancyUrl);
                resume.setVacancyUrl(vacancyUrl);
            } else if (jobDescription != null && !jobDescription.isBlank()) {
                System.out.println("[IN-PROGRESS] Using provided job description text.");
                jobText = jobDescription;
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Either vacancyUrl or jobDescription is required."));
            }

            System.out.println("[IN-PROGRESS] Generating AI edit plan...");
            Map<String, Object> aiResult = improveService.generateEditPlan(resume.getOriginalText(), jobText);
            String companyName = (String) aiResult.get("company_name");
            String positionTitle = (String) aiResult.get("position_title");
            String contactPerson = (String) aiResult.get("contact_person");
            JsonNode editPlan = (JsonNode) aiResult.get("edit_plan");
            JsonNode skillsToAdd = (JsonNode) aiResult.get("skills_to_add");

            System.out.println("[DEBUG] Extracted Company: " + companyName);
            System.out.println("[DEBUG] Extracted Position: " + positionTitle);
            System.out.println("[DEBUG] Extracted Contact: " + contactPerson);
            System.out.println("[DEBUG] Generated Edit Plan: " + (editPlan != null ? editPlan.toString() : "[]"));
            System.out.println("[DEBUG] Generated Skills to Add: " + (skillsToAdd != null ? skillsToAdd.toString() : "{}"));

            System.out.println("[IN-PROGRESS] Creating application-specific folder and metadata...");
            Path appFolder = storageService.createApplicationFolder(companyName, positionTitle);
            storageService.writeMetadata(appFolder, companyName, positionTitle, contactPerson, resume.getVacancyUrl(), editPlan, skillsToAdd);
            System.out.println("[IN-PROGRESS] Application folder and metadata.txt created at: " + appFolder);

            System.out.println("[IN-PROGRESS] Updating DOCX file based on the edit plan...");
            Path tailoredDocxPath = docxService.updateDocx(resume.getFilename(), editPlan, skillsToAdd, appFolder);
            System.out.println("[IN-PROGRESS] DOCX file update complete.");

            System.out.println("[IN-PROGRESS] Saving tailored resume path...");
            resume.setTailoredPath(tailoredDocxPath.toString());
            resumeRepository.save(resume);
            System.out.println("[IN-PROGRESS] Tailored resume path saved.");

            System.out.println("\u001B[1m[SUCCESS]\u001B[0m Resume tailored successfully.");
            return ResponseEntity.ok(Map.of(
                    "message", "Resume tailored successfully",
                    "tailoredPath", tailoredDocxPath.toString()
            ));
        } catch (IOException e) {
            System.err.println("[FAIL] Error during tailoring: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Failed to process request. " + e.getMessage()));
        } catch (Exception e) {
            System.err.println("[FAIL] Resume tailoring failed unexpectedly: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
}
