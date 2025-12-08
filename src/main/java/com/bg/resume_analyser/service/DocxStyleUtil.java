package com.bg.resume_analyser.service;

import org.apache.poi.xwpf.usermodel.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class DocxStyleUtil {
    /**
     * Extracts paragraph and run styles from a DOCX file.
     * Returns a map of style properties for reuse.
     */
    public static Map<String, Object> extractStyles(InputStream docxInputStream) throws Exception {
        Map<String, Object> styleMap = new HashMap<>();
        XWPFDocument doc = new XWPFDocument(docxInputStream);
        // Example: extract default font, size, and paragraph spacing from first paragraph/run
        if (!doc.getParagraphs().isEmpty()) {
            XWPFParagraph para = doc.getParagraphs().get(0);
            XWPFRun run = para.getRuns().isEmpty() ? null : para.getRuns().get(0);
            if (run != null) {
                styleMap.put("fontFamily", run.getFontFamily());
                // Use getFontSizeAsDouble() to avoid deprecated getFontSize()
                double fontSize = run.getFontSizeAsDouble();
                styleMap.put("fontSize", fontSize > 0 ? fontSize : null);
                styleMap.put("bold", run.isBold());
                styleMap.put("italic", run.isItalic());
            }
            styleMap.put("spacingBefore", para.getSpacingBefore());
            styleMap.put("spacingAfter", para.getSpacingAfter());
            styleMap.put("alignment", para.getAlignment());
        }
        doc.close();
        return styleMap;
    }

    /**
     * Applies extracted styles to a paragraph/run in a new DOCX document.
     */
    public static void applyStyles(XWPFParagraph para, XWPFRun run, Map<String, Object> styleMap) {
        if (styleMap == null) return;
        if (styleMap.get("fontFamily") != null) run.setFontFamily((String) styleMap.get("fontFamily"));
        if (styleMap.get("fontSize") != null && ((double) styleMap.get("fontSize")) > 0) run.setFontSize((int) Math.round((double) styleMap.get("fontSize")));
        if (styleMap.get("bold") != null) run.setBold((Boolean) styleMap.get("bold"));
        if (styleMap.get("italic") != null) run.setItalic((Boolean) styleMap.get("italic"));
        if (styleMap.get("spacingBefore") != null) para.setSpacingBefore((int) styleMap.get("spacingBefore"));
        if (styleMap.get("spacingAfter") != null) para.setSpacingAfter((int) styleMap.get("spacingAfter"));
        if (styleMap.get("alignment") != null) para.setAlignment((ParagraphAlignment) styleMap.get("alignment"));
    }
}
