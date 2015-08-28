// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2007
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.HMM;

import Jet.Tipster.*;
import AceJet.Ace;
import Jet.JetTest;
import Jet.Control;
import java.util.*;
import java.io.*;

/**
 *  reads a series of files, with each file containing one sentence per
 *  line, and writes out the sentences with named entity tags.
 *  Developed to handle MT training data.
 */

public class LineByLineNameTagger {

	static final String home =
	  "C:/Documents and Settings/Ralph Grishman/My Documents/";
	static final String mtDir =
	  home + "GALE/mtTrain/";
	static String propertyFile =
	  "props/mt train tag names.properties";
	static String dataDir = mtDir;
	static String outputDir = mtDir;
	static String fileList = mtDir + "fileList.txt";
	static HMMNameTagger tagger;
	
	/**
	 *  generate sentence list files for a list of documents.
	 *  Takes 4 command line parameters:                                       <BR>
	 *  filelist:  a list of the files to be processed                         <BR>
	 *  dataDir:  the path of the directory containing the document            <BR>
	 *  outputDir:  the path of the directory to contain the output            <BR>
	 *  For each <I>file</I> in <I>filelist</I>, the document is read from
	 *  <I>dataDir/file</I>;  if the input file has no sentence tags, the
	 *  sentence splitter is invoked to add sentence tags;  and then
	 *  the sentence file is written to <I>outputDir/file</I>.sent .  
	 */

	public static void main (String[] args) throws IOException {
		if (args.length > 0) {
			if (args.length != 4) {
				System.out.println ("TaggedSentenceWriter must have 4 arguments:");
				System.out.println ("  propertyFile  filelist  dataDirectory  outputDirectory");
				System.exit(1);
			}
			propertyFile = args[0];
			fileList  = args[1];
			dataDir   = args[2];
			outputDir = args[3];
		}
		JetTest.initializeFromConfig (propertyFile);
		// tagger = JetTest.nameTagger;
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
			String sentenceFileName = dataDir + currentDoc;
			String neFileName = outputDir + currentDoc + ".ne";
			processFile (sentenceFileName, neFileName);
		}
	}
	
	private static void processFile (String sentenceFileName, String neFileName)
	    throws IOException {
		BufferedReader reader = new BufferedReader (new FileReader(sentenceFileName));
		PrintWriter writer = new PrintWriter (new FileWriter (neFileName));
		int sentenceCount = 0;
		String currentSentence;
		while ((currentSentence = reader.readLine()) != null) {
			sentenceCount++;
			String taggedSentence = tagSentence(currentSentence);
			writer.println(taggedSentence);
		}
		System.out.println ("Tagged " + sentenceCount + " sentences.");
		writer.close();
	}			

	private static String tagSentence (String sentence) throws IOException {
		// create document with this sentence
		Document doc = new Document(sentence);
		// check case
		Span sentenceSpan = new Span(0, doc.length());
	  Ace.monocase = Ace.allLowerCase(doc, sentenceSpan) || Ace.titleCase(doc, sentenceSpan);
	  // tag names
		Control.processDocument (doc, null, false, 0);
		doc.shrink("ENAMEX");
		// writeSGML
		doc.setSGMLwrapMargin(0);
		return doc.writeSGML("ENAMEX", sentenceSpan).toString();
	}
}
