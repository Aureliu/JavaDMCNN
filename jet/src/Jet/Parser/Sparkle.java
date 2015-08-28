// -*- tab-width: 4 -*-
package Jet.Parser;

//Author:       Ralph Grishman
//Date:         July 13, 2003

import java.util.*;
import java.io.*;

import Jet.*;
import Jet.Control;
import Jet.Parser.SynFun;
import Jet.Refres.Resolve;
import Jet.Lex.Tokenizer;
import Jet.Lisp.*;
import Jet.Tipster.*;
import Jet.Pat.Pat;
// for gazetteer
import AceJet.Ace;
import AceJet.Gazetteer;

/**
 *  procedures for generating a set of syntactic dependency relations from a
 *  document and comparing the generated set with a reference set of triples.
 *  Evaluated for recall and precision of dependencies, like the Sparkle project.
 *  <p>
 *  The key for evaluating the dependencies takes the form of a file of
 *  quadruples, of the form <br>
 *      sentence-number | head | relation | argument
 *  <br> the block of quadruples for a document must be preceded by a line of
 *  the form <br>
 *      docid <i>id</i>
 *  <br> where <i>id</id> is the identifier of the document.  Anything on
 *  a line after a '#' is taken as a comment.  Blank lines, and lines starting
 *  with a '#', are ignored.
 */

public class Sparkle {

	static ExternalDocument doc;
	static String currentDoc;
	static final String sourceType = "text";
	static final String ACEdir =
	    "C:/Documents and Settings/Ralph Grishman/My Documents/ACE/";
	static final String fileList = ACEdir + "Sparkle docs.txt";
	static final String keyFile = ACEdir + "parse key.log";
	/**
	 *  the set of relations to be tested.
	 */
	public static String[] relations = {"of", "subject", "object"};

	static int correct = 0;
	static int spurious = 0;
	static int missing = 0;

	public static void main (String[] args) throws IOException {
		// initialize Jet
		System.out.println("Starting ACE Jet...");
		JetTest.initializeFromConfig("props/ace parser.properties");
		Ace.gazetteer = new Gazetteer();
		// turn off traces
		Pat.trace = false;
		Resolve.trace = false;
		// load dependency key
		HashMap key = readKey (keyFile);
		// new Jet.Console();
		// open list of files
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		while ((currentDoc = reader.readLine()) != null) {
			// if(true) continue;
			// process file 'currentDoc'
			docCount++;
			System.out.println ("\nProcessing document " + docCount + ": " + currentDoc);
			// read document
			doc = new ExternalDocument("sgml", ACEdir + "training/nwire/" + currentDoc + ".sgm");
			doc.open();
			// process document
			Control.processDocument (doc, null, docCount < 3, 1);
			// extract and score dependency relations
			ArrayList triples = extractDependencies (doc);
			ArrayList keyTriples = (ArrayList) key.get(currentDoc);
			score (triples, keyTriples);
			if (docCount >= 3) break;
		}
		System.out.print (correct + " triples correct, " + spurious + " spurious, ");
		System.out.println (missing + " missing.");
	}

	private static ArrayList extractDependencies (Document doc) {
		ArrayList triples = new ArrayList();
		// loop over sentences (assume they are in order)
		Vector sents = doc.annotationsOfType("sentence");
		for (int isent=0; isent<sents.size(); isent++) {
			Annotation sent = (Annotation) sents.get(isent);
			int start = sent.span().start();
			int end = sent.span().end();
			for(int i = start; i < end; i++) {
				Vector constits = doc.annotationsAt(i,"constit");
				if (constits != null) {
					for (int j = 0; j < constits.size();  j++) {
						Annotation ann = (Annotation) constits.elementAt(j);
						for (int r = 0; r < relations.length; r++) {
							String relation = relations[r];
							if (ann.get(relation) != null) {
								Annotation value = (Annotation) ann.get(relation);
								triples.add(buildQuad (isent+1, ann, relation, value));
							}
						}
					}
				}
			}
		}
		return triples;
	}

	private static String buildQuad
		(int sentNumber, Annotation parent, String relation, Annotation dependent) {
		String parentHead = SynFun.getNameOrHead (doc, parent);
		String dependentHead = SynFun.getNameOrHead (doc, dependent);
		return sentNumber + "|" + parentHead + "|" + relation + "|" + dependentHead;
	}

	private static HashMap readKey (String keyFile) throws IOException {
		LineNumberReader reader = new LineNumberReader (new FileReader (keyFile));
		HashMap key = new HashMap();
		String line;
		String docId = "";
		while ((line = reader.readLine()) != null) {
			// remove comment
			int poundsign = line.indexOf('#');
			if (poundsign >= 0)
				line = line.substring(0,poundsign).trim();
			// ignore otherwise blank lines
			if (line.trim().length() == 0)
				continue;
			if (line.length() >= 5 && line.substring(0,5).equals("docid")) {
				docId = line.substring(5).trim();
			} else {
				if (docId == "") {
					System.out.println ("No docid specified, line ignored");
					continue;
				}
				ArrayList vec = (ArrayList) key.get(docId);
				if (vec == null) vec = new ArrayList();
				vec.add(line);
				key.put(docId, vec);
			}
		}
		return key;
	}

	private static void score (ArrayList response, ArrayList masterKey) {
		if (masterKey == null) {
			System.out.println ("No key.");
			return;
		}
		ArrayList key = new ArrayList(masterKey);
		for (int i=0; i<response.size(); i++) {
			String quad = (String) response.get(i);
			int j = key.indexOf(quad);
			if (j >= 0) {
				key.remove(j);
				System.out.println ("Correct:  " + quad);
				correct++;
			} else {
				System.out.println ("Spurious: " + quad);
				spurious++;
			}
		}
		for (int i=0; i<key.size(); i++) {
			String quad = (String) key.get(i);
			System.out.println ("Missing:  " + quad);
			missing++;
		}
	}

}
