// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Zoner;

import Jet.Tipster.*;
import AceJet.Ace;
import java.util.*;
import java.io.*;

/**
 *  split a document into sentences and write the information about the
 *  sentences out in one of three formats:
 *  <ul>
 *  <li> one sentence per line, with each sentence preceded by the character 
 *       offset of the sentence in the document
 *  <li> an XML file with the start and end offsets of each sentence
 *  <li> the original document with 'sentence' tags surrounding each
 *       sentence (inline annotation)
 *  </ul>
 */

public class SentenceWriter {

	static String dataDir;
	static String outputDir;
	static String fileList;
	static boolean writeXML = false;
	static boolean inline = false;
	static final boolean debug = false;
	static final String d = " : ";

	/**
	 *  generate sentence list files for a list of documents.
	 *  Takes 3 or 4 command line parameters:                                  <BR>
	 *  filelist:  a list of the files to be processed                         <BR>
	 *  dataDir:  the path of the directory containing the document            <BR>
	 *  outputDir:  the path of the directory to contain the output            <BR>
	 *  writeXML:  if present, sentences are written in an XML format with
	 *            offsets only                                                 <BR>
	 *  For each <I>file</I> in <I>filelist</I>, the document is read from
	 *  <I>dataDir/file</I>;  if the input file has no sentence tags, the
	 *  sentence splitter is invoked to add sentence tags;  and then
	 *  the sentence file is written to <I>outputDir/file</I>.sent .  
	 *  Only sentences within TEXT XML elements are written.
	 */

	public static void main (String[] args) throws IOException {
		if (args.length > 0) {
			if (args.length < 3 || args.length > 4) {
				System.out.println ("SentenceWriter must have 3 or 4 arguments:");
				System.out.println ("  filelist  dataDirectory  outputDirectory [XMLflag]");
				System.exit(1);
			}
			fileList  = args[0];
			dataDir   = args[1];
			outputDir = args[2];
			writeXML  = args.length == 4;
			inline    = writeXML && (args[3].equals("inline"));
		}
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
			if(debug) {
				processFile (currentDoc, docCount);
			} else {
				try {
					processFile (currentDoc, docCount);
				} catch (Exception e){
					System.err.println("Error" + d + fileList + d + docCount + d + currentDoc + e.toString());
					e.printStackTrace();
				}
			}
		}
	}

	private static void processFile(String currentDoc, int docCount) throws IOException {
 		System.out.println ("\nProcessing document " + docCount + " : " + currentDoc);
		String textFileName = dataDir + currentDoc;
		ExternalDocument doc = new ExternalDocument("sgml", textFileName);
		doc.setAllTags(true);
		doc.open();
		split (doc, currentDoc);
		if (inline) {
			writeInline (doc, currentDoc);
		} else {
			String sentFileName = outputDir + currentDoc + ".sent";
			PrintWriter writer = new PrintWriter (new FileWriter (sentFileName));
			writeSents (doc, currentDoc, writer);
			writer.close();
		}
	}
	
	/**
	 *  split the document into sentences (unless sentences are already marked)
	 */
	 
	private static void split (Document doc, String currentDocPath) {
		SpecialZoner.findSpecialZones (doc);
		Vector textSegments = doc.annotationsOfType ("TEXT");
		if (textSegments == null) {
			System.out.println ("No <TEXT> in " + currentDocPath + ", skipped.");
			return;
		}
		Vector priorSentences = doc.annotationsOfType ("sentence");
		if (priorSentences == null || priorSentences.size() == 0) {
			Iterator it = textSegments.iterator ();
			while (it.hasNext ()) {
				Annotation ann = (Annotation)it.next ();
				Span textSpan = ann.span ();
				// check document case
				Ace.monocase = Ace.allLowerCase(doc);
				// split into sentences
				SentenceSplitter.split (doc, textSpan);
			}
		}
	}


	private static void writeSents (ExternalDocument doc, String currentDocPath, PrintWriter writer) {
		if (writeXML) {
			String currentDoc = currentDocPath;
			if (currentDocPath.indexOf('/') >= 0)
					currentDoc = currentDocPath.substring(currentDocPath.lastIndexOf('/')+1);
			writer.print   ("<source_file URI=\"" + currentDoc + "\"");
			writer.println (" SOURCE=\"newswire\" TYPE=\"text\" AUTHOR=\"NYU\">");
			String docId = Ace.getDocId(doc);
			if (docId == null) {
				if (currentDoc.endsWith(".sgm")) {
					docId = currentDoc.substring(0, currentDoc.length() - 4);
				} else {
					docId = currentDoc;
				}
			}
			writer.println ("<document DOCID=\"" + docId + "\">");
		}
		Vector<Annotation> sentences = doc.annotationsOfType ("sentence");
		if (sentences == null) return;
		for (Annotation sentence : sentences) {
			Span sentenceSpan = sentence.span();
			String sentenceText = doc.text(sentenceSpan).trim().replace('\n',' ');
			if (writeXML) {
				//  for XML output form, remove trailing whitespace and give
				//  ACE offset (end - 1)
				doc.shrink(sentence);
				writer.println ("  <sentence>" +
				                "<charseq START=\"" + sentenceSpan.start() + "\"" +
			                  " END=\"" + (sentenceSpan.end()-1) + "\"></charseq>" +
			                  "</sentence>");
				}
			else
				writer.println (sentenceSpan.start() + " " + sentenceText);
		}
		if (writeXML) {
			writer.println ("</document>");
			writer.println ("</source_file>");
		}
	}
	
	static private void writeInline (ExternalDocument doc, String currentDoc) {
		Vector<Annotation> sentences = doc.annotationsOfType ("sentence");
		if (sentences != null) {
			int sentNo = 0;
			for (Annotation sentence : sentences) {
				sentNo++;
				sentence.put("ID", "SENT-" + sentNo);
			}
		}
		doc.removeAnnotationsOfType ("dateline");
		doc.removeAnnotationsOfType ("textBreak");
		doc.shrink("sentence");
		doc.setSGMLwrapMargin(0);
		doc.saveAs(outputDir, currentDoc);
	}
			
}