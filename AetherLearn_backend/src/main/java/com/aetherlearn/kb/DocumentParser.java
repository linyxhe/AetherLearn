package com.aetherlearn.kb;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 课程资料文档解析器（F-KB-02 文档解析与切片）
 * <p>支持：PDF（Apache PDFBox）、Word .docx（Apache POI）、TXT / Markdown（纯文本直读）。
 * 统一抽取为纯文本后返回，供后续切片处理。</p>
 */
@Slf4j
@Component
public class DocumentParser {

    /**
     * 解析上传文件为纯文本
     *
     * @param file     上传的文件
     * @param fileType 文件类型：pdf / docx / md / txt（不区分大小写）
     * @return 抽取出的纯文本
     */
    public String parse(MultipartFile file, String fileType) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return parse(in, fileType);
        }
    }

    /**
     * 解析输入流为纯文本，供已上传文件二次解析复用。
     *
     * @param in       文件输入流
     * @param fileType 文件类型：pdf / docx / md / txt
     * @return 抽取出的纯文本
     */
    public String parse(InputStream in, String fileType) throws IOException {
        String type = (fileType == null ? "" : fileType).toLowerCase();
        switch (type) {
            case "pdf":
                return parsePdf(in);
            case "docx":
                return parseDocx(in);
            case "md":
            case "txt":
            case "":
                return parseText(in);
            default:
                throw new IllegalArgumentException("不支持的文件类型：" + fileType);
        }
    }

    /** PDF 解析（PDFBox） */
    private String parsePdf(InputStream in) throws IOException {
        try (PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    /** Word(.docx) 解析（POI），含段落与表格文本 */
    private String parseDocx(InputStream in) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                if (p.getText() != null && !p.getText().isBlank()) {
                    sb.append(p.getText()).append("\n");
                }
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        sb.append(cell.getText()).append(" ");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        }
    }

    /** TXT / Markdown 纯文本直读 */
    private String parseText(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
