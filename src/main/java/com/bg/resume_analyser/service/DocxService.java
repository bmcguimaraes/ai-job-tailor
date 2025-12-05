package com.bg.resume_analyser.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class DocxService {

    public byte[] generateDocx(String resumeText) throws IOException {
        XWPFDocument document = new XWPFDocument();

        // Split resume by sections (assumes resume has clear structure with newlines)
        String[] lines = resumeText.split("\n");

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                // Add spacing for empty lines
                document.createParagraph();
            } else if (isSectionHeader(line)) {
                // Section header (bold)
                XWPFParagraph para = document.createParagraph();
                para.setSpacingBefore(200);
                para.setSpacingAfter(100);
                XWPFRun run = para.createRun();
                run.setText(line.trim());
                run.setBold(true);
                run.setFontSize(12);
            } else if (line.trim().startsWith("•") || line.trim().startsWith("-")) {
                // Bullet point
                XWPFParagraph para = document.createParagraph();
                para.setIndentationLeft(720); // Indent bullet
                XWPFRun run = para.createRun();
                run.setText(line.trim().replaceFirst("^[•-]\\s*", ""));
                run.setFontSize(11);
            } else {
                // Regular text
                XWPFParagraph para = document.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(line.trim());
                run.setFontSize(11);
            }
        }

        // Write to bytes
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
