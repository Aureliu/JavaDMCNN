// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.50
//Copyright:    Copyright (c) 2011
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet;

import java.io.*;

import Jet.Tipster.*;
import Jet.Refres.Resolve;
import Jet.Pat.Pat;
import AceJet.Ace;

/**
 *  provides externally-callable main method to apply Jet processing to a
 *  set of documents.
 */

public class ProcessDocuments {

    /**
     *  process a set of documents through Jet in accordance with a
     *  Jet parameter file.  Invoked by <br>
     *  ProcessDocuments  propsFile docList inputDir inputSuffix outputDir outputSuffix 
     *
     *  @param  propsFile     Jet properties file
     *  @param  docList       file containing list of documents to be processed, 1 per line
     *  @param  inputDir      directory containing files to be processed
     *  @param  inputSuffix   file extension to be added to document name to obtain name of input file
     *  @param  outputDir     directory containing output files
     *  @param  outputSuffix  file extension to be added to document name to obtain name of output file
     */

    public static void main (String[] args) throws IOException {

	if (args.length != 6) {
	    System.err.println ("ProcessDocuments requires 6 arguments:");
	    System.err.println ("  propsFile docList inputDir inputSuffix outputDir outputSuffix");
	    System.exit (1);
	}
	String propsFile = args[0];
	String docList = args[1];
	String inputDir = args[2];
	String inputSuffix = args[3];
	String outputDir = args[4];
	String outputSuffix = args[5];

	// initialize Jet

	System.out.println("Starting ACE Jet...");
	JetTest.initializeFromConfig(propsFile);
	// load ACE type dictionary
	AceJet.EDTtype.readTypeDict();
	// turn off traces
	Pat.trace = false;
	Resolve.trace = false;
	// ACE mode (provides additional antecedents ...)
	Resolve.ACE = true;

	String docName;
	int docCount = 0;
	BufferedReader docListReader = new BufferedReader(new FileReader (docList));
	while ((docName = docListReader.readLine()) != null) {
	    docCount++;
	    String inputFile = docName + "." + inputSuffix;
	    ExternalDocument doc = new ExternalDocument ("sgml", inputDir, inputFile);
	    doc.setAllTags(true);
	    doc.open();
	    String[] types = doc.getAnnotationTypes();
	    doc.setSGMLwrapMargin(0);
	    String outputFile = docName + "." + outputSuffix;
	    BufferedWriter writer = new BufferedWriter (new FileWriter (new File (outputDir, outputFile)));
	    // process document
	    AceJet.Ace.monocase = AceJet.Ace.allLowerCase(doc);
	    Control.processDocument (doc, writer, docCount == -1, docCount);
	    writer.close();
	}
    }
}
