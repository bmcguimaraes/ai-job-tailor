# AI Resume Tailor - MVP Backend Demo Guide

## Overview
This is a Spring Boot application that analyzes your resume against job postings, scores the match, suggests improvements, generates a tailored resume, and saves it locally for your review.

## Architecture
- **JobDescriptionService**: Scrapes and extracts job posting details (position, salary, skills, experience level) from URLs
- **MatchingService**: Computes a 0-100 match score using embeddings + keyword matching with weighted criteria:
  - Soft Skills: 20%
  - Technical Skills: 30%
  - Job Role & Experience: 40%
  - Keywords: 10%
- **ImproveService**: Generates improvement suggestions and tailors resume (up to 40% modification)
- **DocxService**: Generates DOCX files from resume text
- **StorageService**: Saves approved resumes to `/Users/brunoguimaraes/Documents/JA/{company_position}/`

## Prerequisites
1. **OpenAI API Key**: Set environment variable or add to `application.properties`
   ```bash
   export OPENAI_API_KEY="sk-..."
   ```

2. **Java 17+**: Project uses Spring Boot 3.4.12

3. **Dependencies**: Maven will download Jsoup, Apache POI, Apache Tika automatically

## Running the Application

### Start the Spring Boot app
```bash
cd /Users/brunoguimaraes/Repo/ai-job-tailor

# Option 1: Using maven wrapper (if available)
./mvnw spring-boot:run

# Option 2: Using system maven (if installed)
mvn spring-boot:run

# App will start on http://localhost:8080
```

## API Endpoints

### 1. Upload Resume (PDF)
**POST** `/api/resumes/upload`

Request (multipart form):
```bash
curl -X POST http://localhost:8080/api/resumes/upload \
  -F "file=@/path/to/your/resume.pdf" \
  -H "Accept: application/json"
```

Response:
```json
{
  "id": 1,
  "filename": "resume.pdf",
  "message": "Resume uploaded successfully"
}
```

Save the `id` — you'll need it for next steps.

---

### 2. Analyze Resume Against Job Posting
**POST** `/api/resumes/analyze/{id}?vacancyUrl={url}`

Scrapes the job posting URL, extracts job details, computes match score, generates improvement suggestions.

Request:
```bash
curl -X POST "http://localhost:8080/api/resumes/analyze/1?vacancyUrl=https://www.linkedin.com/jobs/view/1234567890/" \
  -H "Content-Type: application/json"
```

Response:
```json
{
  "resumeId": 1,
  "jobExtraction": {
    "success": true,
    "url": "https://www.linkedin.com/jobs/...",
    "position": "Senior Software Engineer",
    "salary": "$150,000 - $200,000",
    "yearsOfExperience": 5,
    "technicalSkills": ["Java", "Spring Boot", "AWS", "PostgreSQL"],
    "softSkills": ["Leadership", "Communication"],
    "postingDate": "2025-12-04",
    "securityClearanceRequired": false,
    "requiresUKPassport": false,
    "redFlag": false  // Only if SC or UK required — then DO NOT APPLY
  },
  "matchingScore": {
    "score": 82,
    "fit": "Good match",
    "details": {
      "embeddingSimilarity": 78,
      "softSkillsScore": 75,
      "technicalSkillsScore": 88,
      "roleExperienceScore": 85,
      "keywordScore": 70
    },
    "missingKeywords": ["microservices", "docker"],
    "topKeywords": ["Java", "Spring Boot", "REST", "Microservices", "Docker"]
  },
  "improvements": {
    "improvements": [
      {
        "improvement": "Add Docker and containerization experience",
        "section": "Technical Skills",
        "impactScoreIncrease": 3,
        "keywordsAdded": ["Docker", "Kubernetes"],
        "why": "Required for the role"
      },
      ...6 improvements total
    ],
    "projectedScore": 91,
    "selectedKeywords": []
  },
  "ready_for_tailoring": true
}
```

**Important**: If `redFlag` is `true`, **DO NOT PROCEED** — the job requires SC or UK passport, which disqualifies you.

---

### 3. Review Improvements and Select Keywords
From the response above, the improvements array shows up to 6 suggestions. You can manually select 2-3 keywords to add to your tailored resume.

Example selected keywords for next step: `["Docker", "Kubernetes", "Microservices"]`

---

### 4. Generate Tailored Resume (DOCX)
**POST** `/api/resumes/tailor/{id}`

Generates a tailored resume using the selected keywords, returns as DOCX (base64-encoded).

Request:
```bash
curl -X POST http://localhost:8080/api/resumes/tailor/1 \
  -H "Content-Type: application/json" \
  -d '{
    "selectedKeywords": ["Docker", "Kubernetes", "Microservices"],
    "maxDeviationPercent": 40
  }'
```

Response:
```json
{
  "resumeId": 1,
  "tailoredText": "...full tailored resume text...",
  "deviationPercent": 25,
  "docxBase64": "UEsDBBQABgAIAAAAIQ...(long base64 string)...==",
  "docxFileName": "tailored_resume.docx"
}
```

**Decode DOCX**:
```bash
# Save DOCX file
echo "UEsDBBQABgAIAAAAIQ...(paste the docxBase64 value here)...==" | base64 -d > tailored_resume.docx

# Open in Word or Google Docs for review
open tailored_resume.docx
```

---

### 5. Review & Approve
Open the DOCX in Microsoft Word or Google Docs. Make any final edits you want. The resume has been tailored to match the job posting.

Once approved, proceed to save it.

---

### 6. Save Approved Resume to JA Folder
**POST** `/api/resumes/approve/{id}`

Saves the tailored resume + metadata (job link, date, score, improvements) to `/Users/brunoguimaraes/Documents/JA/{company}_{position}/`

Request:
```bash
curl -X POST http://localhost:8080/api/resumes/approve/1 \
  -H "Content-Type: application/json" \
  -d '{
    "company": "Google",
    "position": "Senior Software Engineer",
    "docxBase64": "UEsDBBQABgAIAAAAIQ...(paste the docxBase64 from tailor response)...==",
    "improvements": {
      "selectedKeywords": ["Docker", "Kubernetes"],
      "projectedScore": 91
    }
  }'
```

Response:
```json
{
  "success": true,
  "folderPath": "/Users/brunoguimaraes/Documents/JA/Google_Senior_Software_Engineer",
  "resumeFile": "tailored_resume_2025-12-04.docx",
  "metadataFile": "metadata.txt",
  "message": "Resume and metadata saved successfully"
}
```

Check your folder:
```bash
open /Users/brunoguimaraes/Documents/JA/Google_Senior_Software_Engineer/
```

You'll find:
- `tailored_resume_2025-12-04.docx` — your tailored resume
- `tailored_resume_2025-12-04.txt` — plain text version
- `metadata.txt` — vacancy URL, date applied, score, improvements

---

## Complete End-to-End Curl Example

```bash
#!/bin/bash

# Set your OpenAI API key
export OPENAI_API_KEY="sk-..."

# 1. Upload resume
RESUME_RESPONSE=$(curl -s -X POST http://localhost:8080/api/resumes/upload \
  -F "file=@~/my_resume.pdf")

RESUME_ID=$(echo $RESUME_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "Uploaded resume with ID: $RESUME_ID"

# 2. Analyze against job posting
JOB_URL="https://www.linkedin.com/jobs/view/1234567890/"
ANALYSIS=$(curl -s -X POST "http://localhost:8080/api/resumes/analyze/$RESUME_ID?vacancyUrl=$JOB_URL")

# Check for red flags
RED_FLAG=$(echo $ANALYSIS | grep -o '"redFlag":true' || echo "false")
if [[ $RED_FLAG == *"true"* ]]; then
  echo "⚠️ RED FLAG: Security clearance or UK passport required. SKIP THIS JOB."
  exit 0
fi

SCORE=$(echo $ANALYSIS | grep -o '"score":[0-9]*' | cut -d: -f2)
echo "Match score: $SCORE/100"

# 3. Generate tailored resume
TAILOR_RESPONSE=$(curl -s -X POST http://localhost:8080/api/resumes/tailor/$RESUME_ID \
  -H "Content-Type: application/json" \
  -d '{
    "selectedKeywords": ["Docker", "Kubernetes"],
    "maxDeviationPercent": 40
  }')

# Extract base64 DOCX
DOCX_BASE64=$(echo $TAILOR_RESPONSE | grep -o '"docxBase64":"[^"]*' | cut -d: -f2 | tr -d '"')

# Save DOCX
echo $DOCX_BASE64 | base64 -d > ~/tailored_resume.docx
echo "Saved tailored resume to ~/tailored_resume.docx"

# 4. Approve and save
APPROVE_RESPONSE=$(curl -s -X POST http://localhost:8080/api/resumes/approve/$RESUME_ID \
  -H "Content-Type: application/json" \
  -d '{
    "company": "Google",
    "position": "Senior Software Engineer",
    "docxBase64": "'$DOCX_BASE64'",
    "improvements": {
      "selectedKeywords": ["Docker", "Kubernetes"],
      "projectedScore": 91
    }
  }')

FOLDER_PATH=$(echo $APPROVE_RESPONSE | grep -o '"folderPath":"[^"]*' | cut -d: -f2- | tr -d '"')
echo "Saved to: $FOLDER_PATH"
```

---

## Scoring Criteria

| Criterion | Weight | Description |
|-----------|--------|-------------|
| Soft Skills | 20% | Communication, leadership, collaboration |
| Technical Skills | 30% | Programming languages, frameworks, tools |
| Job Role & Experience | 40% | Match of position and years of experience |
| Top Keywords | 10% | Key terms from job posting found in resume |

**Score Interpretation**:
- **75+**: Good match — proceed with tailoring
- **50-74**: Moderate match — may need more work
- **<50**: Poor match — consider if worth the effort

---

## Error Handling

### Scraping Failed
If the job posting URL cannot be scraped:
```json
{
  "success": false,
  "message": "Could not extract job description. Please paste manually.",
  "url": "https://..."
}
```

**Solution**: Copy the job description text, POST it manually or wait for the manual paste endpoint (future feature).

### Red Flags
If the job requires SC or UK passport:
```json
{
  "redFlag": true,
  "redFlagReason": "Security Clearance Required"
}
```

**Solution**: DO NOT APPLY. Skip this job.

### API Key Missing
```json
{
  "success": false,
  "score": 0,
  "message": "OpenAI API key not configured..."
}
```

**Solution**: Set `OPENAI_API_KEY` environment variable or add to `application.properties`.

---

## Next Steps (UI)

The backend is complete. Next we'll build a minimal React UI that:
1. Displays upload form for PDF resume (one-time)
2. Accepts job posting URL input
3. Shows extracted job details, match score, and improvements
4. Lets you select keywords and generate DOCX
5. Preview and approve before saving to JA folder

---

## Testing with Your Resume

1. Save your resume as PDF: `~/my_resume.pdf`
2. Follow the end-to-end curl example above
3. Replace job URL with a real LinkedIn/Indeed/company job posting
4. Check the output folder: `/Users/brunoguimaraes/Documents/JA/`

---

## Troubleshooting

**"Cannot connect to localhost:8080"**
- Ensure Spring Boot app is running: `mvn spring-boot:run`
- Check for port conflicts: `lsof -i :8080`

**"API key error"**
- Verify API key: `echo $OPENAI_API_KEY`
- Test with a real key from OpenAI dashboard

**"Resume not found (404)"**
- Ensure you uploaded first and saved the ID
- Double-check the ID in the URL

**"DOCX generation failed"**
- Check tailored resume text is not empty
- Ensure Apache POI is installed (Maven should handle it)

---

## Production Considerations

- Switch H2 to PostgreSQL for persistence
- Add authentication and rate limiting
- Implement PDF generation (docx→PDF conversion)
- Add logging and error tracking
- Deploy to cloud (Heroku, Azure, AWS)
- Implement UI (React, Vue, or simple HTML)

---

Good luck with your applications! 🚀
