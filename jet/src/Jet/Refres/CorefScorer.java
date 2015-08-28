// -*- tab-width: 4 -*-
package Jet.Refres;

import Jet.Lisp.*;
import Jet.Tipster.*;
import java.util.*;

// for testing
import java.io.*;
import Jet.*;

/**
 *  implements coreference scoring based on the metric developed by Marc
 *  Vilain for MUC-6.  The two documents should have <B>entity</B> annotations,
 *  each representing (the referent of) a set of coreferential mentions.
 *  Each annotation should have a <B>mentions</B> feature whose value is
 *  a Vector of mentions -- the phrases referring to this entity.
 *  The scorer measures the overlap between the mention sets in the
 *  key and response documents.
 *  <p>
 *  Mentions in the response and key files are aligned based on the
 *  final character of the head of the mentions.
 */

public class CorefScorer extends DocumentScorer {

	private Document responseDoc, keyDoc;
	private HashMap responseToKeyMentionMap, keyToResponseMentionMap;
	private Vector responseEntities, keyEntities;
	private HashMap responseMentionToEntityMap, keyMentionToEntityMap;
	/**
	 *  coreference link recall for the most recently processed document.
	 */
	public float recall;
	/**
	 *  coreference link precision for the most recently processed document.
	 */
	public float precision;
	/**
	 *  coreference link recall for all documents processed by this instance
	 *  of CorefScorer.
	 */
	public float overallRecall;
	/**
	 *  coreference link precison for all documents processed by this instance
	 *  of CorefScorer.
	 */
	public float overallPrecision;
	/***
	 *  number of mentions in current response Document which have a corresponding
	 *  mention in key Document (a mention whose head ends at the same position).
	 */
	public int mappedMentionCount = 0;
	/**
	 *  number of corresponding mentions in all documents processed by this
	 *  instance of CorefScorer.
	 */
	public int overallMappedMentionCount = 0;
	private int recallNumerator = 0;
	private int recallDenominator = 0;
	private int precisionNumerator = 0;
	private int precisionDenominator = 0;

	/**
	 *  create a new coreference scorer.
	 */

	public CorefScorer () {
	}

	/**
	 *  compare the two documents, <CODE>responseDoc</CODE> and
	 *  <CODE>keyDoc</CODE>, setting <CODE>recall</CODE> and
	 *  <CODE>precision</CODE>.
	 */

	public void score (Document responseDoc, Document keyDoc) {
		this.responseDoc = responseDoc;
		this.keyDoc = keyDoc;
		mappedMentionCount = 0;
		alignMentions();
		responseEntities = responseDoc.annotationsOfType("entity");
		if (responseEntities == null) {
			System.err.println ("CorefScorer.score:  no entity annotations in response");
			return;
		}
		keyEntities = keyDoc.annotationsOfType("entity");
		if (keyEntities == null) {
			System.err.println ("CorefScorer.score:  no entity annotations in key");
			return;
		}
		responseMentionToEntityMap = buildMentionToEntityMap (responseDoc, responseEntities);
		keyMentionToEntityMap = buildMentionToEntityMap (keyDoc, keyEntities);
		precision = scoreEntities (responseEntities, responseToKeyMentionMap,
		                        keyMentionToEntityMap, true);
		recall = scoreEntities (keyEntities, keyToResponseMentionMap,
		                           responseMentionToEntityMap, false);
		overallPrecision = (float) precisionNumerator / precisionDenominator;
		overallRecall = (float) recallNumerator / recallDenominator;
		overallMappedMentionCount += mappedMentionCount;
	}

	/**
	 *  aligns the mentions in the key and response documents, generating
	 *  responseToKeyMentionMap and keyToResponseMentionMap.  Mentions are
	 *  aligned if the end of their spans are the same.
	 */

	private void alignMentions () {
		HashMap endOfKeyMentionMap = buildEndOfMentionMap (keyDoc);
		responseToKeyMentionMap = new HashMap();
		keyToResponseMentionMap = new HashMap();

		Vector responseMentions = findMentions(responseDoc);
		if (responseMentions != null) {
			for (int j = 0; j < responseMentions.size();  j++) {
				Annotation responseMention = (Annotation) responseMentions.elementAt(j);
				Annotation responseMentionHead = Resolve.getHeadC(responseMention);
				int end = responseMentionHead.span().end();
				Annotation keyMention =
				    (Annotation) endOfKeyMentionMap.get(new Integer(end));
				if (keyMention != null) {
					responseToKeyMentionMap.put(responseMention, keyMention);
					keyToResponseMentionMap.put(keyMention, responseMention);
					mappedMentionCount++;
				}
			}
		}
	}

	/**
	 *  builds a map from the end position of the head of a mention
	 *  to the mention.
	 */

	static HashMap buildEndOfMentionMap (Document doc) {
		HashMap endOfMentionMap = new HashMap();
		Vector mentions = findMentions(doc);
		if (mentions != null) {
			for (int j = 0; j < mentions.size();  j++) {
				Annotation mention = (Annotation) mentions.elementAt(j);
				Annotation mentionHead = Resolve.getHeadC(mention);
				int end = mentionHead.span().end();
				endOfMentionMap.put(new Integer(end), mention);
			}
		}
		return endOfMentionMap;
	}

	/**
	 *  return a Vector of all the mentions in the document (the union of
	 *  the 'mentions' feature of all entities).
	 */

	public static Vector findMentions (Document doc) {
		Vector mentions = new Vector();
		Vector entities = doc.annotationsOfType("entity");
		if (entities != null) {
			for (int j=0; j<entities.size(); j++) {
				Annotation entity = (Annotation) entities.get(j);
				Vector mentionsOfEntity = (Vector) entity.get("mentions");
				mentions.addAll(mentionsOfEntity);
			}
		}
		return mentions;
	}

	/**
	 *  build and return a map from mentions to the entities which contain
	 *  them.
	 */

	private HashMap buildMentionToEntityMap (Document doc, Vector entities) {
		HashMap map = new HashMap();
		for (int i=0; i<entities.size(); i++) {
			Annotation entity = (Annotation) entities.get(i);
			Vector mentions = (Vector) entity.get("mentions");
			if (mentions == null) {
				System.err.println ("(buildMentionToEntityMap) entity with no mentions");
				return null;
			}
			for (int j=0; j<mentions.size(); j++) {
				Annotation mention = (Annotation) mentions.get(j);
				map.put(mention,entity);
			}
		}
		return map;
	}

	private int scoreNumerator, scoreDenominator;

	private float scoreEntities (Vector entities, HashMap mentionMap,
	                            HashMap mentionToEntityMap, boolean precision) {
		scoreNumerator = 0;
		scoreDenominator = 0;
		for (int i=0; i<entities.size(); i++) {
			Annotation entity = (Annotation) entities.get(i);
			scoreEntity (entity, mentionMap, mentionToEntityMap);
		}
		if (precision) {
			precisionNumerator += scoreNumerator;
			precisionDenominator += scoreDenominator;
		} else {
			recallNumerator += scoreNumerator;
			recallDenominator += scoreDenominator;
		}
		return ((float) scoreNumerator) / scoreDenominator;
	}

	/**
	 *  increments the scoring values (scoreNumerator and scoreDenominator)
	 *  for entity 'entity1' (which may be either a key or response entity)
	 *  relative to the complementary entities (in the response or key).
	 */

	private void scoreEntity (Annotation entity1, HashMap mentionMap,
	                          HashMap mentionToEntityMap) {
		Vector mentions1 = (Vector) entity1.get("mentions");
		int unmappedMentionsInEntity = 0;
		int mappedMentionsInEntity = 0;
		HashSet mappedEntities = new HashSet();
		for (int i=0; i<mentions1.size(); i++) {
			Annotation mention1 = (Annotation) mentions1.get(i);
			Annotation mention2 = (Annotation) mentionMap.get(mention1);
			if (mention2 == null) {
				unmappedMentionsInEntity++;
			} else {
				mappedMentionsInEntity++;
				Annotation entity2 = (Annotation) mentionToEntityMap.get(mention2);
				mappedEntities.add(entity2);
			}
		}
		// 1.3.04 - ignore unmapped mentions
		// int entitySize = mentions1.size();
		// int partitionSize = unmappedMentionsInEntity + mappedEntities.size();
		// scoreNumerator += entitySize - partitionSize;
		// scoreDenominator += entitySize - 1;

		if (mappedMentionsInEntity > 0) {
			scoreNumerator += mappedMentionsInEntity - mappedEntities.size();
			scoreDenominator += mappedMentionsInEntity -1;
		}
	}

	/**
	 *  writes to standard output a report on the most recently
	 *  scored document.
	 */

	public void report () {
		System.out.println ("Recall = " + recall);
		System.out.println ("Precision = " + precision);
		System.out.println (mappedMentionCount + " mapped mentions");
	}

	/**
	 *  writes to standard output a report on the overall accuracy
	 *  for all documents processed so far.
	 */

	public void summary () {
		System.out.println ("");
		System.out.println ("Overall Recall = " + overallRecall);
		System.out.println ("Overall Precision = " + overallPrecision);
		float FO = 2.f / (1.f / overallRecall + 1.f / overallPrecision);
		System.out.println ("F = " + FO);
		System.out.println (overallMappedMentionCount + " total mapped mentions");
	}

	public static void main (String[] args) throws IOException {
		String home = System.getProperty("user.home") + "\\";
		new AnnotationColor(home + "My Documents\\Jet");
		// read coref key
		String keyFile = home + "My Documents\\Jet\\Data\\coref.txt";
		String[] tags = {"coref"};
		ExternalDocument keyDoc = new ExternalDocument("sgml", keyFile);
		keyDoc.setSGMLtags(tags);
		keyDoc.open();
		// convert it
		CorefFilter.buildEntitiesFromLinkedMentions(keyDoc);
		// display it
		new View(keyDoc, 0);
		new EntityView(keyDoc, 1);
		JetTest.initializeFromConfig ("chunk3.properties");
		// read document
		String testFile = home + "My Documents\\Jet\\Data\\article.txt";
		Document testDoc = JetTest.readDocument (new BufferedReader (new FileReader (testFile)));
		// process document
		Control.processDocument (testDoc, null, true, 1);
		// display test document
		new EntityView(testDoc,2);
		// score document
		CorefScorer scorer = new CorefScorer();
		scorer.score(testDoc, keyDoc);
		System.out.println ("Recall = " + scorer.recall);
		System.out.println ("Precision = " + scorer.precision);
	}

}
