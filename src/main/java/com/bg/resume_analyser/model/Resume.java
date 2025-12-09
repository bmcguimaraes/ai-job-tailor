package com.bg.resume_analyser.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "resumes")
public class Resume {
    @Lob
    private String improvementsJson;

    public String getImprovementsJson() {
        return improvementsJson;
    }

    public void setImprovementsJson(String improvementsJson) {
        this.improvementsJson = improvementsJson;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;

    @Lob
    private String originalText;

    @Lob
    private String tailoredText;

    private Double matchScore;

    private String vacancyUrl;

    private String company;

    private String position;

    private boolean applied = false;

    private String tailoredResumePath;

    public Resume() {
    }

    public Resume(String filename, String originalText) {
        this.filename = filename;
        this.originalText = originalText;
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

    public String getTailoredResumePath() {
        return tailoredResumePath;
    }

    public void setTailoredResumePath(String tailoredResumePath) {
        this.tailoredResumePath = tailoredResumePath;
    }
}
