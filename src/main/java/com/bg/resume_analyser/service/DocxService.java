package com.bg.resume_analyser.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;

@Service
public class DocxService {

    /**
     * Extracts all formatting and content elements from the original DOCX file.
     * Returns a structured representation of the document for use as a template.
     * This includes contact info, headings, sections, lines, bullets, font, alignment, spacing, etc.
     *
     * @param originalDocxBytes The original DOCX file as bytes
     * @return A structured representation of the document (list of paragraphs with formatting)
     */
    public List<java.util.Map<String, Object>> extractDocxStructure(byte[] originalDocxBytes) {
        List<java.util.Map<String, Object>> docStructure = new java.util.ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(originalDocxBytes))) {
            for (XWPFParagraph para : doc.getParagraphs()) {
                java.util.Map<String, Object> paraInfo = new java.util.HashMap<>();
                paraInfo.put("text", para.getText());
                paraInfo.put("style", para.getStyle());
                paraInfo.put("alignment", para.getAlignment());
                paraInfo.put("spacingBefore", para.getSpacingBefore());
                paraInfo.put("spacingAfter", para.getSpacingAfter());
                List<java.util.Map<String, Object>> runsInfo = new java.util.ArrayList<>();
                for (XWPFRun run : para.getRuns()) {
                    java.util.Map<String, Object> runInfo = new java.util.HashMap<>();
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
     *
     * @param originalDocxBytes The original DOCX file as bytes
     * @param tailoredText      The tailored text to insert
     * @param allowedSections   List of section names to update
     * @return The new DOCX file as bytes
     */
    public byte[] insertTailoredTextInSections(byte[] originalDocxBytes, String tailoredText, List<String> allowedSections) {
        try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(originalDocxBytes))) {
            java.util.Map<String, String> tailoredSections = new java.util.HashMap<>();
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
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
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

    public Path updateDocx(String originalFilename, JsonNode editPlan, JsonNode skillsToAdd, Path appFolder) throws IOException {
        System.out.println("[DocxService] Starting surgical update of " + originalFilename);
        Path templatePath = Paths.get(System.getProperty("user.home"), "Documents", "JA", originalFilename);
        if (!Files.exists(templatePath)) {
            throw new IOException("Template file not found at: " + templatePath);
        }

        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(templatePath))) {
            if (editPlan != null && editPlan.isArray()) {
                System.out.println("[DocxService] Applying EDIT PLAN: " + editPlan.size() + " actions.");
                for (JsonNode action : editPlan) {
                    replaceTextInDocFuzzy(doc, action.get("original_text").asText(), action.get("new_text").asText());
                }
            }

            if (skillsToAdd != null && skillsToAdd.isObject()) {
                System.out.println("[DocxService] Applying ADD SKILLS: " + skillsToAdd.toString());
                addSkills(doc, skillsToAdd);
            }

            String tailoredFilename = originalFilename.replace(".docx", "_tailored.docx");
            Path outputPath = appFolder.resolve(tailoredFilename);
            try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
                doc.write(out);
            }
            System.out.println("[DocxService] Surgical update complete. File saved to: " + outputPath);
            return outputPath;
        }
    }

    private void replaceTextInDocFuzzy(XWPFDocument doc, String originalText, String newText) {
        double bestScore = 0.0;
        XWPFParagraph bestMatchParagraph = null;

        // First, find the paragraph with the highest similarity score
        for (XWPFParagraph p : doc.getParagraphs()) {
            String paragraphText = p.getText().trim().replaceAll("\\s+", " ");
            String cleanOriginalText = originalText.trim().replaceAll("\\s+", " ");
            double score = new JaroWinklerSimilarity().apply(paragraphText, cleanOriginalText);

            if (score > bestScore) {
                bestScore = score;
                bestMatchParagraph = p;
            }
        }

        // If we found a sufficiently good match, replace the text
        if (bestMatchParagraph != null && bestScore > 0.95) { // 95% similarity threshold
            System.out.println("[DocxService] Found best match for replacement with score " + bestScore + ": '" + bestMatchParagraph.getText().substring(0, Math.min(50, bestMatchParagraph.getText().length())) + "...'");

            // Preserve the style of the first run
            String fontFamily = !bestMatchParagraph.getRuns().isEmpty() ? bestMatchParagraph.getRuns().get(0).getFontFamily() : "Calibri";
            Double fontSize = null;
            if (!bestMatchParagraph.getRuns().isEmpty()) {
                fontSize = bestMatchParagraph.getRuns().get(0).getFontSizeAsDouble();
            }
            String color = !bestMatchParagraph.getRuns().isEmpty() ? bestMatchParagraph.getRuns().get(0).getColor() : "000000";
            boolean isBold = !bestMatchParagraph.getRuns().isEmpty() && bestMatchParagraph.getRuns().get(0).isBold();

            // Clear existing runs in the paragraph
            while (!bestMatchParagraph.getRuns().isEmpty()) {
                bestMatchParagraph.removeRun(0);
            }

            // Create a new run with the new text and preserved style
            XWPFRun newRun = bestMatchParagraph.createRun();
            newRun.setText(newText);
            newRun.setFontFamily(fontFamily);
            if (fontSize != null && fontSize > 0) {
                newRun.setFontSize(fontSize);
            }
            newRun.setColor(color);
            newRun.setBold(isBold);

            System.out.println("[DocxService] Successfully replaced text.");
        } else {
            System.err.println("[DocxService] FAILED to find a suitable match for replacement. Best score: " + bestScore + ". Text was: '" + originalText.substring(0, Math.min(50, originalText.length())) + "...'");
        }
    }

    private void addSkills(XWPFDocument doc, JsonNode skillsToAdd) {
        XWPFParagraph skillsParagraph = findParagraphContaining(doc, "Skills & Abilities");
        if (skillsParagraph == null) {
            System.err.println("[DocxService] Could not find 'Skills & Abilities' section to add skills.");
            return;
        }

        Iterator<String> categoryIterator = skillsToAdd.fieldNames();
        while (categoryIterator.hasNext()) {
            String category = categoryIterator.next();
            JsonNode skills = skillsToAdd.get(category);

            if (skills.isArray() && !skills.isEmpty()) {
                XWPFParagraph targetParagraph = findParagraphContaining(doc, category + ":");
                if (targetParagraph == null) {
                    System.err.println("[DocxService] Could not find category paragraph: '" + category + ":'");
                    continue;
                }

                for (JsonNode skillNode : skills) {
                    String skillText = skillNode.asText();
                    if (!targetParagraph.getText().contains(skillText)) {
                        // Create a new run for the comma and space to avoid hyperlink issues
                        XWPFRun separatorRun = targetParagraph.createRun();
                        separatorRun.setText(", ");
                        // Copy style from the last run in the paragraph
                        if (!targetParagraph.getRuns().isEmpty()) {
                            XWPFRun lastRun = targetParagraph.getRuns().get(targetParagraph.getRuns().size() - 2); // -2 because we just added one
                            copyRunStyle(lastRun, separatorRun);
                        }

                        // Create a new run for the skill itself
                        XWPFRun skillRun = targetParagraph.createRun();
                        skillRun.setText(skillText);
                        // Copy style from the last run
                        if (!targetParagraph.getRuns().isEmpty()) {
                            XWPFRun lastRun = targetParagraph.getRuns().get(targetParagraph.getRuns().size() - 2);
                            copyRunStyle(lastRun, skillRun);
                        }
                    }
                }
            }
        }
    }

    private XWPFParagraph findParagraphContaining(XWPFDocument doc, String text) {
        for (XWPFParagraph p : doc.getParagraphs()) {
            if (p.getText().contains(text)) {
                return p;
            }
        }
        return null;
    }

    private void copyRunStyle(XWPFRun source, XWPFRun target) {
        target.setFontFamily(source.getFontFamily());
        if (source.getFontSizeAsDouble() != null) {
            target.setFontSize(source.getFontSizeAsDouble());
        }
        target.setBold(source.isBold());
        target.setItalic(source.isItalic());
        target.setUnderline(source.getUnderline());
        target.setColor(source.getColor());
    }
}
