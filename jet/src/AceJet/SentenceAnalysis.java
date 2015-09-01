package AceJet;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;
import Jet.Zoner.SentenceSplitter;
import Jet.Zoner.SpecialZoner;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import jxl.write.biff.RowsExceededException;

public class SentenceAnalysis {
	
	static String textDirectory;
	static String textExtension;
	static String keyApfDirectory;
	static String keyApfExtension;
	static Map<Integer, Integer> store_corpus = new HashMap<Integer, Integer> ();
	static Map<Integer, Integer> store_doc = new HashMap<Integer, Integer> ();
	
	public static void main(String[] args) throws IOException {
		
		String docListFile = args[0];
		textDirectory = args[1];
		textExtension = args[2];
		keyApfDirectory = args[3];
		keyApfExtension = args[4];
		
		BufferedReader docListReader = new BufferedReader(new FileReader (docListFile));	//	input the file
		String docName;		// equal to currentDocPath in ACE
		while ((docName = docListReader.readLine()) != null)
		{
			//docName = getFileNameNoEx(docName);	//remove the extension of the file
			processDocument (docName);
			//
			for (Map.Entry<Integer, Integer> entry : store_doc.entrySet()) { 
				Integer freq = store_corpus.get(entry.getKey());
				store_corpus.put(entry.getKey(), freq == null ? entry.getValue() : freq + entry.getValue());
			}
		}	
		System.out.println("Calculate by doc: \n" + store_corpus);	
		writeExcel("C:/Users/v-mingdi/Desktop/Record_TriggerInSentence.xls", store_corpus);
	}
	
	/**
	 *  This function calculate the number of triggers in each sentence.
	 *  v-mingdi September 1th, 2015
	 */
	public static void processDocument (String docName) {
				
		int NumTrigger = 0;	// Record the number of triggers in this document.
		// open the doc to be processed
		String textFileName = textDirectory + "/" + docName + "." + textExtension;
		ExternalDocument doc = new ExternalDocument("sgml", textFileName);	//creates a new external document associated with file 'fileName'.  The format of the file is given by 'format'.
		doc.setAllTags(true);
		doc.open();		
		
		// split the sentence in this doc
		Vector<Annotation> textSegments = doc.annotationsOfType ("TEXT");
		Span span;
		if (textSegments != null && textSegments.size() > 0)
			span = textSegments.get(0).span();	//@di ? the span of doc = text to be processed
		else
			span = doc.fullSpan();
		if (doc.annotationsOfType("dateline") == null && 
			    doc.annotationsOfType("textBreak") == null)
				SpecialZoner.findSpecialZones (doc);
		//code from control.applyScript()
		SentenceSplitter.split(doc, span);
		Vector zones = doc.annotationsOfType("sentence", span);		
		
		// record the trigger in this doc
		Map<Integer, Span> store_trigger= new HashMap<Integer, Span> ();
		String keyApfFileName = keyApfDirectory + "/" + docName + "." + keyApfExtension;
		AceDocument aceDoc = new AceDocument(textFileName, keyApfFileName);	
		// ^ very important.creat structure data of text based on text and annotation,
		// | traverse event in aceDoc. just event, may include multiple event mention
		for (AceEvent event : aceDoc.events) {	
			// | traverse all event mention in event
			for (AceEventMention mention : event.mentions) {	
				store_trigger.put( NumTrigger , mention.anchorExtent);
				NumTrigger ++;
			}
		}
				
		//statistic of trigger in each sentence
		int[] RecTriSen = new int [zones.size()];	
		// ^ Record the Number of Triggers in each sentence. zones.size() is the number of sentence 
		for (int i = 0; i < NumTrigger; i++){
			for (int j = 0; j < zones.size(); j++) {
				if (store_trigger.get(i).within( ( (Annotation) zones.get(j)).span() ) ){
					RecTriSen[j]++;
					break;
				}	
			}
		}
			
		//merger the statistic in each sentence
		store_doc.clear();
		for (int j = 0; j < zones.size(); j++){
			Integer freq = store_doc.get(RecTriSen[j]);
			store_doc.put(RecTriSen[j], freq == null ? 1 : freq + 1 );
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
    
    public static void writeExcel(String fileName, Map<Integer,Integer> store){
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
            for (Map.Entry<Integer, Integer> entry : store.entrySet()) {  
            	
            	Number numberC = new Number(1, i, entry.getValue());
            	Number labelC = new Number(0, i, entry.getKey());  
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
