package AceJet;

//Author:       Mingxuan Di
//Date:         AUgust 2015

import java.util.*;
import java.io.*;
import Jet.Tipster.*;
import jxl.write.*;
import jxl.write.Number;
import jxl.*;
import jxl.write.biff.RowsExceededException;

/**
 *  EventTypeAnalysis implements the analysis of event subtype
 *  in corpus 
 */

public class EventTypeAnalysis {
	
	static String textDirectory;
	static String textExtension;
	static String keyApfDirectory;
	static String keyApfExtension;
	static Map<String, Integer> store_byDoc = new HashMap<String, Integer> ();
	static Map<String, Integer> store_byType = new HashMap<String, Integer> ();
	
	public static void main(String[] args) throws IOException {
		
		String docListFile = args[0];
		textDirectory = args[1];
		textExtension = args[2];
		keyApfDirectory = args[3];
		keyApfExtension = args[4];
		
		BufferedReader docListReader = new BufferedReader(new FileReader (docListFile));	//	input the file
		String docName;	
		while ((docName = docListReader.readLine()) != null)
		{
			//docName = getFileNameNoEx(docName);	//remove the extension of the file
			processDocument (docName);
		}
		System.out.println("Calculate by doc: \n" + store_byDoc);	
		writeExcel("C:/Users/v-mingdi/Desktop/Record_byDoc.xls",store_byDoc);
		System.out.println("Calculate by Type: \n" + store_byType);	
		writeExcel("C:/Users/v-mingdi/Desktop/Record_byType.xls",store_byType);
	}
	
	public static void processDocument (String docName) {

		String textFileName = textDirectory + "/" + docName + "." + textExtension;
		ExternalDocument doc = new ExternalDocument("sgml", textFileName);	//creates a new external document associated with file 'fileName'.  The format of the file is given by 'format'.
		doc.setAllTags(true);
		doc.open();
		
		Set<String> keyTriggers = new HashSet<String>();
		String keyApfFileName = keyApfDirectory + "/" + docName + "." + keyApfExtension;
		readApf (textFileName, keyApfFileName, keyTriggers);
		Iterator<String> key = keyTriggers.iterator();
		while(key.hasNext()){
			String subtype = key.next();
			Integer freq = store_byDoc.get(subtype);
			store_byDoc.put(subtype, freq == null ? 1 : freq + 1);
		}
	}
	
	static void readApf (String textFileName, String apfFileName, Set<String> triggers) {
        AceDocument aceDoc = new AceDocument(textFileName, apfFileName);	//very important.creat structure data of text based on text and annotation,
		for (AceEvent event : aceDoc.events) {	// traverse event in aceDoc. just event, may include multiple event mention
			String eType = event.type + ":" + event.subtype; //record type and subtype e.g, life.die:
			for (AceEventMention mention : event.mentions) {	//traverse all event mention in event
				triggers.add(eType);
				Integer freq2 = store_byType.get(eType);
				store_byType.put(eType, freq2 == null ? 1 : freq2 + 1);
			}
		} 
	}

    public static String getFileNameNoEx(String filename) {   
        if ((filename != null) && (filename.length() > 0)) {   
            int dot = filename.lastIndexOf('.');   
            if ((dot >-1) && (dot < (filename.length()))) {   
                return filename.substring(0, dot);   
            }   
        }   
        return filename;   
    } 
    
    public static void writeExcel(String fileName, Map<String,Integer> store){
        WritableWorkbook wwb = null;
        try {
            //First, we can apply method of Workbook to create a world-writable object of Workbook.
            wwb = Workbook.createWorkbook(new File(fileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(wwb!=null){
            //create a sheet free written 
            //The method of createSheet in Workbook has two parameters, the first is name of sheet, the second is location of sheet in Workbook.
            WritableSheet ws = wwb.createSheet("sheet1", 0);

            //Now, we start to add cell to the sheet.
            int i = 0;
            for (Map.Entry<String, Integer> entry : store.entrySet()) {  
            	
            	Number numberC = new Number(1, i, entry.getValue());
            	Label labelC = new Label(0, i, entry.getKey());  
                try {
                    ws.addCell(numberC);
                    ws.addCell(labelC);
                } catch (RowsExceededException e) {
                    e.printStackTrace();
                } catch (WriteException e) {
                    e.printStackTrace();
                }
                i++;
            }              
            try {
                //write to file from memory
                wwb.write();
                //close resource
                wwb.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (WriteException e) {
                e.printStackTrace();
            }
        }
    }
    
}


