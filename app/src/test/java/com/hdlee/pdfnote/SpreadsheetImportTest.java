package com.hdlee.pdfnote;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.zip.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
public class SpreadsheetImportTest {
    @Test public void xlsKeepsCellsButRemovesPrintedBorders()throws Exception{File file=File.createTempFile("workbook-",".xls");try{try(HSSFWorkbook book=new HSSFWorkbook()){Sheet sheet=book.createSheet("Data");sheet.setPrintGridlines(true);Cell cell=sheet.createRow(0).createCell(0);cell.setCellValue("금융");CellStyle style=book.createCellStyle();style.setBorderBottom(BorderStyle.THIN);cell.setCellStyle(style);try(OutputStream out=new FileOutputStream(file)){book.write(out);}}SpreadsheetImport.prepare(file);try(InputStream in=new FileInputStream(file);HSSFWorkbook book=new HSSFWorkbook(in)){assertEquals("금융",book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());assertFalse(book.getSheetAt(0).isPrintGridlines());assertEquals(BorderStyle.NONE,book.getSheetAt(0).getRow(0).getCell(0).getCellStyle().getBorderBottom());}}finally{file.delete();}}
    @Test public void xlsxXmlKeepsFormulasAndDropsGridlines()throws Exception{String xml="<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView showGridLines=\"1\"/></sheetViews><sheetData><row r=\"1\"><c r=\"A1\"><f>SUM(B1:B2)</f><v>12</v></c></row></sheetData><pageMargins left=\".7\"/></worksheet>";String result=new String(SpreadsheetImport.cleanXml(xml.getBytes(StandardCharsets.UTF_8),false),StandardCharsets.UTF_8);assertTrue(result.contains("SUM(B1:B2)"));assertTrue(result.contains("showGridLines=\"0\""));assertTrue(result.contains("gridLines=\"0\""));assertTrue(result.indexOf("printOptions")<result.indexOf("pageMargins"));String styles="<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><borders><border><left style=\"thin\"/><bottom style=\"thin\"/></border></borders></styleSheet>";assertFalse(new String(SpreadsheetImport.cleanXml(styles.getBytes(StandardCharsets.UTF_8),true),StandardCharsets.UTF_8).contains("thin"));}
    @Test public void externalXmlEntitiesAreRejected()throws Exception{try{SpreadsheetImport.cleanXml("<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///private'>]><x>&a;</x>".getBytes(StandardCharsets.UTF_8),false);fail();}catch(IOException expected){}}
}
