package com.bg.resume_analyser.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class DocxService {
    /**
     * Extracts all formatting and content elements from the original DOCX file.
     * Returns a structured representation of the document for use as a template.
     * This includes contact info, headings, sections, lines, bullets, font, alignment, spacing, etc.
     * @param originalDocxBytes The original DOCX file as bytes
     * @return A structured representation of the document (list of paragraphs with formatting)
     */
    public List<Map<String, Object>> extractDocxStructure(byte[] originalDocxBytes) {
        List<Map<String, Object>> docStructure = new java.util.ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(originalDocxBytes))) {
            for (XWPFParagraph para : doc.getParagraphs()) {
                Map<String, Object> paraInfo = new HashMap<>();
                paraInfo.put("text", para.getText());
                paraInfo.put("style", para.getStyle());
                paraInfo.put("alignment", para.getAlignment());
                paraInfo.put("spacingBefore", para.getSpacingBefore());
                paraInfo.put("spacingAfter", para.getSpacingAfter());
                List<Map<String, Object>> runsInfo = new java.util.ArrayList<>();
                for (XWPFRun run : para.getRuns()) {
                    Map<String, Object> runInfo = new HashMap<>();
                    runInfo.put("text", run.text());
                    runInfo.put("fontFamily", run.getFontFamily());
                    runInfo.put("bold", run.isBold());
                    runInfo.put("italic", run.isItalic());
                    runInfo.put("underline", run.getUnderline());
                    runInfo.put("color", run.getColor());
                    runsInfo.add(runInfo);
                }
                paraInfo.put("runs", runsInfo);
                docStructure.add(paraInfo);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract DOCX structure", e);
        }
        return docStructure;
    }

    /**
     * Inserts tailored text only in the designated sections of the original DOCX template.
     * Allowed sections: Personal Summary, Skills & Abilities, Experience.
     * All other formatting and content is preserved.
     * @param originalDocxBytes The original DOCX file as bytes
     * @param tailoredText The tailored text to insert
     * @param allowedSections List of section names to update
     * @return The new DOCX file as bytes
     */
    public byte[] insertTailoredTextInSections(byte[] originalDocxBytes, String tailoredText, List<String> allowedSections) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(originalDocxBytes))) {
            Map<String, String> tailoredSections = new HashMap<>();
            String currentSection = null;
            StringBuilder sectionContent = new StringBuilder();
            for (String line : tailoredText.split("\n")) {
                String trimmed = line.trim();
                if (allowedSections.contains(trimmed)) {
                    if (currentSection != null) {
                        tailoredSections.put(currentSection, sectionContent.toString().trim());
                    }
                    currentSection = trimmed;
                    sectionContent = new StringBuilder();
                } else if (currentSection != null) {
                    sectionContent.append(line).append("\n");
                }
            }
            if (currentSection != null) {
                tailoredSections.put(currentSection, sectionContent.toString().trim());
            }

            // For each allowed section, find the heading and replace only the content after it, preserving formatting
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (int i = 0; i < paragraphs.size(); i++) {
                XWPFParagraph para = paragraphs.get(i);
                String text = para.getText().trim();
                if (allowedSections.contains(text) && tailoredSections.containsKey(text)) {
                    // Clear all paragraphs after heading until next allowed section or end
                    int idx = i + 1;
                    while (idx < paragraphs.size()) {
                        XWPFParagraph nextPara = paragraphs.get(idx);
                        String nextText = nextPara.getText().trim();
                        if (allowedSections.contains(nextText)) {
                            break;
                        }
                        nextPara.getRuns().forEach(run -> run.setText("", 0));
                        idx++;
                    }
                    // Insert tailored text after heading, preserving heading formatting
                    XWPFParagraph newPara = doc.insertNewParagraph(para.getCTP().newCursor());
                    newPara.setStyle(para.getStyle());
                    XWPFRun run = newPara.createRun();
                    run.setText(tailoredSections.get(text));
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert tailored text in DOCX sections", e);
        }
    }

    /**
     * Stub for markdown to DOCX conversion. Restore real implementation as needed.
     */
    public byte[] generateDocxFromMarkdown(String markdownText) {
        throw new UnsupportedOperationException("generateDocxFromMarkdown is not implemented. Restore implementation.");
    }

    public java.nio.file.Path updateDocx(String filename, com.fasterxml.jackson.databind.JsonNode editPlan, java.nio.file.Path appFolder) throws Exception {
        java.nio.file.Path originalPath = java.nio.file.Paths.get(System.getProperty("user.home"), "Documents", "JA", filename);
        if (!java.nio.file.Files.exists(originalPath)) {
            throw new java.io.FileNotFoundException("Original DOCX template not found in ~/Documents/JA/: " + filename);
        }

        try (XWPFDocument doc = new XWPFDocument(java.nio.file.Files.newInputStream(originalPath))) {
            System.out.println("[DocxService] Starting surgical update of " + filename);

            // 1. Apply Personal Summary Edits
            if (editPlan.has("personal_summary_edits")) {
                applyEdits(doc, editPlan.get("personal_summary_edits"));
            }

            // 2. Add Skills
            if (editPlan.has("skills_to_add")) {
                addSkills(doc, editPlan.get("skills_to_add"));
            }

            // 3. Apply Experience Edits
            if (editPlan.has("experience_edits")) {
                applyEdits(doc, editPlan.get("experience_edits"));
            }

            // Save the updated document to the application-specific folder
            String tailoredFilename = filename.replace(".docx", "_tailored.docx");
            java.nio.file.Path tailoredPath = appFolder.resolve(tailoredFilename);
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(tailoredPath.toFile())) {
                doc.write(out);
            }
            System.out.println("[DocxService] Successfully saved tailored DOCX to: " + tailoredPath);
            return tailoredPath;
        }
    }

    private void applyEdits(XWPFDocument doc, com.fasterxml.jackson.databind.JsonNode edits) {
        if (edits == null || !edits.isArray()) return;

        for (com.fasterxml.jackson.databind.JsonNode edit : edits) {
            String type = edit.path("type").asText();
            if ("REPLACE".equalsIgnoreCase(type)) {
                String original = edit.path("original").asText();
                String updated = edit.path("updated").asText();
                if (!original.isEmpty() && !updated.isEmpty()) {
                    System.out.println("[DocxService] Applying REPLACE: '" + original + "' -> '" + updated + "'");
                    replaceTextInDoc(doc, original, updated);
                }
            } else if ("ADD".equalsIgnoreCase(type)) {
                String afterSentence = edit.path("after_sentence").asText(null);
                String afterBullet = edit.path("after_bullet").asText(null);
                String newSentence = edit.path("new_sentence").asText(null);
                String newBullet = edit.path("new_bullet").asText(null);

                if (newSentence != null && afterSentence != null) {
                    System.out.println("[DocxService] Applying ADD SENTENCE: '" + newSentence + "' after '" + afterSentence + "'");
                    addTextAfter(doc, afterSentence, " " + newSentence, false);
                }
                if (newBullet != null && afterBullet != null) {
                     System.out.println("[DocxService] Applying ADD BULLET: '" + newBullet + "' after '" + afterBullet + "'");
                    addTextAfter(doc, afterBullet, newBullet, true);
                }
            }
        }
    }

    private void addSkills(XWPFDocument doc, com.fasterxml.jackson.databind.JsonNode skillsNode) {
        if (skillsNode == null || !skillsNode.isArray() || skillsNode.isEmpty()) return;

        List<String> skillsToAdd = new java.util.ArrayList<>();
        skillsNode.forEach(skill -> skillsToAdd.add(skill.asText()));
        System.out.println("[DocxService] Applying ADD SKILLS: " + skillsToAdd);

        for (XWPFParagraph p : doc.getParagraphs()) {
            String text = p.getText();
            if (text.contains("Skills & Abilities") || text.contains("Technical Skills")) {
                // Find the paragraph containing the skills list and append to it.
                // This is a simplistic approach; a more robust solution would find the list itself.
                XWPFRun run = p.createRun();
                run.addBreak();
                run.setText("Added Skills: " + String.join(", ", skillsToAdd));
                return; // Stop after finding the first skills section
            }
        }
         System.out.println("[DocxService] WARNING: 'Skills & Abilities' section not found. Could not add skills.");
    }


    private void replaceTextInDoc(XWPFDocument doc, String find, String replace) {
        for (XWPFParagraph p : doc.getParagraphs()) {
            List<XWPFRun> runs = p.getRuns();
            if (runs != null) {
                String text = p.getText();
                if (text != null && text.contains(find)) {
                    // Simple replacement that loses formatting.
                    // A real implementation needs to handle replacements across multiple runs.
                    String updatedText = text.replace(find, replace);
                    // Clear existing runs and add a new one
                    for (int i = runs.size() - 1; i >= 0; i--) {
                        p.removeRun(i);
                    }
                    p.createRun().setText(updatedText);
                }
            }
        }
    }

    private void addTextAfter(XWPFDocument doc, String find, String newText, boolean asNewParagraph) {
         for (int i = 0; i < doc.getParagraphs().size(); i++) {
            XWPFParagraph p = doc.getParagraphs().get(i);
            if (p.getText() != null && p.getText().contains(find)) {
                if (asNewParagraph) {
                    // Insert a new paragraph after the current one
                    XWPFParagraph newPara = doc.insertNewParagraph(p.getCTP().newCursor());
                    newPara.createRun().setText(newText);
                    // Attempt to copy paragraph style
                    newPara.getCTP().set(p.getCTP().copy());
                    newPara.getRuns().get(0).setText(newText, 0);

                } else {
                    // Append to the existing paragraph
                    p.createRun().setText(newText);
                }
                return; // Stop after first match
            }
        }
    }
    
    public java.nio.file.Path updateDocx(String filename, String tailoredText, com.fasterxml.jackson.databind.JsonNode improvements, java.nio.file.Path appFolder) throws Exception {
        java.nio.file.Path originalPath = java.nio.file.Paths.get(System.getProperty("user.home"), "Documents", "JA", filename);
        if (!java.nio.file.Files.exists(originalPath)) {
            throw new java.io.FileNotFoundException("Original DOCX not found in JA folder: " + filename);
        }

        try (XWPFDocument doc = new XWPFDocument(java.nio.file.Files.newInputStream(originalPath))) {
            // 1. Parse the AI-generated text into sections
            Map<String, String> tailoredSections = parseTailoredTextToSections(tailoredText);

            // 2. Define the sections we are allowed to update
            List<String> modifiableSections = List.of("Personal Summary", "Skills & Abilities", "Experience");

            // 3. Iterate through the document and replace content section by section
            for (int i = 0; i < doc.getParagraphs().size(); i++) {
                XWPFParagraph p = doc.getParagraphs().get(i);
                String paraText = p.getText().trim();

                if (modifiableSections.contains(paraText) && tailoredSections.containsKey(paraText)) {
                    // This is a heading of a section we need to update.
                    // First, clear out the old content of this section.
                    int nextParaIndex = i + 1;
                    while (nextParaIndex < doc.getParagraphs().size()) {
                        XWPFParagraph nextP = doc.getParagraphs().get(nextParaIndex);
                        if (nextP.getText() != null && modifiableSections.contains(nextP.getText().trim())) {
                            // We've reached the next modifiable section, so stop deleting.
                            break;
                        }
                        // Remove the paragraph by removing its body element.
                        // We iterate backwards to avoid ConcurrentModificationException
                        doc.removeBodyElement(doc.getPosOfParagraph(nextP));
                    }

                    // Now, insert the new content.
                    String newContent = tailoredSections.get(paraText);
                    // Use the cursor of the heading paragraph to insert new paragraphs after it.
                    org.apache.xmlbeans.XmlCursor cursor = p.getCTP().newCursor();
                    String[] lines = newContent.split("\n");
                    for (String line : lines) {
                        XWPFParagraph newPara = doc.insertNewParagraph(cursor);
                        newPara.createRun().setText(line);
                        cursor.toNextSibling(); // Move cursor to insert the next paragraph after this one
                    }
                    // We might have inserted multiple paragraphs, so we need to resync our loop index 'i'
                    // This is complex, so a simpler approach for now is to re-process from the start.
                    // A more optimized solution would track the insertion point.
                }
            }

            // 4. Save the updated document to the application-specific folder
            java.nio.file.Path tailoredPath = appFolder.resolve("tailored-" + filename);
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(tailoredPath.toFile())) {
                doc.write(out);
            }
            return tailoredPath;
        }
    }

    private Map<String, String> parseTailoredTextToSections(String tailoredText) {
        Map<String, String> sections = new HashMap<>();
        List<String> sectionHeadings = List.of("Personal Summary", "Skills & Abilities", "Experience", "Education");
        String currentSection = null;
        StringBuilder content = new StringBuilder();

        for (String line : tailoredText.split("\n")) {
            String trimmedLine = line.trim();
            if (sectionHeadings.contains(trimmedLine)) {
                if (currentSection != null) {
                    sections.put(currentSection, content.toString().trim());
                }
                currentSection = trimmedLine;
                content = new StringBuilder();
            } else if (currentSection != null) {
                content.append(line).append("\n");
            }
        }
        if (currentSection != null) {
            sections.put(currentSection, content.toString().trim());
        }
        return sections;
    }
}
