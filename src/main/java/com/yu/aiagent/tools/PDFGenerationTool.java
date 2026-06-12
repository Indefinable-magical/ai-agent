package com.yu.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.yu.aiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PDF 生成工具
 */
public class PDFGenerationTool {

    private static final List<String> FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/simsunb.ttf",
            "C:/Windows/Fonts/STXIHEI.TTF",
            "C:/Windows/Fonts/STSONG.TTF"
    );

    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String normalizedFileName = normalizeFileName(fileName);
        String filePath = fileDir + "/" + normalizedFileName;
        try {
            FileUtil.mkdir(fileDir);
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                document.setMargins(36, 36, 36, 36);
                document.setFont(createChineseFont());
                addMarkdownContent(document, normalizeContent(content));
            }
            return "PDF generated successfully to: " + filePath;
        } catch (Exception e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    private PdfFont createChineseFont() throws IOException {
        for (String fontPath : FONT_CANDIDATES) {
            String actualPath = fontPath.split(",", 2)[0];
            if (Files.exists(Path.of(actualPath))) {
                return PdfFontFactory.createFont(
                        fontPath,
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
                );
            }
        }
        return PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
    }

    private void addMarkdownContent(Document document, String content) {
        for (String line : content.split("\\R")) {
            String text = line.trim();
            if (text.isEmpty() || text.equals("---")) {
                continue;
            }

            boolean heading = text.startsWith("# ");
            boolean subHeading = text.startsWith("## ");
            boolean thirdHeading = text.startsWith("### ");

            Paragraph paragraph = new Paragraph(stripMarkdown(text))
                    .setMultipliedLeading(1.25f)
                    .setMarginBottom(6);

            if (heading) {
                paragraph.setFontSize(18).setMarginTop(8);
            } else if (subHeading) {
                paragraph.setFontSize(15).setMarginTop(8);
            } else if (thirdHeading) {
                paragraph.setFontSize(13).setMarginTop(6);
            } else if (text.startsWith("- ") || text.startsWith("* ")) {
                paragraph.setFirstLineIndent(12).setFontSize(11);
            } else {
                paragraph.setFontSize(11);
            }

            document.add(paragraph);
        }
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        content.codePoints()
                .filter(codePoint -> codePoint <= Character.MAX_VALUE)
                .filter(codePoint -> !Character.isISOControl(codePoint)
                        || codePoint == '\n'
                        || codePoint == '\r'
                        || codePoint == '\t')
                .forEach(result::appendCodePoint);
        return result.toString()
                .replace('✅', ' ')
                .replace('🎯', ' ')
                .replace('📅', ' ')
                .replace('🌐', ' ')
                .replace('💡', ' ')
                .replace('—', '-')
                .replace('–', '-')
                .replace('→', '-')
                .replace('≥', '>');
    }

    private String stripMarkdown(String text) {
        return text
                .replaceFirst("^#{1,6}\\s*", "")
                .replaceFirst("^[-*]\\s*", "· ")
                .replace("**", "")
                .replace("`", "")
                .replaceAll("\\[(.+?)]\\((.+?)\\)", "$1 ($2)");
    }

    private String normalizeFileName(String fileName) {
        String name = fileName == null || fileName.isBlank() ? "generated.pdf" : fileName;
        if (!name.toLowerCase().endsWith(".pdf")) {
            name += ".pdf";
        }
        return name;
    }
}
