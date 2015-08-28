// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkit

package Jet.Refres;

import java.util.*;
import java.io.*;

import Jet.JetTest;
import Jet.Control;
import Jet.Pat.Pat;
import Jet.Lisp.*;
import Jet.Tipster.*;
import Jet.Chunk.Chunker;

import AceJet.Gazetteer;
import AceJet.Ace;

/**
 *  provides a stand-alone main method for annotating a document
 *  collection with coreference information.
 */

public class CorefWriter {

	static final String home =
	    "C:/Documents and Settings/Ralph Grishman/My Documents/";
	static final String ACEdir = home + "ACE/";
	// static final String collection = home + "Sekine/input/Duc2004/files.txt";
	static final String collection = "../coref/texts/trainingTexts.txt";
	// static final String outCollection = home + "Sekine/output/Duc2004/files.txt";
	static final String outCollection = "../coref/sysout/trainingTexts.txt";
	static final String dictFile = ACEdir + "EDT type dict.txt";

	public static void main (String[] args) throws IOException {
		// initialize Jet
		System.out.println("Starting ACE Jet...");
		JetTest.initializeFromConfig("props/ME ace.properties");
		Ace.useParser = false;
		// JetTest.initializeFromConfig("ace parser.properties");
		Chunker.loadModel();
		// load ACE type dictionary
		AceJet.EDTtype.readTypeDict(dictFile);
		// open text collection
		DocumentCollection col = new DocumentCollection(collection);
		col.open();
		for (int docCount = 0; docCount < col.size(); docCount++) {
			// process file 'currentDoc'
			ExternalDocument doc = col.get(docCount);
			System.out.println ("\nProcessing document " + docCount + ": " + doc.fileName());
			// read test document
			// doc.setAllTags(true);
			doc.open();
			// process document
			// doc.annotateWithTag("TEXT");
			AceJet.Ace.monocase = AceJet.Ace.allLowerCase(doc);
			Control.processDocument (doc, null, docCount == 0, docCount);
			// CorefFilter.buildMentionsFromEntities (doc);
			// doc.setSGMLtags(new String[] {"mention"});
			CorefFilter.buildLinkedMentionsFromEntities (doc);
			doc.setSGMLtags(new String[] {"COREF"});
		}
		// write new collection with mention tags
		col.saveAs(outCollection);
	}

}
