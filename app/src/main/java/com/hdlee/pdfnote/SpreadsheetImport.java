package com.hdlee.pdfnote;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

/** Prepares only a temporary conversion copy. Formulas, values and graphics stay in the workbook. */
final class SpreadsheetImport {
    static void prepare(File file)throws IOException{
        File prepared=new File(file.getParentFile(),file.getName()+".prepared");
        try{
            if(file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"))xlsx(file,prepared);
            else try(InputStream input=new FileInputStream(file);HSSFWorkbook book=new HSSFWorkbook(input)){
                for(int i=0;i<book.getNumCellStyles();i++){CellStyle style=book.getCellStyleAt(i);style.setBorderBottom(BorderStyle.NONE);style.setBorderTop(BorderStyle.NONE);style.setBorderLeft(BorderStyle.NONE);style.setBorderRight(BorderStyle.NONE);}
                for(Sheet sheet:book){sheet.setPrintGridlines(false);sheet.setDisplayGridlines(false);sheet.setFitToPage(true);sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);}
                try(OutputStream out=new FileOutputStream(prepared)){book.write(out);}
            }
            try(InputStream in=new FileInputStream(prepared);OutputStream out=new FileOutputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        }catch(Exception error){throw new IOException("엑셀 격자선 정리 실패: "+error.getMessage(),error);}finally{prepared.delete();}
    }
    private static void xlsx(File input,File output)throws Exception{
        try(ZipInputStream in=new ZipInputStream(new FileInputStream(input));ZipOutputStream out=new ZipOutputStream(new FileOutputStream(output))){ZipEntry entry;byte[] buffer=new byte[65536];long total=0;while((entry=in.getNextEntry())!=null){out.putNextEntry(new ZipEntry(entry.getName()));boolean transform=entry.getName().equals("xl/styles.xml")||entry.getName().matches("xl/worksheets/sheet[0-9]+\\.xml");ByteArrayOutputStream xml=transform?new ByteArrayOutputStream():null;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>256L*1024*1024||(xml!=null&&xml.size()+n>32*1024*1024))throw new IOException("엑셀 내용이 너무 큽니다");if(xml!=null)xml.write(buffer,0,n);else out.write(buffer,0,n);}if(xml!=null)out.write(cleanXml(xml.toByteArray(),entry.getName().equals("xl/styles.xml")));out.closeEntry();}}
    }
    static byte[] cleanXml(byte[] bytes,boolean styles)throws Exception{
        if(new String(bytes,StandardCharsets.UTF_8).replace("\u0000","").toUpperCase(java.util.Locale.ROOT).contains("<!DOCTYPE"))throw new IOException("지원하지 않는 XML 선언");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setExpandEntityReferences(false);DocumentBuilder builder=factory.newDocumentBuilder();builder.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("외부 XML 참조 금지");});Document doc=builder.parse(new ByteArrayInputStream(bytes));
        if(styles){NodeList borders=doc.getElementsByTagNameNS("*","border");for(int i=0;i<borders.getLength();i++){Node border=borders.item(i);while(border.hasChildNodes())border.removeChild(border.getFirstChild());}}
        else{
            NodeList views=doc.getElementsByTagNameNS("*","sheetView");for(int i=0;i<views.getLength();i++)((Element)views.item(i)).setAttribute("showGridLines","0");
            Element root=doc.getDocumentElement();NodeList options=doc.getElementsByTagNameNS("*","printOptions");Element print;if(options.getLength()>0)print=(Element)options.item(0);else{String prefix=root.getPrefix();print=doc.createElementNS(root.getNamespaceURI(),(prefix==null?"":prefix+":")+"printOptions");Node before=null;String[] later={"pageMargins","pageSetup","headerFooter","rowBreaks","colBreaks","customProperties","cellWatches","ignoredErrors","smartTags","drawing","legacyDrawing","legacyDrawingHF","picture","oleObjects","controls","webPublishItems","tableParts","extLst"};for(Node node=root.getFirstChild();node!=null;node=node.getNextSibling())if(java.util.Arrays.asList(later).contains(node.getLocalName())){before=node;break;}root.insertBefore(print,before);}print.setAttribute("gridLines","0");print.setAttribute("gridLinesSet","1");
        }
        ByteArrayOutputStream out=new ByteArrayOutputStream();Transformer transformer=TransformerFactory.newInstance().newTransformer();transformer.setOutputProperty(OutputKeys.ENCODING,"UTF-8");transformer.transform(new DOMSource(doc),new StreamResult(out));return out.toByteArray();
    }
}
