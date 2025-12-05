package com.bg.resume_analyser.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "resumes")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;

    @Lob
    private String originalText;

    @Lob
    private String tailoredText;

    private Double matchScore;

    private LocalDateTime lastUploadedDate;

    private String vacancyUrl;

    private String company;

    private String position;

    private boolean applied = false;

    public Resume() {
    }

    public Resume(String filename, String originalText) {
        this.filename = filename;
        this.originalText = originalText;
        this.lastUploadedDate = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getTailoredText() {
        return tailoredText;
    }

    public void setTailoredText(String tailoredText) {
        this.tailoredText = tailoredText;
    }

    public Double getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Double matchScore) {
        this.matchScore = matchScore;
    }

    public String getVacancyUrl() {
        return vacancyUrl;
    }

    public void setVacancyUrl(String vacancyUrl) {
        this.vacancyUrl = vacancyUrl;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public boolean isApplied() {
        return applied;
    }

    public void setApplied(boolean applied) {
        this.applied = applied;
    }

    public LocalDateTime getLastUploadedDate() {
        return lastUploadedDate;
    }

    public void setLastUploadedDate(LocalDateTime lastUploadedDate) {
        this.lastUploadedDate = lastUploadedDate;
    }
}
