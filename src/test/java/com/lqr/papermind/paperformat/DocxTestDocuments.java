package com.lqr.papermind.paperformat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 测试用docx文档生成器，用于创建各种格式的测试docx文件
 */
final class DocxTestDocuments {

    /** 测试页眉文字 */
    private static final String HEADER_TEXT = "某大学本科毕业论文";

    /** 私有构造器，防止实例化 */
    private DocxTestDocuments() {
    }

    /**
     * 创建模板docx文件（包含标题、正文、页眉页脚和页面设置）
     *
     * @param directory     临时目录
     * @param footerPageField 页脚是否包含PAGE字段
     * @return docx文件路径
     */
    static Path templateDocx(Path directory, boolean footerPageField) throws IOException {
        return writeDocx(
                directory.resolve("template.docx"),
                defaultStylesXml(),
                settingsXml(false),
                headerXml(HEADER_TEXT, true, null, null, null),
                footerXml(footerPageField, true),
                documentXml("""
                        <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>第一章 绪论</w:t></w:r></w:p>
                        <w:p><w:pPr><w:pStyle w:val="Normal"/></w:pPr><w:r><w:t>这是正文段落。</w:t></w:r></w:p>
                        """, pgMarTwips(1440, 1800, 1440, 1800, 851, 992, 0))
        );
    }

    /** 创建学生论文docx文件（带页脚页码） */
    static Path studentDocx(Path directory) throws IOException {
        return templateDocx(directory, true);
    }

    /**
     * 创建包含格式规范文本的docx文件
     *
     * @param directory 临时目录
     * @param conflict  是否模拟文本规则与OOXML规则的冲突
     * @return docx文件路径
     */
    static Path requirementTemplateDocx(Path directory, boolean conflict) throws IOException {
        String headerText = conflict ? "OOXML 页眉文本" : "人工智能与信息工程学院毕业设计";
        String margins = conflict
                ? pgMarTwips(1440, 1134, 1134, 1417, 851, 850, 283)
                : pgMarTwips(1134, 1134, 1134, 1417, 850, 850, 283);
        return writeDocx(
                directory.resolve(conflict ? "requirement-conflict.docx" : "requirement.docx"),
                defaultStylesXml(),
                settingsXml(true),
                headerXml(headerText, true, null, "宋体", 18),
                footerXml(true, true),
                documentXml("""
                        <w:p><w:r><w:t>一、基本要求</w:t></w:r></w:p>
                        <w:p><w:r><w:t>A4 纸，双面打印，对称页边距，上2cm，下2cm，内侧2.5cm，外侧2.0cm，装订线0.5cm。</w:t></w:r></w:p>
                        <w:p><w:r><w:t>页眉1.5cm，页脚1.5cm。</w:t></w:r></w:p>
                        <w:p><w:r><w:t>页眉设置为：居中，以小5号字宋体键入“人工智能与信息工程学院毕业设计”。</w:t></w:r></w:p>
                        <w:p><w:r><w:t>页脚设置为：插入页码，居中。</w:t></w:r></w:p>
                        <w:p><w:r><w:t>二、书写样式</w:t></w:r></w:p>
                        <w:p><w:r><w:t>正文采用固定值，16磅，段前、段后均为0磅。</w:t></w:r></w:p>
                        <w:p><w:r><w:t>所有数字、字母统一采用Times New Roman字体。</w:t></w:r></w:p>
                        <w:p><w:r><w:t>标题编号采用 1（一级标题）/ 1.1（二级标题）/ 1.1.1（三级标题）。</w:t></w:r></w:p>
                        <w:p><w:r><w:t>引用文献使用“[1]”方式注于正文相应处。</w:t></w:r></w:p>
                        <w:p><w:r><w:t>参考文献类型包括：专著[M]、论文集[C]、学位论文[D]、报告[R]、期刊[J]、标准[S]、专利[P]、报纸[N]、档案[A]。</w:t></w:r></w:p>
                        """, margins)
        );
    }

    /** 创建通过样式继承设置页眉对齐的docx文件 */
    static Path styleInheritedHeaderDocx(Path directory) throws IOException {
        return writeDocx(
                directory.resolve("style-inherited-header.docx"),
                stylesWithInheritedHeaderAlignment(),
                settingsXml(false),
                headerXml("样式继承页眉", false, "HeaderDerived", null, null),
                footerXml(false, true),
                documentXml("<w:p><w:r><w:t>正文</w:t></w:r></w:p>", pgMarTwips(1440, 1800, 1440, 1800, 851, 992, 0))
        );
    }

    /** 将XML部件写入docx ZIP文件 */
    private static Path writeDocx(Path path,
                                  String stylesXml,
                                  String settingsXml,
                                  String headerXml,
                                  String footerXml,
                                  String documentXml) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            entry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                      <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                      <Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>
                      <Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>
                      <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
                    </Types>
                    """);
            entry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            entry(zip, "word/_rels/document.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rIdHeader1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>
                      <Relationship Id="rIdFooter1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
                    </Relationships>
                    """);
            entry(zip, "word/styles.xml", stylesXml);
            entry(zip, "word/settings.xml", settingsXml);
            entry(zip, "word/header1.xml", headerXml);
            entry(zip, "word/footer1.xml", footerXml);
            entry(zip, "word/document.xml", documentXml);
        }
        return path;
    }

    /** 生成默认styles.xml（包含docDefaults、Normal、Heading1/2/3样式） */
    private static String defaultStylesXml() {
        return """
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:docDefaults>
                    <w:rPrDefault><w:rPr><w:rFonts w:ascii="Times New Roman" w:eastAsia="宋体"/><w:sz w:val="24"/></w:rPr></w:rPrDefault>
                    <w:pPrDefault><w:pPr><w:spacing w:line="480" w:lineRule="auto"/><w:jc w:val="both"/><w:ind w:firstLine="480"/></w:pPr></w:pPrDefault>
                  </w:docDefaults>
                  <w:style w:type="paragraph" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:rFonts w:eastAsia="宋体"/><w:sz w:val="24"/></w:rPr></w:style>
                  <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:pPr><w:jc w:val="center"/></w:pPr><w:rPr><w:b/><w:rFonts w:eastAsia="黑体"/><w:sz w:val="32"/></w:rPr></w:style>
                  <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:rPr><w:rFonts w:eastAsia="黑体"/><w:sz w:val="28"/></w:rPr></w:style>
                  <w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:rPr><w:rFonts w:eastAsia="黑体"/><w:sz w:val="26"/></w:rPr></w:style>
                </w:styles>
                """;
    }

    /** 生成带样式继承的页眉样式styles.xml */
    private static String stylesWithInheritedHeaderAlignment() {
        return """
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:style w:type="paragraph" w:styleId="HeaderBase"><w:pPr><w:jc w:val="center"/></w:pPr></w:style>
                  <w:style w:type="paragraph" w:styleId="HeaderDerived"><w:basedOn w:val="HeaderBase"/><w:rPr><w:rFonts w:eastAsia="宋体"/><w:sz w:val="18"/></w:rPr></w:style>
                </w:styles>
                """;
    }

    /** 生成settings.xml（可选对称页边距设置） */
    private static String settingsXml(boolean mirrorMargins) {
        return mirrorMargins
                ? "<w:settings xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:mirrorMargins/></w:settings>"
                : "<w:settings xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>";
    }

    /** 生成页眉XML（支持文字、对齐、样式、字体、字号配置） */
    private static String headerXml(String text, boolean directCentered, String styleId, String eastAsiaFont, Integer halfPointSize) {
        String pStyle = styleId == null ? "" : "<w:pStyle w:val=\"" + styleId + "\"/>";
        String jc = directCentered ? "<w:jc w:val=\"center\"/>" : "";
        String fonts = eastAsiaFont == null ? "" : "<w:rFonts w:eastAsia=\"" + eastAsiaFont + "\"/>";
        String size = halfPointSize == null ? "" : "<w:sz w:val=\"" + halfPointSize + "\"/>";
        String rPr = fonts.isBlank() && size.isBlank() ? "" : "<w:rPr>" + fonts + size + "</w:rPr>";
        return """
                <w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:p><w:pPr>%s%s</w:pPr><w:r>%s<w:t>%s</w:t></w:r></w:p>
                </w:hdr>
                """.formatted(pStyle, jc, rPr, text);
    }

    /** 生成页脚XML（可选PAGE字段页码和居中设置） */
    private static String footerXml(boolean footerPageField, boolean centered) {
        String jc = centered ? "<w:pPr><w:jc w:val=\"center\"/></w:pPr>" : "";
        String content = footerPageField
                ? "<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText> PAGE </w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"end\"/></w:r>"
                : "<w:r><w:t>1</w:t></w:r>";
        return """
                <w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:p>%s%s</w:p>
                </w:ftr>
                """.formatted(jc, content);
    }

    /** 生成document.xml（包含段落内容和页面设置） */
    private static String documentXml(String paragraphs, String pgMar) {
        return """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <w:body>
                    %s
                    <w:sectPr>
                      <w:headerReference w:type="default" r:id="rIdHeader1"/>
                      <w:footerReference w:type="default" r:id="rIdFooter1"/>
                      <w:pgSz w:w="11906" w:h="16838"/>
                      %s
                    </w:sectPr>
                  </w:body>
                </w:document>
                """.formatted(paragraphs, pgMar);
    }

    /** 生成页面边距XML（twips单位） */
    private static String pgMarTwips(int top, int right, int bottom, int left, int header, int footer, int gutter) {
        return "<w:pgMar w:top=\"%d\" w:right=\"%d\" w:bottom=\"%d\" w:left=\"%d\" w:header=\"%d\" w:footer=\"%d\" w:gutter=\"%d\"/>"
                .formatted(top, right, bottom, left, header, footer, gutter);
    }

    /** 向ZIP流中写入一个XML条目 */
    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}