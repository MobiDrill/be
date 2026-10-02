package com.mobidrill.backend.domain.manual;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

public final class ManualFileFixtures {

    private ManualFileFixtures() {
    }

    public static byte[] file(String extension) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        switch (extension) {
            case "pdf" -> {
                return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n"
                        .getBytes(StandardCharsets.US_ASCII);
            }
            case "ppt" -> {
                try (HSLFSlideShow slides = new HSLFSlideShow()) {
                    slides.createSlide();
                    slides.write(output);
                }
            }
            case "pptx" -> {
                try (XMLSlideShow slides = new XMLSlideShow()) {
                    slides.createSlide();
                    slides.write(output);
                }
            }
            case "docx" -> {
                try (XWPFDocument document = new XWPFDocument()) {
                    document.createParagraph().createRun().setText("교범 본문");
                    document.write(output);
                }
            }
            // 본문 파싱이 아닌 OLE 컨테이너의 형식 판별 경계를 검증한다.
            case "doc", "hwp" -> {
                try (POIFSFileSystem container = new POIFSFileSystem()) {
                    byte[] header = new byte[256];
                    if (extension.equals("hwp")) {
                        byte[] signature = "HWP Document File".getBytes(StandardCharsets.US_ASCII);
                        System.arraycopy(signature, 0, header, 0, signature.length);
                        header[35] = 5;
                        container.createDocument(new ByteArrayInputStream(header), "FileHeader");
                        container.createDocument(new ByteArrayInputStream(new byte[4]), "DocInfo");
                        container.getRoot().createDirectory("BodyText")
                                .createDocument("Section0", new ByteArrayInputStream(new byte[4]));
                    } else {
                        header[0] = (byte) 0xec;
                        header[1] = (byte) 0xa5;
                        container.createDocument(new ByteArrayInputStream(header), "WordDocument");
                    }
                    container.writeFilesystem(output);
                }
            }
            default -> throw new IllegalArgumentException(extension);
        }
        return output.toByteArray();
    }
}
