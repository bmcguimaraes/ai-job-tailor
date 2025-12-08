

package com.bg.resume_analyser.service;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Service
public class DocxService {
        /**
         * Extracts bullet points from DOCX paragraphs by searching for lines that start with a bullet character.
         * Returns a list of bullet point strings.
         */
        public java.util.List<String> extractBulletsFromDocx(InputStream docxStream) throws IOException {
            java.util.List<String> bullets = new java.util.ArrayList<>();
            XWPFDocument doc = new XWPFDocument(docxStream);
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text == null || text.isEmpty()) continue;
                for (String line : text.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.length() > 1 && (trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("·"))) {
                        bullets.add(trimmed.substring(1).trim());
                    }
                }
            }
            doc.close();
            return bullets;
        }
    /**
     * Extracts structured sections, headings, and bullet points from a DOCX file.
     * Returns a map: section name -> content (with bullets, tables, etc. as lists or strings).
     */
    public Map<String, Object> extractStructuredSections(InputStream docxStream) throws IOException {
        Map<String, Object> sections = new LinkedHashMap<>();
        XWPFDocument doc = new XWPFDocument(docxStream);
        String currentSection = "Header";
        List<Map<String, Object>> currentContent = new ArrayList<>();

        for (XWPFParagraph para : doc.getParagraphs()) {
            String text = para.getText().trim();
            if (text.isEmpty()) continue;

            boolean isHeader = isSectionHeader(text);
            boolean isBold = para.getRuns().stream().anyMatch(XWPFRun::isBold);
            int fontSize = para.getRuns().stream().mapToInt(run -> {
                Double sz = null;
                try {
                    sz = run.getFontSizeAsDouble();
                } catch (Exception e) {
                    // ignore
                }
                return (sz != null && sz > 0) ? sz.intValue() : 11;
            }).max().orElse(11);
            boolean isLarge = fontSize >= 12;

            // Detect section header by style or bold/large font
            if (isHeader || (isBold && isLarge)) {
                if (!currentContent.isEmpty()) {
                    sections.put(currentSection, new ArrayList<>(currentContent));
                    currentContent.clear();
                }
                currentSection = text;
                continue;
            }

            Map<String, Object> paraInfo = new LinkedHashMap<>();
            paraInfo.put("text", text);
            paraInfo.put("fontSize", fontSize);
            paraInfo.put("bold", isBold);

            // Detect bullet points
            if (para.getNumFmt() != null || text.startsWith("•") || text.startsWith("-") || text.startsWith("·")) {
                paraInfo.put("bullet", true);
                    paraInfo.put("text", text.replaceFirst("^[•\\-·]\\s*", "")); // Use double backslash for \s
            } else {
                paraInfo.put("bullet", false);
            }
            currentContent.add(paraInfo);
        }
        if (!currentContent.isEmpty()) {
            sections.put(currentSection, currentContent);
        }

        // Extract tables (e.g., skills section)
        int tableIdx = 0;
        for (org.apache.poi.xwpf.usermodel.XWPFTable table : doc.getTables()) {
            List<List<Map<String, Object>>> rows = new ArrayList<>();
            for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
                List<Map<String, Object>> cells = new ArrayList<>();
                for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                    String cellText = cell.getText().trim();
                    int cellFontSize = cell.getParagraphs().stream()
                        .flatMap(p -> p.getRuns().stream())
                        .mapToInt(run -> {
                            Double sz = null;
                            try {
                                sz = run.getFontSizeAsDouble();
                            } catch (Exception e) {
                                // ignore
                            }
                            return (sz != null && sz > 0) ? sz.intValue() : 11;
                        })
                        .max().orElse(11);
                    Map<String, Object> cellInfo = new LinkedHashMap<>();
                    cellInfo.put("text", cellText);
                    cellInfo.put("fontSize", cellFontSize);
                    cells.add(cellInfo);
                }
                rows.add(cells);
            }
            sections.put("Table_" + (++tableIdx), rows);
        }

        doc.close();
        return sections;
    }

    public byte[] generateDocx(String resumeText) throws IOException {
        return generateDocxWithStyle(resumeText, null);
    }

    /**
     * Generate DOCX using style from original resume DOCX if provided.
     */
    public byte[] generateDocxWithStyle(String resumeText, InputStream originalDocxStream) throws IOException {
        XWPFDocument document = new XWPFDocument();
        Map<String, Object> styleMap = null;
        if (originalDocxStream != null) {
            try {
                styleMap = DocxStyleUtil.extractStyles(originalDocxStream);
            } catch (Exception e) {
                styleMap = null;
            }
        }

        String[] lines = resumeText.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                document.createParagraph();
            } else if (isSectionHeader(line)) {
                XWPFParagraph para = document.createParagraph();
                para.setSpacingBefore(200);
                para.setSpacingAfter(100);
                XWPFRun run = para.createRun();
                run.setText(line.trim());
                run.setBold(true);
                run.setFontSize(12);
                DocxStyleUtil.applyStyles(para, run, styleMap);
            } else if (line.trim().startsWith("•") || line.trim().startsWith("-")) {
                XWPFParagraph para = document.createParagraph();
                para.setIndentationLeft(720);
                XWPFRun run = para.createRun();
                run.setText(line.trim().replaceFirst("^[•-]\\s*", ""));
                run.setFontSize(11);
                DocxStyleUtil.applyStyles(para, run, styleMap);
            } else {
                XWPFParagraph para = document.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(line.trim());
                run.setFontSize(11);
                DocxStyleUtil.applyStyles(para, run, styleMap);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.write(out);
        document.close();
        return out.toByteArray();
    }

    private boolean isSectionHeader(String line) {
        String lower = line.toLowerCase().trim();
        return lower.matches("^(experience|education|skills|summary|contact|objective|certifications|projects).*") ||
               line.endsWith(":") && line.length() < 50;
    }
}
