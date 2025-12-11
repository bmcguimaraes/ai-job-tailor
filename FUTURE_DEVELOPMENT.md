# Future Development Ideas

This document outlines potential features and improvements for the AI Job Tailor application.

## Core Functionality Enhancements
- **Support for More Job Sites**: Implement dedicated web scrapers for other major job boards like Indeed, Glassdoor, etc., to broaden the application's reach.
- **PDF Output Generation**: In addition to the tailored `.docx` file, automatically generate a `.pdf` version for easy, non-editable sharing.
- **Advanced Match Analysis**: Develop a more sophisticated "match score" system that provides detailed feedback on which key requirements from the job description are met and which are missing.

## User Experience
- **Simple Web Frontend**: Create a basic user interface (e.g., using React or Thymeleaf) to allow for file uploads and job description input through a web browser, removing the need for `curl` commands.
- **Interactive Edit Approval**: Before applying changes, present the AI's suggested `edit_plan` to the user for approval, allowing them to accept or reject individual changes.

## Quality & Reliability
- **Configuration File**: Externalize settings like the OpenAI API key, base folder path, and AI model name into an `application.properties` or `.yml` file for easier management.
- **Comprehensive Unit & Integration Testing**: Build out a full suite of tests to ensure all services (`DocxService`, `ImproveService`, etc.) work as expected and to prevent future regressions.
