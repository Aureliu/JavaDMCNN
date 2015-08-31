package AceJet;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import Jet.Zoner.SentenceSplitter;
import Jet.Zoner.SpecialZoner;
import Jet.Tipster.Annotation;
import Jet.Tipster.ExternalDocument;
import Jet.Tipster.Span;

public class SentenceAnalysis {
	
	static String textDirectory;
	static String textExtension;
	static String keyApfDirectory;
	static String keyApfExtension;
	static Map<String, Integer> store = new HashMap<String, Integer> ();
	
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
		}
		System.out.println(store);	
	}
	
	public static void processDocument (String docName) {

		String textFileName = textDirectory + "/" + docName + "." + textExtension;
		ExternalDocument doc = new ExternalDocument("sgml", textFileName);	//creates a new external document associated with file 'fileName'.  The format of the file is given by 'format'.
		doc.setAllTags(true);
		doc.open();
		Vector<Annotation> textSegments = doc.annotationsOfType ("TEXT");
		Span span;
		if (textSegments != null && textSegments.size() > 0)
			span = textSegments.get(0).span();	//@di ? the span of doc = text to be processed
		else
			span = doc.fullSpan();
//		if (doc.annotationsOfType("dateline") == null && 
//			    doc.annotationsOfType("textBreak") == null)
//				SpecialZoner.findSpecialZones (doc);
		//code from control.applyScript()
		SentenceSplitter.split(doc, span);
		Vector zones = doc.annotationsOfType("sentence", span);
		for (int i = 0; i < zones.size(); i++) {
			Annotation zone = (Annotation) zones.get(i);
			
		}
	}

}
