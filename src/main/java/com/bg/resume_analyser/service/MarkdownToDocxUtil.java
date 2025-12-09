package com.bg.resume_analyser.service;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import org.apache.poi.xwpf.usermodel.*;

import java.io.ByteArrayOutputStream;

public class MarkdownToDocxUtil {
    public static byte[] convertMarkdownToDocx(String markdown) throws Exception {
        XWPFDocument doc = new XWPFDocument();
        Parser parser = Parser.builder().build();
        Node document = parser.parse(markdown);

        for (Node node : document.getChildren()) {
            if (node instanceof Heading) {
                Heading heading = (Heading) node;
                XWPFParagraph para = doc.createParagraph();
                if (heading.getLevel() == 1) {
                    para.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun run = para.createRun();
                    run.setBold(true);
                    run.setFontSize(18);
                    run.setText(heading.getText().toString());
                } else if (heading.getLevel() == 2) {
                    para.setAlignment(ParagraphAlignment.LEFT);
                    XWPFRun run = para.createRun();
                    run.setBold(true);
                    run.setFontSize(14);
                    run.setText(heading.getText().toString());
                } else if (heading.getLevel() == 3) {
                    para.setAlignment(ParagraphAlignment.LEFT);
                    XWPFRun run = para.createRun();
                    run.setBold(true);
                    run.setFontSize(12);
                    run.setText(heading.getText().toString());
                }
            } else if (node instanceof BulletList) {
                for (Node item : node.getChildren()) {
                    if (item instanceof ListItem) {
                        XWPFParagraph para = doc.createParagraph();
                        para.setAlignment(ParagraphAlignment.LEFT);
                        para.setNumID(java.math.BigInteger.valueOf(1)); // enables bullet
                        XWPFRun run = para.createRun();
                        run.setFontSize(10);
                        run.setText(item.getFirstChild().getChars().toString());
                    }
                }
            } else if (node instanceof Paragraph) {
                XWPFParagraph para = doc.createParagraph();
                para.setAlignment(ParagraphAlignment.LEFT);
                XWPFRun run = para.createRun();
                run.setFontSize(10);
                for (Node child : node.getChildren()) {
                    if (child instanceof StrongEmphasis) {
                        run.setBold(true);
                        run.setText(child.getChars().toString());
                        run.setBold(false);
                    } else {
                        run.setText(child.getChars().toString());
                    }
                }
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        doc.close();
        return out.toByteArray();
    }
}
