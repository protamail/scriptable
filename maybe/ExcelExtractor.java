package model.extra;

import java.io.InputStream;
import java.io.File;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;
import org.mozilla.javascript.Context;
import org.scriptable.RhinoHttpRequest;
import org.scriptable.ScriptableMap;

import org.xml.sax.SAXException;

public final class ExcelExtractor {

    public static List<ScriptableMap> getData(InputStream file, int... headRowNums) throws Exception {
        return getData(OPCPackage.open(file), headRowNums);
    }

    public static List<ScriptableMap> getData(File file, int... headRowNums) throws Exception {
        return getData(OPCPackage.open(file, PackageAccess.READ), headRowNums);
    }

    public static List<ScriptableMap> getData(String file, int... headRowNums) throws Exception {
        return getData(OPCPackage.open(file, PackageAccess.READ), headRowNums);
    }

    @SuppressWarnings("unchecked")
    private static List<ScriptableMap> getData(OPCPackage p, int... headRowNums) throws Exception {
        InputStream is = null;
        ArrayList<ScriptableMap> result = new ArrayList<ScriptableMap>(64);

        try {

            XSSFReader r = new XSSFReader(p);
            SharedStringsTable sst = r.getSharedStringsTable();
            
            XMLReader parser = XMLReaderFactory.createXMLReader();// use default reader "org.apache.xerces.parsers.SAXParser");
            SheetHandler sheetHandler = new SheetHandler(sst, result, headRowNums);

            parser.setContentHandler(new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String name, Attributes attributes)
                    throws SAXException {
                    if (name.equals("sheet")) {
                        sheetHandler.setSheetName(attributes.getValue("name"));
                    }
                }
            });

            is = r.getWorkbookData();
            parser.parse(new InputSource(is));
            is.close();
            
            parser.setContentHandler(sheetHandler);
            Iterator<InputStream> sheets = r.getSheetsData();
            while (sheets.hasNext()) {
                is = sheets.next();
                InputSource sheetSource = new InputSource(is);
                sheetHandler.newSheet();
                parser.parse(sheetSource);
                is.close();
            }
        }
        finally {

            if (is != null)
                is.close();
            p.revert();
            p = null;
        }

        return (List<ScriptableMap>)
            Context.getCurrentContext().newArray(RhinoHttpRequest.getGlobalScope(), result.toArray());
    }

    private static final class SheetHandler extends DefaultHandler {
        SharedStringsTable sst;
        String value = null;
        Double doubleValue = null;
        String colRef = null;
        HashMap<String, String> headRow = null;
        ScriptableMap row = null;
        List<ScriptableMap> result = null;
        int rowNum = 0;
        int rowNumLength = 0;
        int headRowNum = 0;
        int sheetIdx = -1;
        int[] headRowNums = null;
        boolean valueIsString;
        boolean valueIsDouble;
        String[] sstCache;
        String sheetName = "Sheet1";
        
        private SheetHandler(SharedStringsTable sst, List<ScriptableMap> result, int... headRowNums) {
            this.sst = sst;
            this.result = result;
            if (sst.getUniqueCount() > 10000000)
                throw new RuntimeException("Too many entries in SST");
            sstCache = new String[sst.getUniqueCount()];
            this.headRowNums = headRowNums == null? new int[0] : headRowNums;
            headRowNum = headRowNums.length > 0? headRowNums[0] : 1;
        }
        
        public void setSheetName(String v) {
            sheetName = v == null? "Sheet1" : v;
        }

        public void newSheet() {
            sheetIdx++;
            headRowNum = sheetIdx < headRowNums.length? headRowNums[sheetIdx] : 1;
            row = null;
            headRow = null;
        }

        @Override
        public void startElement(String uri, String localName, String name,
                Attributes attributes) throws SAXException {
            if (name.equals("c")) { // cell element
                colRef = attributes.getValue("r");
                colRef = colRef.substring(0, colRef.length()-rowNumLength);
                String t = attributes.getValue("t");
                valueIsString = t != null && t.equals("s"); // is value an index into SST array (strings)
                valueIsDouble = t == null;
            }
            else if (name.equals("row")) { // row started
                String r = attributes.getValue("r");
                rowNum = Integer.parseInt(r);
                rowNumLength = r.length();
                if (rowNum >= headRowNum) {
                    if (headRow == null) {
                        row = null;
                        headRow = new HashMap<String, String>();
                    }
                    else
                        row = new ScriptableMap((int)(headRow.size()*1.5), .75f, false);
                }
            }

            value = null;
            doubleValue = null;
        }
        
        @Override
        public void endElement(String uri, String localName, String name) throws SAXException {
            if (name.equals("v")) { // value is ready
                if (value != null) {
                    if (valueIsDouble) {
                        doubleValue = Double.parseDouble(value);
                    }
                    else if (valueIsString) {
                        int i = Integer.parseInt(value);
                        if (sstCache[i] != null)
                            value = sstCache[i];
                        else {
                            value = new XSSFRichTextString(sst.getEntryAt(i)).toString();
                            sstCache[i] = value;
                        }
                        valueIsString = false;
                    }
                }
                if (headRow != null) {
                    if (row != null) {
                        String k = headRow.get(colRef);
                        row.put(k == null? colRef : k, doubleValue != null? doubleValue : value);
                    }
                    else {
                        String colKey = doubleValue != null? doubleValue.toString() : value != null? value : "";
                        headRow.put(colRef, !colKey.equals("")? colKey : colRef);
                    }
                }
            }
            else if (name.equals("row")) { // row is done
                if (row != null) {
                    //@SuppressWarnings("unchecked")
                    row.put("__sheet", sheetName);
                    result.add(row);
                    row = null;
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (value == null)
                value = new String(ch, start, length);
            else
                value += new String(ch, start, length);
        }
    }
}

