package tso.usmc.jira.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility to generate native OpenXML Excel (.xlsx) workbooks without external dependencies.
 * Uses standard java.util.zip and OpenXML SpreadsheetML format.
 */
public class ExcelExportUtil {

    public static final int STYLE_NORMAL = 0;
    public static final int STYLE_HEADER = 1;
    public static final int STYLE_YELLOW = 2;

    public static class SheetData {
        private final String sheetName;
        private final List<String> headers;
        private final List<List<String>> rows;
        private final List<List<Integer>> cellStyles;

        public SheetData(String sheetName, List<String> headers) {
            this.sheetName = sheetName;
            this.headers = new ArrayList<>(headers);
            this.rows = new ArrayList<>();
            this.cellStyles = new ArrayList<>();
        }

        public void addRow(List<String> rowValues) {
            addRow(rowValues, null);
        }

        public void addRow(List<String> rowValues, List<Integer> styles) {
            rows.add(new ArrayList<>(rowValues));
            if (styles != null) {
                cellStyles.add(new ArrayList<>(styles));
            } else {
                List<Integer> defaultStyles = new ArrayList<>();
                for (int i = 0; i < rowValues.size(); i++) {
                    defaultStyles.add(STYLE_NORMAL);
                }
                cellStyles.add(defaultStyles);
            }
        }

        public String getSheetName() {
            return sheetName;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public List<List<String>> getRows() {
            return rows;
        }

        public List<List<Integer>> getCellStyles() {
            return cellStyles;
        }
    }

    /**
     * Export multiple sheets to an .xlsx file.
     *
     * @param targetFile the destination .xlsx file
     * @param sheets list of sheets to write
     * @throws IOException on write error
     */
    public static void exportToFile(File targetFile, List<SheetData> sheets) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(targetFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {

            // 1. [Content_Types].xml
            writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml(sheets.size()));

            // 2. _rels/.rels
            writeZipEntry(zos, "_rels/.rels", buildRootRelsXml());

            // 3. xl/workbook.xml
            writeZipEntry(zos, "xl/workbook.xml", buildWorkbookXml(sheets));

            // 4. xl/_rels/workbook.xml.rels
            writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sheets.size()));

            // 5. xl/styles.xml
            writeZipEntry(zos, "xl/styles.xml", buildStylesXml());

            // 6. xl/worksheets/sheetN.xml
            for (int i = 0; i < sheets.size(); i++) {
                SheetData sheet = sheets.get(i);
                String sheetXml = buildWorksheetXml(sheet);
                writeZipEntry(zos, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml);
            }

            zos.finish();
        }
    }

    private static void writeZipEntry(ZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        zos.write(bytes, 0, bytes.length);
        zos.closeEntry();
    }

    private static String buildContentTypesXml(int sheetCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n");
        sb.append("  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n");
        sb.append("  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n");
        sb.append("  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n");
        sb.append("  <Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>\n");
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("  <Override PartName=\"/xl/worksheets/sheet").append(i)
              .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n");
        }
        sb.append("</Types>");
        return sb.toString();
    }

    private static String buildRootRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n" +
                "</Relationships>";
    }

    private static String buildWorkbookXml(List<SheetData> sheets) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n");
        sb.append("  <sheets>\n");
        for (int i = 0; i < sheets.size(); i++) {
            String name = escapeXml(sheets.get(i).getSheetName());
            sb.append("    <sheet name=\"").append(name)
              .append("\" sheetId=\"").append(i + 1)
              .append("\" r:id=\"rId").append(i + 1).append("\"/>\n");
        }
        sb.append("  </sheets>\n");
        sb.append("</workbook>");
        return sb.toString();
    }

    private static String buildWorkbookRelsXml(int sheetCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n");
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("  <Relationship Id=\"rId").append(i)
              .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
              .append(i).append(".xml\"/>\n");
        }
        sb.append("  <Relationship Id=\"rId").append(sheetCount + 1)
          .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n");
        sb.append("</Relationships>");
        return sb.toString();
    }

    private static String buildStylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n" +
                "  <fonts count=\"2\">\n" +
                "    <font>\n" +
                "      <sz val=\"11\"/>\n" +
                "      <name val=\"Calibri\"/>\n" +
                "      <family val=\"2\"/>\n" +
                "    </font>\n" +
                "    <font>\n" +
                "      <b/>\n" +
                "      <sz val=\"11\"/>\n" +
                "      <name val=\"Calibri\"/>\n" +
                "      <family val=\"2\"/>\n" +
                "    </font>\n" +
                "  </fonts>\n" +
                "  <fills count=\"4\">\n" +
                "    <fill><patternFill patternType=\"none\"/></fill>\n" +
                "    <fill><patternFill patternType=\"gray125\"/></fill>\n" +
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD9E1F2\"/><bgColor indexed=\"64\"/></patternFill></fill>\n" +
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFFF00\"/><bgColor indexed=\"64\"/></patternFill></fill>\n" +
                "  </fills>\n" +
                "  <borders count=\"2\">\n" +
                "    <border><left/><right/><top/><bottom/><diagonal/></border>\n" +
                "    <border>\n" +
                "      <left style=\"thin\"><color rgb=\"FFD4D4D4\"/></left>\n" +
                "      <right style=\"thin\"><color rgb=\"FFD4D4D4\"/></right>\n" +
                "      <top style=\"thin\"><color rgb=\"FFD4D4D4\"/></top>\n" +
                "      <bottom style=\"thin\"><color rgb=\"FFD4D4D4\"/></bottom>\n" +
                "      <diagonal/>\n" +
                "    </border>\n" +
                "  </borders>\n" +
                "  <cellStyleXfs count=\"1\">\n" +
                "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/>\n" +
                "  </cellStyleXfs>\n" +
                "  <cellXfs count=\"3\">\n" +
                "    <!-- 0: STYLE_NORMAL -->\n" +
                "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"/>\n" +
                "    <!-- 1: STYLE_HEADER -->\n" +
                "    <xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>\n" +
                "    <!-- 2: STYLE_YELLOW -->\n" +
                "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFill=\"1\" applyBorder=\"1\"/>\n" +
                "  </cellXfs>\n" +
                "</styleSheet>";
    }

    private static String buildWorksheetXml(SheetData sheet) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");

        // Column widths
        List<String> headers = sheet.getHeaders();
        int colCount = headers.size();
        int[] maxColLens = new int[colCount];
        for (int c = 0; c < colCount; c++) {
            maxColLens[c] = headers.get(c) != null ? headers.get(c).length() : 0;
            if ("Notes".equalsIgnoreCase(headers.get(c))) {
                maxColLens[c] = Math.max(maxColLens[c], 32);
            }
        }

        for (List<String> row : sheet.getRows()) {
            for (int c = 0; c < colCount && c < row.size(); c++) {
                String val = row.get(c);
                if (val != null && val.length() > maxColLens[c]) {
                    maxColLens[c] = val.length();
                }
            }
        }

        sb.append("  <cols>\n");
        for (int c = 0; c < colCount; c++) {
            int width = Math.min(Math.max(maxColLens[c] + 4, 12), 65);
            sb.append("    <col min=\"").append(c + 1)
              .append("\" max=\"").append(c + 1)
              .append("\" width=\"").append(width)
              .append("\" customWidth=\"1\"/>\n");
        }
        sb.append("  </cols>\n");

        sb.append("  <sheetData>\n");

        // Row 1: Headers
        sb.append("    <row r=\"1\">\n");
        for (int c = 0; c < colCount; c++) {
            String colRef = getColumnRef(c);
            String cellRef = colRef + "1";
            String headerText = escapeXml(headers.get(c));
            sb.append("      <c r=\"").append(cellRef)
              .append("\" s=\"").append(STYLE_HEADER)
              .append("\" t=\"inlineStr\"><is><t>").append(headerText).append("</t></is></c>\n");
        }
        sb.append("    </row>\n");

        // Data Rows
        List<List<String>> rows = sheet.getRows();
        List<List<Integer>> styles = sheet.getCellStyles();
        for (int r = 0; r < rows.size(); r++) {
            int rowNum = r + 2;
            List<String> row = rows.get(r);
            List<Integer> rowStyles = (r < styles.size()) ? styles.get(r) : null;

            sb.append("    <row r=\"").append(rowNum).append("\">\n");
            for (int c = 0; c < colCount; c++) {
                String colRef = getColumnRef(c);
                String cellRef = colRef + rowNum;
                String val = (c < row.size() && row.get(c) != null) ? row.get(c) : "";
                int style = (rowStyles != null && c < rowStyles.size()) ? rowStyles.get(c) : STYLE_NORMAL;

                if (val.isEmpty()) {
                    if (style != STYLE_NORMAL) {
                        sb.append("      <c r=\"").append(cellRef).append("\" s=\"").append(style).append("\"/>\n");
                    } else {
                        sb.append("      <c r=\"").append(cellRef).append("\"/>\n");
                    }
                } else {
                    sb.append("      <c r=\"").append(cellRef)
                      .append("\" s=\"").append(style)
                      .append("\" t=\"inlineStr\"><is><t>")
                      .append(escapeXml(val))
                      .append("</t></is></c>\n");
                }
            }
            sb.append("    </row>\n");
        }

        sb.append("  </sheetData>\n");
        sb.append("</worksheet>");
        return sb.toString();
    }

    public static String getColumnRef(int colIndex) {
        StringBuilder sb = new StringBuilder();
        colIndex++; // 1-based
        while (colIndex > 0) {
            int rem = (colIndex - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            colIndex = (colIndex - 1) / 26;
        }
        return sb.toString();
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;"); break;
                case '<':  sb.append("&lt;"); break;
                case '>':  sb.append("&gt;"); break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    // Filter out invalid XML 1.0 characters
                    if (c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD)) {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
