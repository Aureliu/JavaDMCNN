package AceJet;
import java.util.*;
import java.io.*;
import Jet.Tipster.*;
import Jet.Zoner.SentenceSplitter;
import Jet.Zoner.SpecialZoner;

public class AureDiTrainData {
	
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
		
        File writename = new File("C:\\Users\\v-mingdi\\Desktop\\Test"); // path + filename.
        writename.createNewFile(); // construct a new file
        BufferedWriter out = new BufferedWriter(new FileWriter(writename));  
        
		BufferedReader docListReader = new BufferedReader(new FileReader (docListFile));	//	input the file
		String docName;		// equal to currentDocPath in ACE
		while ((docName = docListReader.readLine()) != null)
		{
			//docName = getFileNameNoEx(docName);	//remove the extension of the file
			processDocument (docName, out);
			//
			for (Map.Entry<Integer, Integer> entry : store_doc.entrySet()) { 
				Integer freq = store_corpus.get(entry.getKey());
				store_corpus.put(entry.getKey(), freq == null ? entry.getValue() : freq + entry.getValue());
			}
			System.out.println(docName);
		}	
        out.flush(); // write the buffer to the file.
        out.close(); // close the file  
	}
	
	/**
	 *  This function calculate the number of triggers in each sentence.
	 *  v-mingdi September 1th, 2015
	 */
	public static void processDocument (String docName, BufferedWriter out) {
				
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
		        try {
		        	StringBuffer Text = new StringBuffer (mention.text);
		        	for(int i = 0; i < mention.text.length(); i++){
		        		if (mention.text.charAt(i) == '\n'){       			
		        			Text.deleteCharAt(i);
		        			Text.insert(i, ' ');
		        		}
		        	}
					out.write(Text.toString() + '_' + event.subtype + "\r\n");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} // \r\n is the newline order
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
    
    public static void writedata(String pathname, boolean bool){   	
	    try { // In order to avoid construct or read failure,  use try catch the error.
	    	if (bool){
		        /* read the txt file */  
		        File filename = new File(pathname); // pathname include the filename   
		        InputStreamReader reader = new InputStreamReader(  
		                new FileInputStream(filename)); // construct a input stream reader  
		        BufferedReader br = new BufferedReader(reader); // transfer the language to the computer read  
		        String line = "";  
		        line = br.readLine();  
		        while (line != null) {  
		            line = br.readLine(); // once a line
		        }  
	    	}
	    	else{
	
		        /* write Txt file */  
		        File writename = new File(pathname); // path + filename.
		        writename.createNewFile(); // construct a new file
		        BufferedWriter out = new BufferedWriter(new FileWriter(writename));  
		        out.write("I can wrtite file.\r\n"); // \r\n is the newline order.
		        out.flush(); // write the buffer to the file.
		        out.close(); // close the file  
	    	}
	
	    } catch (Exception e) {  
	        e.printStackTrace();  
	    }
    }

}
