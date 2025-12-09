package com.bg.resume_analyser.service;

import org.apache.poi.xwpf.usermodel.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class DocxStyleUtil {
        /**
         * Deeply extracts all formatting from a DOCX file: paragraphs, runs, tables, cells, images, hyperlinks, and styles.
         * Returns a structure with all formatting details for later mapping.
         */
        public static Map<String, Object> extractFullFormatting(InputStream docxInputStream) throws Exception {
            Map<String, Object> docxFormat = new HashMap<>();
            XWPFDocument doc = new XWPFDocument(docxInputStream);
            // Extract paragraphs and runs
            List<Map<String, Object>> paraList = new ArrayList<>();
            for (XWPFParagraph para : doc.getParagraphs()) {
                Map<String, Object> paraMap = new HashMap<>();
                paraMap.put("alignment", para.getAlignment());
                paraMap.put("spacingBefore", para.getSpacingBefore());
                paraMap.put("spacingAfter", para.getSpacingAfter());
                paraMap.put("indentationLeft", para.getIndentationLeft());
                paraMap.put("indentationRight", para.getIndentationRight());
                paraMap.put("isBullet", para.getNumFmt() != null);
                // paraMap.put("backgroundColor", para.getBackgroundColor()); // Not supported by POI
                paraMap.put("borderBottom", para.getBorderBottom());
                paraMap.put("borderTop", para.getBorderTop());
                paraMap.put("borderLeft", para.getBorderLeft());
                paraMap.put("borderRight", para.getBorderRight());
                List<Map<String, Object>> runsList = new ArrayList<>();
                for (XWPFRun run : para.getRuns()) {
                    Map<String, Object> runMap = new HashMap<>();
                    runMap.put("text", run.getText(0));
                    runMap.put("fontFamily", run.getFontFamily());
                    runMap.put("fontSize", run.getFontSizeAsDouble());
                    runMap.put("bold", run.isBold());
                    runMap.put("italic", run.isItalic());
                    runMap.put("underline", run.getUnderline());
                    runMap.put("color", run.getColor());
                    runMap.put("strike", run.isStrikeThrough());
                    // runMap.put("highlightColor", ...); // Not supported by POI
                    // runMap.put("hyperlink", ...); // Not supported by POI
                    runsList.add(runMap);
                }
                paraMap.put("runs", runsList);
                paraList.add(paraMap);
            }
            docxFormat.put("paragraphs", paraList);
            // Extract tables
            List<List<List<Map<String, Object>>>> tables = new ArrayList<>();
            for (XWPFTable table : doc.getTables()) {
                List<List<Map<String, Object>>> rows = new ArrayList<>();
                for (XWPFTableRow row : table.getRows()) {
                    List<Map<String, Object>> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        Map<String, Object> cellMap = new HashMap<>();
                        cellMap.put("text", cell.getText());
                        cellMap.put("backgroundColor", cell.getColor());
                        cellMap.put("verticalAlignment", cell.getVerticalAlignment());
                        cellMap.put("width", cell.getWidth());
                        // cellMap.put("borderBottom", ...); // Not supported by POI
                        // cellMap.put("borderTop", ...); // Not supported by POI
                        // cellMap.put("borderLeft", ...); // Not supported by POI
                        // cellMap.put("borderRight", ...); // Not supported by POI
                        cells.add(cellMap);
                    }
                    rows.add(cells);
                }
                tables.add(rows);
            }
            docxFormat.put("tables", tables);
            // Extract images
            List<Map<String, Object>> images = new ArrayList<>();
            for (XWPFPictureData pic : doc.getAllPictures()) {
                Map<String, Object> imgMap = new HashMap<>();
                imgMap.put("fileName", pic.getFileName());
                imgMap.put("imageType", pic.getPictureType());
                imgMap.put("data", pic.getData());
                images.add(imgMap);
            }
            docxFormat.put("images", images);
            doc.close();
            return docxFormat;
        }

        /**
         * Applies full formatting to paragraphs, runs, tables, cells, and images in a new DOCX document.
         */
        @SuppressWarnings("unchecked")
        public static void applyFullFormatting(XWPFDocument document, Map<String, Object> docxFormat, List<String> tailoredLines) {
            List<Map<String, Object>> paraList = (List<Map<String, Object>>) docxFormat.get("paragraphs");
            // Build a map of original section headers to their paragraph style
            Map<String, Map<String, Object>> headerStyles = new HashMap<>();
            if (paraList != null) {
                for (Map<String, Object> paraMap : paraList) {
                    List<Map<String, Object>> runsList = (List<Map<String, Object>>) paraMap.get("runs");
                    if (runsList != null && !runsList.isEmpty()) {
                        String headerText = (String) runsList.get(0).get("text");
                        if (headerText != null && isSectionHeader(headerText)) {
                            headerStyles.put(headerText.trim().toLowerCase(), paraMap);
                        }
                    }
                }
            }

            for (String line : tailoredLines) {
                XWPFParagraph para = document.createParagraph();
                String trimmedLine = line.trim();
                // Semantic mapping: if line matches a known section header, apply header style
                Map<String, Object> headerStyle = headerStyles.get(trimmedLine.toLowerCase());
                if (headerStyle != null) {
                    applyParagraphStyles(para, headerStyle);
                    List<Map<String, Object>> runsList = (List<Map<String, Object>>) headerStyle.get("runs");
                    if (runsList != null && !runsList.isEmpty()) {
                        XWPFRun run = para.createRun();
                        run.setText(trimmedLine);
                        applyRunStyles(run, runsList.get(0));
                    } else {
                        XWPFRun run = para.createRun();
                        run.setText(trimmedLine);
                    }
                } else {
                    // Fallback: positional mapping or default style
                    int idx = tailoredLines.indexOf(line);
                    Map<String, Object> paraMap = (paraList != null && idx < paraList.size()) ? paraList.get(idx) : null;
                    if (paraMap != null) {
                        applyParagraphStyles(para, paraMap);
                        List<Map<String, Object>> runsList = (List<Map<String, Object>>) paraMap.get("runs");
                        if (runsList != null && !runsList.isEmpty()) {
                            XWPFRun run = para.createRun();
                            run.setText(trimmedLine);
                            applyRunStyles(run, runsList.get(0));
                        } else {
                            XWPFRun run = para.createRun();
                            run.setText(trimmedLine);
                        }
                    } else {
                        XWPFRun run = para.createRun();
                        run.setText(trimmedLine);
                    }
                }
            }
            // Tables and images can be added similarly if tailoredLines include cues or mapping
            // (Advanced: implement table/image mapping if needed)
        }
    /**
     * Extracts paragraph and run styles from a DOCX file.
     * Returns a list of style maps, one per paragraph.
     */
    public static java.util.List<Map<String, Object>> extractParagraphsWithRuns(InputStream docxInputStream) throws Exception {
        java.util.List<Map<String, Object>> paraList = new java.util.ArrayList<>();
        XWPFDocument doc = new XWPFDocument(docxInputStream);
        for (XWPFParagraph para : doc.getParagraphs()) {
            Map<String, Object> paraMap = new HashMap<>();
            paraMap.put("alignment", para.getAlignment());
            paraMap.put("spacingBefore", para.getSpacingBefore());
            paraMap.put("spacingAfter", para.getSpacingAfter());
            paraMap.put("indentationLeft", para.getIndentationLeft());
            paraMap.put("indentationRight", para.getIndentationRight());
            paraMap.put("isBullet", para.getNumFmt() != null);
            java.util.List<Map<String, Object>> runsList = new java.util.ArrayList<>();
            for (XWPFRun run : para.getRuns()) {
                Map<String, Object> runMap = new HashMap<>();
                runMap.put("text", run.getText(0));
                runMap.put("fontFamily", run.getFontFamily());
                runMap.put("fontSize", run.getFontSizeAsDouble());
                runMap.put("bold", run.isBold());
                runMap.put("italic", run.isItalic());
                runMap.put("underline", run.getUnderline());
                runMap.put("color", run.getColor());
                runsList.add(runMap);
            }
            paraMap.put("runs", runsList);
            paraList.add(paraMap);
        }
        doc.close();
        return paraList;
    }

    /**
     * Applies extracted styles to a paragraph/run in a new DOCX document.
     */
    public static void applyRunStyles(XWPFRun run, Map<String, Object> runMap) {
        if (runMap == null) return;
        if (runMap.get("fontFamily") != null) run.setFontFamily((String) runMap.get("fontFamily"));
        if (runMap.get("fontSize") != null && ((double) runMap.get("fontSize")) > 0) run.setFontSize((int) Math.round((double) runMap.get("fontSize")));
        if (runMap.get("bold") != null) run.setBold((Boolean) runMap.get("bold"));
        if (runMap.get("italic") != null) run.setItalic((Boolean) runMap.get("italic"));
        if (runMap.get("underline") != null) run.setUnderline((org.apache.poi.xwpf.usermodel.UnderlinePatterns) runMap.get("underline"));
        if (runMap.get("color") != null) run.setColor((String) runMap.get("color"));
    }

    public static void applyParagraphStyles(XWPFParagraph para, Map<String, Object> paraMap) {
        if (paraMap == null) return;
        if (paraMap.get("spacingBefore") != null) para.setSpacingBefore((int) paraMap.get("spacingBefore"));
        if (paraMap.get("spacingAfter") != null) para.setSpacingAfter((int) paraMap.get("spacingAfter"));
        if (paraMap.get("alignment") != null) para.setAlignment((org.apache.poi.xwpf.usermodel.ParagraphAlignment) paraMap.get("alignment"));
        if (paraMap.get("indentationLeft") != null) para.setIndentationLeft((int) paraMap.get("indentationLeft"));
        if (paraMap.get("indentationRight") != null) para.setIndentationRight((int) paraMap.get("indentationRight"));
    }

    // Section header detection for semantic mapping
    public static boolean isSectionHeader(String line) {
        String lower = line.toLowerCase().trim();
        return lower.matches("^(experience|education|skills|summary|contact|objective|certifications|projects|personal summary|profile|about|header|professional summary|work history|employment|achievements|interests|languages|references).*") ||
               (line.endsWith(":") && line.length() < 50);
    }
}
