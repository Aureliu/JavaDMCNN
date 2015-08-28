// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Zoner;

import Jet.Tipster.*;
import AceJet.Ace;
import Jet.JetTest;
import Jet.Control;
import java.util.*;
import java.io.*;

/**
 *  write a Document out, one sentence per line, with tags as gnerated by
 *  properties file.  Each sentence is preceded by the character offset of the 
 *  sentence in the document.
 */

public class TaggedSentenceWriter {

	static String dataDir;
	static String outputDir;
	static String[] types;
	
	/**
	 *  generate sentence list files for a list of documents.
	 *  Takes 4 command line parameters:                                       <BR>
	 *  props:     Jet property list file                                      <BR>
	 *  filelist:  a list of the files to be processed                         <BR>
	 *  dataDir:   the path of the directory containing the documents          <BR>
	 *  outputDir: the path of the directory to contain the output             <BR>
	 *  For each <I>file</I> in <I>filelist</I>, the document is read from
	 *  <I>dataDir/file</I>; and then processed as specified by the Jet
	 *  properties file.  If the input file has no sentence tags, and none
	 *  are added by the script in the properties file, the
	 *  sentence splitter is invoked to add sentence tags to text within
	 *  TEXT XML elements.  Finally the sentence file is written to 
	 *  <I>outputDir/file</I>.nesent with XML tags.  
	 */

	public static void main (String[] args) throws IOException {
		if (args.length != 4) {
			System.out.println ("TaggedSentenceWriter must have 4 arguments:");
			System.out.println ("  propertyFile  filelist  dataDirectory  outputDirectory");
			System.exit(1);
		}
		String propertyFile = args[0];
		String fileList  = args[1];
		dataDir   = args[2];
		outputDir = args[3];
		JetTest.initializeFromConfig (propertyFile);
		String typeProperty = JetTest.getConfig("WriteSGML.type");
		if (typeProperty == null)
			types = new String[] {"ENAMEX"};
		else if (typeProperty.equals("all"))
			types = null;
		else
			types = typeProperty.split("\\s*,\\s*");
		processFileList (fileList);
	}

	private static void processFileList (String fileList) throws IOException {
		// open list of files
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		String currentDoc;
		while ((currentDoc = reader.readLine()) != null) {
			// process file 'currentDoc'
			docCount++;
			System.out.println ("\nProcessing document " + docCount + ": " + currentDoc);
			String textFileName = dataDir + currentDoc;
			ExternalDocument doc = new ExternalDocument("sgml", textFileName);
			doc.setAllTags(true);
			doc.open();
			// check document case
			Ace.monocase = Ace.allLowerCase(doc);
			Control.processDocument (doc, null, false, docCount);
			for (String type : types)
				doc.shrink(type);
			String sentFileName = outputDir + currentDoc + ".nesent";
			PrintWriter writer = new PrintWriter (new FileWriter (sentFileName));
			writeSents (doc, currentDoc, writer);
			writer.close();
		}
	}

	private static void writeSents (ExternalDocument doc, String currentDocPath, PrintWriter writer) {
		Vector<Annotation> priorSentences = doc.annotationsOfType ("sentence");
		if (priorSentences == null || priorSentences.size() == 0) {
			doc.annotateWithTag ("TEXT");
			SpecialZoner.findSpecialZones (doc);
			Vector<Annotation> textSegments = doc.annotationsOfType ("TEXT");
			if (textSegments == null) {
				System.out.println ("No <TEXT> in " + currentDocPath + ", skipped.");
				return;
			}
			for (Annotation ann : textSegments) {
				Span textSpan = ann.span ();
				// check document case
				Ace.monocase = Ace.allLowerCase(doc);
				// split into sentences
				SentenceSplitter.split (doc, textSpan);
			}
		}
		Vector<Annotation> sentences = doc.annotationsOfType ("sentence");
		if (sentences == null) return;
		for (Annotation sentence : sentences) {
			Span sentenceSpan = sentence.span();
			String sentenceText = doc.writeSGML(types, sentenceSpan.start(),
			                                           sentenceSpan.end())
			                         .toString().trim().replace('\n',' ');
			writer.println (sentenceSpan.start() + " " + sentenceText);
		}
	}
}
