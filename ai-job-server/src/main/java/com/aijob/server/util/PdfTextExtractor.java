package com.aijob.server.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;

public class PdfTextExtractor {

    private PdfTextExtractor() {
    }

    public static String extract(Path filePath) {
        return extractDetailed(filePath).text();
    }

    public static PdfExtractResult extractDetailed(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            int pageCount = document.getNumberOfPages();
            return new PdfExtractResult(
                    text == null ? "" : text.trim(),
                    pageCount
            );
        } catch (IOException e) {
            throw new RuntimeException("PDF文本提取失败", e);
        }
    }

    public record PdfExtractResult(String text, int pageCount) {
    }
}
