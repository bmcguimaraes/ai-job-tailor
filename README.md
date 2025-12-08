# AI Job Tailor - Resume Analyzer & Tailoring System

## Project Status
✅ **Backend Implementation Complete** (MVP ready for testing)
⏳ **UI Implementation**: Next phase (React, simple HTML, or Postman-based testing)

---

## What We Built


### Core Features
1. **Resume Upload**: Extract text from PDF resumes using Apache Tika
2. **Job Posting Analysis**: Scrape job postings and extract key details (position, salary, skills, experience level, posting date)
3. **Intelligent Matching**: 
    - Embeddings-based semantic similarity (OpenAI)
    - Keyword matching on required skills
    - Weighted scoring: Soft Skills (20%), Technical (30%), Role/Experience (40%), Keywords (10%)
    - **Note:** The matching score is computed by the backend using embeddings and field matching logic, not directly by an LLM. The LLM is used for extraction, not for scoring.
    - Flags red flags: Security Clearance or UK Passport requirements
    - ⚠️ **Matching system may need readjustment/tuning for better accuracy.**
4. **Improvement Suggestions**: AI-generated suggestions (up to 6) for tailoring your resume
5. **Resume Tailoring**: Generate a tailored version using selected keywords (max 40% modification)
6. **DOCX Output**: Download tailored resume in Microsoft Word format for review
7. **Local Storage**: Save tailored resumes to `/Users/brunoguimaraes/Documents/JA/{company_position}/` as DOCX and metadata only
    - Folder name is always `{company}_{position}` (sanitized)
    - Tailored resume DOCX file is named `{YourName}_{position}.docx` (e.g., `BrunoGuimaraes_software_dev.docx`)
    - ⚠️ End goal: Output tailored resume as PDF (not DOCX)

---

## Architecture

```
ResumeController (HTTP endpoints)
    ↓
JobDescriptionService (Web scraping + LLM parsing)
    ↓
MatchingService (Score computation: embeddings + keywords)
    ↓
ImproveService (Improvement suggestions + resume tailoring)
    ↓
DocxService (DOCX generation)
    ↓
StorageService (File save to JA folder)
    ↓
Resume Entity (H2 Database)
```

---

## Tech Stack
- **Backend**: Spring Boot 3.4.12, Java 17
- **NLP/AI**: OpenAI API (gpt-3.5-turbo, text-embedding-3-small)
- **Web Scraping**: Jsoup 1.15.3
- **Document Generation**: Apache POI 5.2.3
- **Text Extraction**: Apache Tika 2.9.0
- **Database**: H2 (prototype), PostgreSQL ready
- **Build**: Maven

---

## API Endpoints

### 1. Upload Resume
```
POST /api/resumes/upload
Input: multipart/form-data (PDF file)

Output: { id, filename, message }

```

### 2. Analyze Resume Against Job
```
POST /api/resumes/analyze/{id}?vacancyUrl={url}

Input: Job posting URL

```
### 3. Generate Tailored Resume
```
POST /api/resumes/tailor/{id}
Input: { selectedKeywords: [...], maxDeviationPercent: 40 }
Output: Tailored text + DOCX (base64-encoded)
```



### 4. Save Approved Resume
```
POST /api/resumes/approve/{id}
Input: { company, position, docxBase64, improvements }
Output: Folder path + saved files

```

```
```

---

## Scoring Formula


```

Final Score = (
    0.20 * softSkillsMatch +
    0.30 * technicalSkillsMatch +
    0.40 * roleExperienceMatch +
    0.10 * keywordMatch
) * 100

Score ≥ 75: "Good match"
Score 50-74: "Moderate match"

Score < 50: "Poor match"


- ⛔ Job requires **UK Passport**

When flagged, the system advises NOT to proceed as you don't meet the eligibility criteria.

---

## File Structure
```
ai-job-tailor/
├── src/main/java/com/bg/resume_analyser/
│   ├── controller/
│   │   └── ResumeController.java      # HTTP endpoints
│   ├── service/
│   │   ├── JobDescriptionService.java # Job posting scraping & parsing
│   │   ├── MatchingService.java       # Score computation
│   │   ├── ImproveService.java        # Suggestions & tailoring
│   │   ├── DocxService.java           # DOCX generation
│   │   └── StorageService.java        # File persistence
│   ├── model/
│   │   └── Resume.java                # JPA entity
│   ├── repository/
│   │   └── ResumeRepository.java      # Spring Data repository
│   └── ResumeAnalyserApplication.java # Spring Boot entry
├── src/main/resources/
│   └── application.properties         # Config (OpenAI key)
├── pom.xml                            # Maven dependencies
├── DEMO_GUIDE.md                      # Complete API demo guide
└── README.md                          # This file
```

---

## Getting Started

### Prerequisites
- Java 17+
- Maven
- OpenAI API key (set as `OPENAI_API_KEY` env var or in `application.properties`)

### Run Application
```bash
cd ai-job-tailor

# Start Spring Boot app
mvn spring-boot:run

# App runs on http://localhost:8080
```

### Quick Test
See **DEMO_GUIDE.md** for complete curl examples and end-to-end workflow.

Brief example:
```bash

# 1. Upload resume
curl -F "file=@resume.pdf" http://localhost:8080/api/resumes/upload

# 2. Analyze against job (any of these work):
# As query parameter:
curl -X POST "http://localhost:8080/api/resumes/analyze/1?vacancyUrl=https://..."
# As JSON body:
curl -X POST "http://localhost:8080/api/resumes/analyze/1" \
    -H "Content-Type: application/json" \
    -d '{"vacancyUrl":"https://..."}'
# As form data:
curl -X POST "http://localhost:8080/api/resumes/analyze/1" \
    -F "vacancyUrl=https://..."

# 3-4. Generate and save tailored resume (see DEMO_GUIDE.md for details)
```

---

## Configuration

### application.properties
```properties
spring.application.name=resume-analyser
spring.ai.openai.api-key=${OPENAI_API_KEY:}

# H2 (default)
spring.datasource.url=jdbc:h2:mem:testdb
spring.h2.console.enabled=true
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect

# Optional: Switch to PostgreSQL for production
# spring.datasource.url=jdbc:postgresql://localhost:5432/resume_db
# spring.datasource.username=user
# spring.datasource.password=pass
```

### Environment Variables
```bash
export OPENAI_API_KEY="sk-your-key-here"
```

---

## Output Folder Structure
```
/Users/brunoguimaraes/Documents/JA/
├── Google_Senior_Software_Engineer/
│   ├── tailored_resume_2025-12-04.docx
│   ├── tailored_resume_2025-12-04.txt
│   └── metadata.txt
├── Microsoft_Software_Engineer_II/
│   ├── tailored_resume_2025-12-05.docx
│   ├── tailored_resume_2025-12-05.txt
│   └── metadata.txt
└── ...
```

---

## Next Steps

### Phase 2: UI Implementation
- [ ] React web app (or simple HTML form)
- [ ] One-time resume upload
- [ ] Job URL input
- [ ] Display extracted job details
- [ ] Show match score & improvements
- [ ] Select keywords for tailoring
- [ ] Preview tailored resume
- [ ] Download DOCX/PDF
- [ ] Mark as applied checkbox

### Phase 3: Enhancements
- [ ] PDF generation (DOCX → PDF conversion)
- [ ] PostgreSQL migration
- [ ] User authentication
- [ ] Resume comparison / multi-resume support
- [ ] Auto-apply integration (LinkedIn, Indeed, email)
- [ ] Advanced analytics (best performing keywords, company trends)
- [ ] Cloud deployment (Heroku, Azure, AWS)

---

## Evaluation Metrics

After using the system, you can evaluate:
1. **Acceptance Rate**: How many tailored resumes resulted in interviews?
2. **Score vs. Outcome**: Do higher match scores correlate with better results?
3. **Keyword Impact**: Which suggested keywords most improved outcomes?
4. **Time Saved**: How much time did the tailoring save vs. manual editing?

---

## Testing Checklist

- [ ] Upload a PDF resume
- [ ] Analyze against a real job posting (LinkedIn/Indeed/company site)
- [ ] Review match score (should be 0-100)
- [ ] View improvement suggestions
- [ ] Generate tailored DOCX
- [ ] Download and review in Word/Google Docs
- [ ] Approve and save to JA folder
- [ ] Verify files in `/Users/brunoguimaraes/Documents/JA/`

---

## Support & Contact

For issues, feature requests, or questions:
1. Check DEMO_GUIDE.md for API examples
2. Review application logs: `mvn spring-boot:run`
3. Test individual endpoints with Postman or curl

---

## License

Personal project. Free to modify and extend.

---

**Status**: MVP backend complete. Ready for testing with your resume and real job postings.
**Demo Ready**: In 4 days from start date.
**Next**: React UI for seamless user experience.

🚀 **Good luck with your applications!**
