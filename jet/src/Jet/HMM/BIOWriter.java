// -*- tab-width: 4 -*-
package Jet.HMM;

import java.util.*;
import java.io.*;
import Jet.JetTest;
import Jet.Tipster.*;
import Jet.Zoner.SentenceSplitter;
import Jet.Lex.Tokenizer;

/**
 *  converts an XML-annotated named entity file (with the tag ENAMEX) into
 *  a BIO-format file.  In BIO format, each token is on a separate line.  A
 *  token which is not part of a name is tagged "O";  a token which is the
 *  first token of a name of type X is tagged "B-X";  a token which is a
 *  subsequent token of a name of type X is tagged "I-X".  Sentences are
 *  separated by a blank line.
 */

public class BIOWriter {

	private static final String home = "C:/Documents and Settings/Ralph Grishman/My Documents/";

	// read collection from "xmlCollection"
	// write file to "bioFile"

	// MUC-7 official training
	// private static final String xmlCollection = home + "HMM/NE/training/collection.txt";
	// private static final String bioFile = home + "HMM/NE/officialtrainBIO.txt";
	// MUC-7 augmented training
	// private static final String xmlCollection = home + "HMM/NE/NE train Collection.txt";
	// private static final String bioFile = home + "HMM/NE/trainBIO.txt";
	// MUC-7 dry run test
	// private static final String xmlCollection = home + "HMM/NE/dryrun test/key-collection.txt";
	// private static final String bioFile = home + "HMM/NE/dryrunBIO.txt";
	private static final String xmlCollection =  home + "HMM/NE/ACE training Collection.txt";
	private static final String bioFile = home + "HMM/NE/aceTrainingBIO.txt";
	private static final String[] tagsToRead = {"ENAMEX", "TIMEX", "NUMEX"};
	private static PrintStream writer;

	public static void main (String[] args) throws IOException {
		convertCollection (xmlCollection, bioFile);
	}

	/**
	 *  command-line callable file conversion (invoked by -Dtask=CorefEval).
	 *  Passed an array of two file names:  the collection of XML files and
	 *  the file to contain the BIO-format data.
	 **/

	public static void task (String[] args) {
		if (args.length != 3) {
			System.out.println ("BIOWriter requires 2 arguments: jet -BIOWriter <XML-collection> <BIO-file>");
			System.exit(1);
		}
		String xmlCollection = args[1];
		String bioFile = args[2];
		try {
		convertCollection (xmlCollection, bioFile);
		} catch (IOException e) {
			System.out.println ("BIOWriter IO error: " + e);
		}
	}

	/**
	 *  converts the collection of XML-coded files 'xmlCollectionName' and writes
	 *  the BIO format data as a single file on 'bioFileName'.
	 */

	public static void convertCollection (String xmlCollectionName, String bioFileName)
	      throws IOException {
		DocumentCollection xmlCollection = new DocumentCollection(xmlCollectionName);
		writer = new PrintStream (new FileOutputStream (bioFileName));
		xmlCollection.open();
		for (int i=0; i<xmlCollection.size(); i++) {
			// open test document
			ExternalDocument doc = xmlCollection.get(i);
			System.out.println ("Processing document " + doc.fileName());
			doc.setSGMLtags (tagsToRead);
			doc.open();
			doc.annotateWithTag ("text");
			Span textSpan = ((Annotation) doc.annotationsOfType ("text").get(0)).span();
			eraseAnnotationsOutside (doc, "ENAMEX", textSpan);
			eraseAnnotationsOutside (doc, "TIMEX", textSpan);
			eraseAnnotationsOutside (doc, "NUMEX", textSpan);
			// find sentences
			SentenceSplitter.split (doc, textSpan);
			Vector sentences = doc.annotationsOfType ("sentence");
			if (sentences == null) continue;
			Iterator is = sentences.iterator ();
			while (is.hasNext ()) {
				Annotation sentence = (Annotation)is.next ();
				Span sentenceSpan = sentence.span();
				Tokenizer.tokenize (doc, sentenceSpan);
				writeTags (doc, sentenceSpan);
			}
		}
	}

	private static void writeTags (Document doc, Span sentenceSpan) {
		// loop over tokens, writing tags
		int posn = sentenceSpan.start();
		int end = sentenceSpan.end();  // end of zone
		// skip whitespace at beginning of textSpan
		posn = Tokenizer.skipWSX(doc, posn, end);
		String tokenTag;
		String continuationTag = "O";
		int markupEnd = 0;
		while (posn < end) {
			Annotation token = doc.tokenAt(posn);
			String tokenText = doc.text(token).trim();
			Vector enamexes = doc.annotationsAt(posn, "ENAMEX");
			if (enamexes != null && enamexes.size() > 0) {
				Annotation enamex = (Annotation) enamexes.get(0);
				String tag = (String) enamex.get("TYPE");
				// check no active markup (markupEnd == 0)
				if (markupEnd == 0) {
					// set token tags, for current and following tokens
					tokenTag = ("B-" + tag).intern();
					continuationTag = ("I-" + tag).intern();
					// record markupEnd
					markupEnd = enamex.span().end();
				} else {
					System.out.println ("Nested tag " + tag + " ignored.");
					System.out.println ("(tag from annotation " + enamex + ")");
					tokenTag = continuationTag;
				}
			} else {
				tokenTag = continuationTag;
			}
			writer.println (tokenText + " " + tokenTag);
			posn = token.span().end();
			// if this token matches end point,
			//     clear end point and set continuation tag = other
			if (markupEnd != 0 && posn > markupEnd) {
				System.out.println ("Annotation does not end at token boundary");
				System.out.println ("(annotation ends at " + markupEnd +
				                    ", token boundary is " + posn);
			}
			if (posn >= markupEnd) {
				markupEnd = 0;
				continuationTag = "O";
			}
		}
		if (markupEnd != 0) {
			System.out.println ("Annotation extends past text [sentence] boundary");
			System.out.println ("(annotation ends at " + markupEnd + ")");
		}
		// write blank line at end of sentence
		writer.println();
	}

	private static void eraseAnnotationsOutside (Document doc, String type, Span span) {
		Vector v = doc.annotationsOfType(type);
		if (v == null) return;
		v = (Vector) v.clone();
		for (int i=0; i<v.size(); i++) {
			Annotation a = (Annotation) v.get(i);
			if (!a.span().within(span)) {
				doc.removeAnnotation(a);
			}
		}
	}
}
