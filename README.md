# AI Job Tailor - Resume Analyzer & Tailoring System

## The Problem

Manually tailoring a resume for every job application is a time-consuming, repetitive, and error-prone process. Job seekers often have to:
-   Carefully read each job description to identify key skills and requirements.
-   Manually edit their resume to insert these keywords.
-   Struggle to preserve the document's original formatting, often breaking styles, fonts, and spacing.
-   Risk introducing typos or grammatical errors during the editing process.

This project aims to solve this by automating the entire tailoring process, creating a perfectly formatted, keyword-optimised resume in seconds.

## Version 1.0 - Functional MVP

This marks the first fully functional version of the AI Job Tailor. The system can now successfully take a base resume, compare it against a job description, and produce a surgically-edited, tailored DOCX file that preserves the original formatting.

### Core Features
-   **AI-Powered Tailoring**: Leverages the OpenAI API (`gpt-4-turbo`) to analyze a resume against a job description and generate a plan for targeted edits.
-   **Flexible Job Input**: Accepts a job description from either a public URL (e.g., a LinkedIn posting) or as plain text.
-   **Surgical DOCX Editing**: Uses Apache POI to apply the AI's edit plan to a `.docx` file, ensuring that all original formatting, fonts, and styles are perfectly preserved.
-   **Robust Text Replacement**: Implements a fuzzy matching algorithm (`JaroWinklerSimilarity`) to reliably find and replace text, even with minor variations.
-   **Simple REST API**: Exposes a single, easy-to-use endpoint for all operations.

---

## Architecture

The application follows a simple, service-oriented design pattern running on Spring Boot.

```
HTTP Request (POST /api/resumes/tailor/{id})
    ↓
ResumeController
    ↓
ScraperService (if URL is provided)
    ↓
ImproveService (Calls OpenAI API, generates edit plan)
    ↓
DocxService (Executes the edit plan on the .docx file)
    ↓
FileSystem (Saves tailored resume and metadata.txt)
```

### Architectural Trade-offs

-   **"Surgical Edit" vs. Full Regeneration**:
    -   **Our Choice**: We opted for a "Surgical Edit" approach, where the AI generates a JSON plan of edits, and the Java code executes it.
    -   **Trade-off**: This is more complex to implement, as it requires a robust system for finding and replacing text within the DOCX structure. However, it gives us **maximum control and guarantees that the original document's formatting is perfectly preserved**.
    -   **Alternative**: We could have asked the AI to generate a completely new resume text. This would be simpler to implement but would lose all original formatting and risk the AI "hallucinating" a completely different structure.

-   **Fuzzy Matching vs. Exact Matching**:
    -   **Our Choice**: We implemented fuzzy string matching (`JaroWinklerSimilarity`) for text replacement.
    -   **Trade-off**: This adds a slight computational overhead and a dependency on `Apache Commons Text`. However, it makes the system **far more resilient**. It can find and replace text even if there are minor differences (like extra spaces or slight rephrasing) between the resume text and the AI's `original_text` instruction.
    -   **Alternative**: An exact match is simpler but brittle. A single extra space or a minor punctuation difference would cause the replacement to fail.

---

## Tech Stack
- **Language**: Java 17
- **Framework**: Spring Boot 3
- **AI Engine**: OpenAI API (gpt-4-turbo)
- **Web Scraping**: Jsoup 1.17.2
- **Document Manipulation**: Apache POI 5.2.3
- **Fuzzy Matching**: Apache Commons Text 1.12.0
- **Build Tool**: Maven

---

## Operational Costs

The application uses the OpenAI API, which is a pay-as-you-go service. Based on initial testing, the average cost for a single run of the tailoring process is approximately **$0.035 (3.5 cents)**. This cost can fluctuate based on the length of the resume and the job description provided.

---

## Getting Started

### Prerequisites
-   Java 17+
-   Maven
-   An OpenAI API key

### Configuration
1.  The application looks for the OpenAI API key in an environment variable named `OPENAI_API_KEY`.
    ```bash
    export OPENAI_API_KEY="sk-your-key-here"
    ```
2.  Place your base resume, named `resume_1.docx`, in the root directory of the project.

### Run Application
```bash
# Navigate to the project root
cd ai-job-tailor

# Run the Spring Boot application
mvn spring-boot:run

# The API will be available at http://localhost:8080
```

---

## API Endpoint

The application has a single endpoint to perform the tailoring.

### Tailor Resume
`POST /api/resumes/tailor/{id}`

-   `{id}`: The identifier of the resume file (e.g., `1` for `resume_1.docx`).

**Request Body (JSON)**

You must provide *either* a `vacancyUrl` or a `jobDescription`.

**Example 1: Using a URL**
```json
{
  "vacancyUrl": "https://www.linkedin.com/jobs/view/..."
}
```

**Example 2: Using Plain Text**
```json
{
  "jobDescription": "We are looking for a Senior Java Developer with experience in Spring Boot, microservices, and cloud platforms like AWS or Azure..."
}
```

### How to Test with cURL

There are three ways to provide the job description.

**1. Tailor using a URL:**
```bash
curl -X POST -H "Content-Type: application/json" \
-d '{
  "vacancyUrl": "https://www.linkedin.com/jobs/view/your-job-id-here/"
}' \
http://localhost:8080/api/resumes/tailor/1
```

**2. Tailor using inline plain text (for short descriptions):**
```bash
curl -X POST -H "Content-Type: application/json" \
-d '{
  "jobDescription": "Your job description text here."
}' \
http://localhost:8080/api/resumes/tailor/1
```

**3. Tailor using a JSON file (Recommended for long descriptions):**

This method is the most reliable way to handle long job descriptions with special characters, as it avoids issues with shell command interpretation. However, it requires manually copying and pasting text, which is not an optimal long-term workflow. A proper UI would be a better solution.

First, create a file named `job.json` with the following content:
```json
{
  "jobDescription": "Paste the full, long job description text here..."
}
```

Then, run `curl` using the `--data` flag:
```bash
curl -X POST -H "Content-Type: application/json" \
--data @job.json \
http://localhost:8080/api/resumes/tailor/1
```

Upon success, a `resume_1_tailored.docx` file and a `metadata.txt` file detailing the changes will be created in the project's root directory.

---

## Next Steps
-   [ ] Build a simple web UI (e.g., using React or Thymeleaf) to provide a more user-friendly interface than cURL.
-   [ ] Continue to refine the AI prompt in `ImproveService` for even greater accuracy and consistency.
-   [ ] Add support for more input file formats (e.g., `.pdf`).
-   [ ] Implement a global exception handler (`@ControllerAdvice`) for more consistent API error responses.

---

## License

This is a personal project. You are free to use, modify, and extend it.
