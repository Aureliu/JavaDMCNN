// -*- tab-width: 4 -*-
package Jet.Refres;

import Jet.Lisp.*;
import Jet.Tipster.*;
import java.util.*;

// for testing
import java.io.*;
import Jet.*;
import AceJet.*;

/**
 *  implements a simple pronoun accuracy metric.  For each third-person
 *  pronoun (including possessive and reflexives) in the
 *  response document which appears as a mention in the key, determine
 *  whether the antecedent (the most recent prior mention in the document)
 *  is correct (the same as the antecedent in the key).
 *  <p>
 *  Mentions in the response and key files are aligned based on the
 *  final character of the head of the mentions.
 */

public class PronounScorer extends DocumentScorer {

	private static HashSet<String> pronounSet = new HashSet<String>();
	        // personal pronouns
	static {pronounSet.add("he");
	        pronounSet.add("she");
	        pronounSet.add("it");
	        pronounSet.add("him");
	        pronounSet.add("her");
	        pronounSet.add("they");
	        pronounSet.add("them");
	        // possessive pronouns
	        pronounSet.add("his");
	        pronounSet.add("its");
	        pronounSet.add("their");
	        // reflexive pronouns
	        pronounSet.add("himself");
	        pronounSet.add("herself");
	        pronounSet.add("itself");
	        pronounSet.add("themselves");
	        }

	private Document responseDoc, keyDoc;
	private Vector responseMentions;
	/**
	 *  map from a mention head in response document to corresponding
	 *  mention head in key document.
	 */
	private HashMap<Annotation, Annotation> responseToKeyMentionMap;
	/**
	 *  map from a mention head to its antecedent in response document
	 *  (null if no antecedent).
	 */
	private HashMap<Annotation, Annotation> responseAntecedent;
	/**
	 *  map from a mention head to its antecedent in key document
	 *  (null if no antecedent).
	 */
	private HashMap<Annotation, Annotation> keyAntecedent;
	/**
	 *  pronoun coreference accuracy for the most recently processed document.
	 */
	public float accuracy;
	/**
	 *  pronoun coreference accuracy for all documents processed by this instance
	 *  of CorefScorer.
	 */
	public float overallAccuracy;
	private int correct = 0;
	private int pronouns = 0;
	private int totalCorrect = 0;
	private int totalPronouns = 0;

	/**
	 *  create a new pronoun coreference scorer.
	 */

	public PronounScorer () {
	}

	/**
	 *  compare the two documents, <CODE>responseDoc</CODE> and
	 *  <CODE>keyDoc</CODE>, setting <CODE>accuracy</CODE> and
	 *  <CODE>overallAccuracy</CODE>.
	 */

	public void score (Document responseDoc, Document keyDoc) {
		this.responseDoc = responseDoc;
		this.keyDoc = keyDoc;
		// check both documents for presence of entities
		Vector responseEntities = responseDoc.annotationsOfType("entity");
		if (responseEntities == null) {
			System.err.println ("CorefScorer.score:  no entity annotations in response");
			return;
		}
		Vector keyEntities = keyDoc.annotationsOfType("entity");
		if (keyEntities == null) {
			System.err.println ("CorefScorer.score:  no entity annotations in key");
			return;
		}
		// build response mention --> key mention map
		alignMentions();
		// build anaphor --> antecedent map for both response and key
		responseAntecedent = buildAntecedentMap(responseDoc);
		keyAntecedent = buildAntecedentMap(keyDoc);
		// compute accuracy:
		accuracy = scoreMentions (responseMentions);
		totalCorrect += correct;
		totalPronouns += pronouns;
		overallAccuracy = (float) totalCorrect / totalPronouns;
	}

	/**
	 *  aligns the mentions in the key and response documents, generating
	 *  keyToResponseMentionMap.  Mentions are aligned if the end of their
	 *  spans are the same.
	 */

	private void alignMentions () {
		HashMap endOfKeyMentionMap = CorefScorer.buildEndOfMentionMap (keyDoc);
		responseToKeyMentionMap = new HashMap();

		responseMentions = CorefScorer.findMentions(responseDoc);
		if (responseMentions != null) {
			for (int j = 0; j < responseMentions.size();  j++) {
				Annotation responseMention = (Annotation) responseMentions.elementAt(j);
				Annotation responseMentionHead = Resolve.getHeadC(responseMention);
				int end = responseMentionHead.span().end();
				Annotation keyMention =
				    (Annotation) endOfKeyMentionMap.get(new Integer(end));
				if (keyMention != null) {
					responseToKeyMentionMap.put(responseMentionHead, keyMention);
				}
			}
		}
	}

	/**
	 *  returns a map for Document <CODE>doc</CODE> from the head of each
	 *  mention to the head of its antecedent (or to <CODE>null</CODE> if
	 *  the mention has no antecedent.
	 */

	private HashMap<Annotation, Annotation> buildAntecedentMap (Document doc) {
		HashMap antecedentMap = new HashMap<Annotation, Annotation>();
		Vector entities = doc.annotationsOfType("entity");
		for (int i=0; i<entities.size(); i++) {
			Annotation entity = (Annotation) entities.get(i);
			Vector mentions = (Vector) entity.get("mentions");
			Annotation antecedent = null;
			for (int j=0; j<mentions.size(); j++) {
				Annotation mention = (Annotation) mentions.get(j);
				Annotation mentionHead = Resolve.getHeadC(mention);
				antecedentMap.put(mentionHead, antecedent);
				antecedent = mentionHead;
			}
		}
		return antecedentMap;
	}

	private float scoreMentions (Vector mentions) {
		correct = 0;
		pronouns = 0;
		for (int i=0; i<mentions.size(); i++) {
			Annotation mention = (Annotation) mentions.get(i);
			Annotation mentionHead = Resolve.getHeadC(mention);
			scoreMention (mentionHead);
		}
		return ((float) correct) / pronouns;
	}

	/**
	 *  increments the scoring values (pronouns and correct) for the
	 *  mention whose head is <CODE>mentionHead</CODE>:  pronouns if
	 *  it is a pronoun which is marked as a mention in the key, and
	 *  correct if its antecedent is the same as the antecedent in the key.
	 */

	private void scoreMention (Annotation mentionHead) {
		// is mention a pronoun?
		String headText = responseDoc.text(mentionHead).trim().toLowerCase();
		if (!pronounSet.contains(headText)) return;
		// is it mapped to a key mention
		Annotation keyMentionHead = responseToKeyMentionMap.get(mentionHead);
		if (keyMentionHead == null) return;
		// yes, increment total
		pronouns++;
		// get coref of both mentions
		Annotation responseAnte = responseAntecedent.get(mentionHead);
		Annotation keyAnte = keyAntecedent.get(keyMentionHead);
		// if the same, incr. correct
		if ((responseAnte == null && keyAnte == null) ||
		    responseToKeyMentionMap.get(responseAnte) == keyAnte) {
			correct++;
		}
		while ((keyAnte = keyAntecedent.get(keyAnte)) != null) {
			if (responseToKeyMentionMap.get(responseAnte) == keyAnte) {
				correct++;
				return;
			}
		}
	}

	/**
	 *  writes to standard output a report on the most recently
	 *  scored document.
	 */

	public void report () {
		System.out.println ("Pronoun accuracy = " + accuracy);
		System.out.println ("( " + correct + " correct pronoun antecedents " +
		                    "out of " + pronouns + " pronouns)");
	}

	/**
	 *  writes to standard output a report on the overall accuracy
	 *  for all documents processed so far.
	 */

	public void summary () {
		System.out.println ("Overall pronoun accuracy = " + overallAccuracy);
		System.out.println ("( " + totalCorrect + " correct pronoun antecedents " +
		                    "out of " + totalPronouns + " pronouns)");
	}

	public static void main (String[] args) throws IOException {
		String home = System.getProperty("user.home") + "/";
		// read coref key
		String keyFile = home + "My Documents/jet/data/coref.txt";
		String[] tags = {"coref"};
		ExternalDocument keyDoc = new ExternalDocument("sgml", keyFile);
		keyDoc.setSGMLtags(tags);
		keyDoc.open();
		// convert it
		CorefFilter.buildEntitiesFromLinkedMentions(keyDoc);
		// display it
		new View(keyDoc, 0);
		new EntityView(keyDoc, 0);
		JetTest.initializeFromConfig ("props/chunk3.properties");
		EDTtype.readTypeDict();
		// read document
		String testFile = home + "My Documents\\Jet\\Data\\article.txt";
		Document testDoc = JetTest.readDocument (new BufferedReader (new FileReader (testFile)));
		// process document
		Control.processDocument (testDoc, null, true, 1);
		// score document
		PronounScorer scorer = new PronounScorer();
		scorer.score(testDoc, keyDoc);
		scorer.report();
		scorer.summary();
	}

}
