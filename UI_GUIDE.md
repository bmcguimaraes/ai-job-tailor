# Resume Tailor - UI Implementation Guide

## Overview
This guide covers the UI requirements for the AI Resume Tailor application, designed for macOS users. The backend API is fully functional and ready for integration.

## Key Features

### 1. Resume Upload & Storage
- **Upload Once, Reuse Forever**: Users upload their PDF resume once
- **Resume Persistence**: The system stores the resume and displays it for future job analyses
- **Upload Option**: Button to upload a new resume at any time (replaces the stored one)
- **Status Display**: Show filename, upload date (from `lastUploadedDate`), and ID of current resume

### 2. Job URL Analysis
- **Paste Job URL**: Text input for job posting links
- **Error Handling Flag**: If URL scraping fails, display a clear flag/error message:
  - "⚠️ Could not scrape this job posting"
  - Provide option: "Upload job details manually" (paste job text)
- **Fallback**: When manual paste occurs, let user enter job description as plain text

### 3. Match Analysis Display
- **Scoring Breakdown**:
  - Overall Match Score: `0-100` with color coding
    - ✅ 75+: "Good match" (green)
    - ⚠️ 50-74: "Moderate match" (yellow)
    - ❌ <50: "Poor match" (red)
  - Detailed breakdown table:
    | Component | Score |
    | --- | --- |
    | Soft Skills Match | XX% |
    | Technical Skills Match | XX% |
    | **Job Functions Match** | XX% |
    | Role & Experience Match | XX% |
    | Keywords Match | XX% |

- **Red Flag Alert**: If security clearance or UK passport required, display prominent alert:
  - ⛔ **Red Flag**: "This role requires [Security Clearance / UK Passport]. Recommend not applying."
  - Disable "Continue" button

### 4. Improvements Preview (6 Suggestions)
- **Improvement Cards**: Show up to 6 improvements with:
  - **Title**: Improvement summary
  - **Section**: Where to make change (Skills, Experience, Education, etc.)
  - **Impact**: `+X points` estimated score increase
  - **Keywords**: List of new keywords to add
  - **Bullet Point Suggestion**: NEW! Show suggested bullet point or experience rewrite
    - Example: "Led a team of X developers to deliver Y project, improving Z metric by W%"
  - **Why**: Explanation of why this matters for the job
  - **Checkbox**: ☑️ Select improvements to include in tailored resume

- **Projected Score**: Display "If applied: ~XX/100" based on selected improvements

### 5. Tailored Resume Generation
- **Input**:
  - Max Deviation: Slider (default 40%, range 20-60%)
  - Selected Improvements: Auto-selects top 3 by default
- **Output**:
  - Tailored text preview (in collapsible panel)
  - Deviation percentage shown
  - **"Generate DOCX"** button to create downloadable file

### 6. DOCX Download & Review (macOS Specific)
- **File Format**: Microsoft Word (.docx)
- **macOS Support**: ✅ Can open with Pages, Word, or default
  - Display note: "On macOS, open with Pages, Microsoft Word, or preferred app"
  - Direct download link: `tailored_resume.docx`
- **Review Workflow**:
  1. User downloads DOCX
  2. Opens in Pages/Word for review
  3. Can edit before final approval
  4. Returns to UI or manually applies

### 7. Approval & Storage
- **Pre-Approval Checklist**:
  - ☑️ Reviewed in Word/Pages
  - ☑️ No lies or inaccuracies
  - ☑️ Ready to submit
- **Approve Button**: Saves to `/Users/brunoguimaraes/Documents/JA/{company_position}/`
  - Saves: `original_resume_{date}.txt`
  - Saves: `tailored_resume_{date}.txt`
  - Saves: `tailored_resume_{date}.docx`
  - Saves: `metadata.txt` (with URL, score, date, improvements applied)
- **Confirmation**: "✅ Resume saved! Ready to apply."
  - Show folder path: `/Users/.../Documents/JA/{company_position}/`

## UI Layout Suggestion

```
┌─────────────────────────────────────────────────────────────┐
│                    AI RESUME TAILOR                         │
└─────────────────────────────────────────────────────────────┘

┌─ STEP 1: UPLOAD RESUME ─────────────────────────────────────┐
│ Current Resume: [example.pdf] (Uploaded 2025-12-04)        │
│ [Choose File]  [Upload New Resume]                          │
└─────────────────────────────────────────────────────────────┘

┌─ STEP 2: PASTE JOB URL ─────────────────────────────────────┐
│ [Text Field] https://linkedin.com/jobs/...                 │
│ [Analyze Job]                                               │
│                                                              │
│ ℹ️ Cannot scrape? [Paste Job Text Manually] ↗             │
└─────────────────────────────────────────────────────────────┘

┌─ STEP 3: REVIEW MATCH SCORE ────────────────────────────────┐
│ Company: Acme Corp                                           │
│ Position: Senior Software Engineer                          │
│                                                              │
│ ┌─ Overall Score: 78/100 ✅ Good Match ─────────────────┐ │
│ │ Soft Skills:           72%                             │ │
│ │ Technical Skills:      85%                             │ │
│ │ Job Functions Match:   81% (NEW!)                     │ │
│ │ Role & Experience:     70%                             │ │
│ │ Keywords:              60%                             │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘

┌─ STEP 4: SELECT IMPROVEMENTS ───────────────────────────────┐
│ [Improvement Card 1]                                         │
│ ☑ Add Kubernetes to technical skills                         │
│ Section: Skills | Impact: +3 pts                             │
│ Keywords: [Kubernetes, container orchestration]              │
│ Bullet: N/A                                                  │
│                                                              │
│ [Improvement Card 2]                                         │
│ ☑ Emphasize team leadership                                  │
│ Section: Experience | Impact: +4 pts                         │
│ Keywords: [led team, coordination]                           │
│ Bullet: "Led team of 5 engineers; coordinated sprints"      │
│                                                              │
│ ... (up to 6 cards) ...                                      │
│                                                              │
│ Projected Score if Applied: ~90/100                          │
└─────────────────────────────────────────────────────────────┘

┌─ STEP 5: GENERATE & DOWNLOAD ───────────────────────────────┐
│ Max Deviation: [====40%====]                                 │
│ [Generate DOCX & Download]                                  │
│                                                              │
│ ℹ️ On macOS, open .docx with Pages or Microsoft Word        │
│ Review carefully before applying!                            │
└─────────────────────────────────────────────────────────────┘

┌─ STEP 6: APPROVE & SAVE ────────────────────────────────────┐
│ ☑ Reviewed in Word/Pages                                    │
│ ☑ No lies or inaccuracies                                   │
│ ☑ Ready to apply                                            │
│                                                              │
│ [Approve & Save Resume]  [Go Back]                          │
│                                                              │
│ ✅ Resume saved to:                                         │
│    /Users/.../Documents/JA/Acme_Corp_Senior_SoftEng/       │
└─────────────────────────────────────────────────────────────┘
```

## API Endpoints to Integrate

### 1. Upload Resume
```
POST /api/resumes/upload
Content-Type: multipart/form-data

Form Data: file (PDF)

Response:
{
  "id": 123,
  "filename": "john_doe_resume.pdf",
  "message": "Resume uploaded successfully"
}
```

### 2. Analyze Job
```
POST /api/resumes/analyze/123?vacancyUrl=https://...

Response:
{
  "resumeId": 123,
  "jobExtraction": {
    "success": true,
    "position": "Senior Engineer",
    "company": "Acme Corp",
    "salary": "$150k-180k",
    "yearsOfExperience": 5,
    "technicalSkills": ["Java", "Spring", "Kubernetes"],
    "softSkills": ["Leadership", "Communication"],
    "mainFunctions": ["developing microservices", "leading team", "mentoring juniors"],
    "postingDate": "2025-12-01"
  },
  "matchingScore": {
    "score": 78,
    "fit": "Good match",
    "details": {
      "softSkillsScore": 72,
      "technicalSkillsScore": 85,
      "jobFunctionsScore": 81,
      "roleExperienceScore": 70,
      "keywordScore": 60
    },
    "missingKeywords": ["gRPC", "event-driven"],
    "topKeywords": ["java", "spring", "kubernetes", "rest", "design patterns"]
  },
  "improvements": {
    "improvements": [
      {
        "improvement": "Add Kubernetes to skills",
        "section": "Skills",
        "impactScoreIncrease": 3,
        "keywordsAdded": ["Kubernetes", "container orchestration"],
        "bulletPointSuggestion": null,
        "why": "Job requires Kubernetes expertise"
      },
      {
        "improvement": "Emphasize team leadership",
        "section": "Experience",
        "impactScoreIncrease": 4,
        "keywordsAdded": ["led team", "coordination"],
        "bulletPointSuggestion": "Led cross-functional team of 5 engineers; coordinated sprint planning and delivery",
        "why": "Role requires proven leadership experience"
      }
    ],
    "projectedScore": 90
  }
}
```

### 3. Tailor Resume
```
POST /api/resumes/tailor/123
Content-Type: application/json

Request:
{
  "selectedKeywords": ["Kubernetes", "led team"],
  "maxDeviationPercent": 40
}

Response:
{
  "resumeId": 123,
  "tailoredText": "...",
  "deviationPercent": 28,
  "docxBase64": "UEsDBBQACAAIAAA...",
  "docxFileName": "tailored_resume.docx"
}
```

### 4. Approve & Save
```
POST /api/resumes/approve/123
Content-Type: application/json

Request:
{
  "company": "Acme Corp",
  "position": "Senior Engineer",
  "docxBase64": "UEsDBBQACAAIAAA...",
  "improvements": {...}
}

Response:
{
  "success": true,
  "folderPath": "/Users/.../Documents/JA/Acme_Corp_Senior_Engineer",
  "resumeFile": "tailored_resume_2025-12-04.docx",
  "metadataFile": "metadata.txt",
  "message": "Resume and metadata saved successfully"
}
```

## Technology Stack Recommendations

### Option 1: React (Recommended)
- **Pros**: Professional, responsive, future-scalable
- **Setup**: `npx create-react-app ai-job-tailor-ui`
- **Components**:
  - `ResumeUpload.jsx`
  - `JobAnalysis.jsx`
  - `MatchScore.jsx`
  - `ImprovementsPreview.jsx`
  - `TailoredResume.jsx`
  - `ApprovalStep.jsx`
- **Libraries**:
  - `axios` (API calls)
  - `react-toastify` (notifications)
  - `react-markdown` (if needed)
  - `tailwindcss` or `mui` (styling)

### Option 2: Simple HTML + JavaScript (Faster Prototype)
- **Pros**: Single file, no build step, works immediately
- **Setup**: Plain HTML with inline JavaScript
- **Trade-off**: Less maintainable but faster to deploy

## Important Notes for macOS

✅ **DOCX Format is Perfect for macOS**:
- Pages app (built-in) opens .docx natively
- Microsoft Word (if installed)
- Google Docs (upload and edit)
- LibreOffice
- Any standard Office viewer

🔒 **File Storage**:
- Path: `/Users/brunoguimaraes/Documents/JA/`
- Permissions: Ensure backend has write access
- Folder naming: `{Company}_{Position}` (sanitized)

📝 **Resume Review Best Practice**:
1. Download tailored DOCX
2. Open in Pages
3. Review for accuracy
4. Make small edits if needed
5. Return to UI and approve
6. Resume automatically saved with metadata

## Security Clearance & UK Passport Red Flags

When either is detected:
1. Display prominent ⛔ alert with reason
2. Disable "Continue" button to proceed further
3. Show recommendation: "This role is not recommended for your profile."
4. Allow user to acknowledge but strongly discourage application

## Next Steps

1. ✅ Backend: COMPLETE
2. ⏳ UI: Choose React or HTML approach
3. ⏳ Testing: With actual resume and job postings
4. ⏳ Deployment: GitHub Pages or AWS Amplify for UI + Spring Boot on EC2/Heroku for API

---

**Demo Status**: Backend ready. UI implementation can start immediately.
