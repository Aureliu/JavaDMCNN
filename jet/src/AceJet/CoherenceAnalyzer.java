// -*- tab-width: 4 -*-
package AceJet;

import java.util.*;
import java.io.*;

import Jet.Tipster.*;
import Jet.Zoner.SentenceSplitter;

/**
 *  analyze a set of ACE APF files for coreference relations
 *  between nominals.
 */

public class CoherenceAnalyzer {

	static final String ACEdir =
	    "C:/Documents and Settings/Ralph Grishman/My Documents/ACE/";
	static final String fileList =
		// ACEdir + "training all.txt";
		// ACEdir + "feb02 all.txt";
		// ACEdir + "sep02 all.txt";
		// ACEdir + "aug03 all.txt";
		// ACEdir + "files-to-process.txt";
		ACEdir + "training nwire.txt";

	static final int NSENT = 50;
	static int[] mentionCount = new int[NSENT];
	static int[] mentionWithAnaphorCount = new int[NSENT];
	static int[] mentionWithAntecedentCount = new int[NSENT];

	public static void main (String [] args) throws Exception  {

		// open list of files
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		String currentDoc;
		while ((currentDoc = reader.readLine()) != null) {
			// process file 'currentDoc'
			docCount++;
			// if (docCount != 65) continue;
			System.out.println ("\nProcessing document " + docCount + ": " + currentDoc);
			String textFileName = ACEdir + currentDoc + ".sgm";
			boolean newData = fileList.indexOf("03") > 0;
			String APFfileName = ACEdir + currentDoc + (newData ? ".apf.xml" : ".sgm.tmx.rdc.xml");
			reset();
			analyzeDocument (textFileName, APFfileName);
			report(sentenceBoundaries.length);
		}
		report(NSENT);
	}

	private static void analyzeDocument (String textFileName, String APFfileName) {
	  AceDocument aceDoc = new AceDocument (textFileName, APFfileName);
	  Document doc = aceDoc.JetDocument();
	  collectSentenceBoundaries(doc);
	  collectMentionSpans (aceDoc);
		findEntityMentions (aceDoc);
	}

	/**
	 *  collect the mentions for each entity and call 'analyzeMentions' for them.
	 */

	static void findEntityMentions (AceDocument aceDoc) {
		ArrayList entities = aceDoc.entities;
		for (int i=0; i<entities.size(); i++) {
			AceEntity entity = (AceEntity) entities.get(i);
			// if (entity.generic) continue;
			ArrayList mentions = entity.mentions;
			analyzeMentions(mentions);
		}
	}

	private static void analyzeMentions (ArrayList mentions) {
		for (int imention = 0; imention < mentions.size(); imention++) {
			AceEntityMention mention = (AceEntityMention) mentions.get(imention);
			// if (embedded(mention)) continue;
			Span span = mention.head;
			int sentence = sentenceNumber(span);
			if (sentence >= NSENT) continue;
			boolean hasAntecedent = imention > 0;
			boolean hasAnaphor = imention < mentions.size() - 1;
			mentionCount[sentence]++;
			if (hasAnaphor) mentionWithAnaphorCount[sentence]++;
			if (hasAntecedent) mentionWithAntecedentCount[sentence]++;
		}
	}

	private static void reset () {
		mentionCount = new int[NSENT];
		mentionWithAnaphorCount = new int[NSENT];
		mentionWithAntecedentCount = new int[NSENT];
	}

	private static void report (int limit) {
		//*
		for (int i=0; i<limit; i++) {
			if (i >= NSENT) break;
			System.out.print   ("Sentence " + i + ": ");
			System.out.print   (mentionCount[i] + " mentions, ");
			System.out.print   (mentionWithAnaphorCount[i] + " with anaphors, ");
			System.out.println (mentionWithAntecedentCount[i] + " with antecedents");
		}
		// */
		if (mentionCount[1] > 0 && mentionWithAnaphorCount[1] == 0) {
			System.out.println ("**** Isolated first sentence:");
			System.out.println ("     " + firstSentence);
		}
	}

	private static int[] sentenceBoundaries;
	private static String firstSentence;

	private static void collectSentenceBoundaries (Document doc) {
		Vector textAnnotations = doc.annotationsOfType("TEXT");
		Annotation textAnnotation = (Annotation) textAnnotations.get(0);
		Span textSpan = textAnnotation.span();
		SentenceSplitter.split(doc, textSpan);
		Vector v = doc.annotationsOfType("sentence");
		Span firstSentenceSpan = ((Annotation)v.get(0)).span();
		firstSentence = doc.text(firstSentenceSpan);
		int datelineEnd = firstSentence.indexOf('_') + firstSentenceSpan.start();
		sentenceBoundaries = new int[v.size() + 1];
		sentenceBoundaries[0] = datelineEnd;
		for (int i=0; i<v.size(); i++)
			sentenceBoundaries[i+1] = ((Annotation) v.get(i)).span().end();
	}

	private static int sentenceNumber (Span span) {
		int start = span.start();
		for (int i=0; i<sentenceBoundaries.length; i++)
			if (start < sentenceBoundaries[i])
				return i;
		return -1;
	}

	static ArrayList spans = new ArrayList();

	private static void collectMentionSpans (AceDocument aceDoc) {
		spans = new ArrayList();
		ArrayList entities = aceDoc.entities;
		for (int i=0; i<entities.size(); i++) {
			AceEntity entity = (AceEntity) entities.get(i);
			if (entity.generic) continue;
			ArrayList mentions = entity.mentions;
			for (int j=0; j<mentions.size(); j++) {
				AceEntityMention mention = (AceEntityMention) mentions.get(j);
				spans.add(mention.extent);
			}
		}
	}

	private static boolean embedded (AceEntityMention mention) {
		Span span = mention.extent;
		for (int i=0; i<spans.size(); i++) {
			Span otherSpan = (Span) spans.get(i);
			if (span.within(otherSpan) && !span.equals(otherSpan))
				return true;
		}
		return false;
	}
}
