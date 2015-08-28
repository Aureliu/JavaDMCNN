// -*- tab-width: 4 -*-
package AceJet;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;
import Jet.Lisp.FeatureSet;
import Jet.Refres.*;
import Jet.Pat.NewAnnotationAction;
import Jet.JetTest;
import Jet.Control;
import Jet.Pat.Pat;
import Jet.Parser.StatParser;
import Jet.Zoner.SpecialZoner;

/**
 *  contains methods which create perfect mentions or entities from
 *  APF file.
 */

public class PerfectAce extends Resolve {

	/**
	 *  generate parse collection with perfect named entities
	 */

	public static void main (String[] args) throws IOException {
		String home = "C:/Documents and Settings/Ralph Grishman/My Documents/";
		// ---- 2004 --------------------------------------------------------------------
		// String ACEdir = home + "ACE/";
		// ---- for testing
		// String collection = ACEdir + "training04 nwire 20 sgm.txt";
		// String parseCollection = ACEdir + "perfect parses/training04 nwire 20 parses.txt";
		// ---- for NIST (single) test file
		// String collection = ACEdir + "training04 perfect mention sgm.txt";
		// String parseCollection = ACEdir + "perfect parses/training04 perfect mention sgm.txt";
		// ---- coref evaluation
		// String collection = ACEdir + "corefeval04 nwire sgm.txt";
		// String parseCollection = ACEdir + "perfect parses/corefeval04 nwire parses.txt";
		// String collection = ACEdir + "corefeval04 bnews sgm.txt";
		// String parseCollection = ACEdir + "perfect parses/corefeval04 bnews parses.txt";
		// ---- rdr evaluation
		// String collection = ACEdir + "rdreval04 nwire sgm.txt";
		// String parseCollection = ACEdir + "perfect parses/rdreval04 nwire parses.txt";
		// String collection = ACEdir + "rdreval04 bnews sgm.txt";
		// String parseCollection = ACEdir + "perfect parses/rdreval04 bnews parses.txt";
		// ---- 2005 -------------------------------------------------------------------
		String ACEdir = home + "ACE 05/V4/";
		// String parseCollection = ACEdir + "perfect-parses/nw-sgm.txt";
		// String parseCollection = ACEdir + "perfect-parses/bn-sgm.txt";
		// String parseCollection = ACEdir + "perfect-parses/bc-sgm.txt";
		String parseCollection = ACEdir + "perfect-parses/un-sgm.txt";
		// String parseCollection = ACEdir + "perfect-parses/wl-sgm.txt";
		// String parseCollection = ACEdir + "perfect-parses/cts-sgm.txt";
		// Ace.perfectMentions = true;
		parseCollection (parseCollection);
	}

	/**
	 *  modified version of 'parseCollection' from StatParser, modified to call
	 *  perfectNames after nameTagger and before parser.
	 *  <BR>
	 *  Modified 27 Sep 2005 to parse 'in place', replacing the documents without
	 *  parses with documents with parse annotations.  Before parsing a document,
	 *  check whether it already has been parsed;  if so, skip it.  This allows
	 *  the 'parseCollection' process to be interrupted and restarted with
	 *  minimal loss (only the partial document being processed).  The APF file
	 *  is assumed to be in the same directory as the .sgm (text) file.
	 */

	// private static void parseCollection (String collection, String parsedCollection)
	private static void parseCollection (String collection) // Sep 27 05
		    throws IOException {
		System.out.println("Starting ACE Jet...");
		JetTest.initializeFromConfig("props/ace perfect just parser.properties");
		// turn off traces
		Pat.trace = false;
		// open text collection
		DocumentCollection col = new DocumentCollection(collection);
		col.open();
	docLoop:
		for (int docCount = 0; docCount < col.size(); docCount++) {
			// process file 'currentDoc'
			// if (docCount != 3) continue;
			ExternalDocument doc = col.get(docCount);
			System.out.println ("\nProcessing document " + docCount + ": " + doc.fileName());
			// read test document
			doc.setAllTags(true);
			// doc.setEmptyTags(new String[] {"W"});
			doc.open();
			Vector anns = doc.annotationsOfType("sentence");
			if (anns != null) {   // Sep 27 05
				for (int i=0; i<anns.size(); i++) {
					Annotation sentAnn = (Annotation) anns.get(i);
					if (sentAnn.get("parse") != null) {
						System.out.println ("\nSkipping document " + doc.fileName() + ", already parsed");
						continue docLoop;
					}
				}
			}
			SpecialZoner.findSpecialZones (doc);
			String textFile = doc.fullFileName();
			String apfFile = textFile.replaceAll(".sgm", ".apf.xml"); // << for testing
			// String apfFile = textFile.replaceAll(".sgm", ".mentions.apf.xml"); // << for coref eval
			// String apfFile = textFile.replaceAll(".sgm", ".entities.apf.xml"); // << for rdr eval
			AceDocument aceDoc = new AceDocument(textFile, apfFile);
			// process document
			AceJet.Ace.monocase = AceJet.Ace.allLowerCase(doc);
			Control.processDocument (doc, null, docCount == -1, docCount);
			createPerfectNames (doc, aceDoc);
			Vector sentences = doc.annotationsOfType("sentence");
			for (int i=0; i<sentences.size(); i++) {
				Annotation sentence = (Annotation) sentences.get(i);
				StatParser.parse (doc, sentence.span());
			}
			// remove name and tagger annotations -- not needed after parsing
			doc.removeAnnotationsOfType ("ENAMEX");
			StatParser.clearInputAnnotations (doc);
			doc.removeAnnotationsOfType ("tagger");
			doc.save(); // Sep. 27 05
		}
		// col.saveAs (parsedCollection);  // Sep. 27 05
	}

	static void createPerfectNames (ExternalDocument doc, AceDocument aceDoc) {
		// assign a constit cat=name annotation if either
		//  1) APF file has a name annotation
		//  2) APF file has a PRE annotation and it corresponds to an ENAMEX annotation
		//     produced by the name tagger (only needed for Ace 2004 data)
		ArrayList entities = aceDoc.entities;
		for (int i=0; i<entities.size(); i++) {
			AceEntity entity = (AceEntity) entities.get(i);
			ArrayList perfectMentions = entity.mentions;
			for (int j=0; j<perfectMentions.size(); j++) {
				AceEntityMention mention = (AceEntityMention) perfectMentions.get(j);
				LearnRelations.doc = doc;
				Span head = mention.jetHead;
				String type = mention.type;
				// PRE:  for 2004 files only
				if (type.equals("PRE")) {
					Vector enamexes = doc.annotationsAt(head.start(), "ENAMEX");
					if (enamexes != null) {
						Annotation enamex = (Annotation) enamexes.get(0);
						if (enamex.end() == head.end()) {
							type = "NAM";
						}
					}
				}
				if (type.equals("NAM")) {
					// stretch 'head' span to next token boundary
					// (annotation end may not align with Jet token boundaries)
					int start = head.start();
					int posn = start;
					int end = head.end();
					while (posn < end) {
						Annotation token = doc.tokenAt(posn);
						if (token == null)
							// if we can't find start of token, use given jetExtent
							posn = end;
						else
							posn = token.end();
					}
					Span span = new Span(start, posn);
					NewAnnotationAction.hideAnnotations (doc, "constit", span);
	    		NewAnnotationAction.hideAnnotations (doc, "token", span);
					Annotation name = new Annotation ("constit", span,
					                                  new FeatureSet
					                                        ("cat", "name",
					                                         "pa", new FeatureSet
					                                                      ("head", entity.type.toLowerCase(),
					                                                       "number", "singular")));
					doc.addAnnotation(name);
	    		// System.out.println ("Found name " + name);
				}
			}
		}
	}

	static HashMap entityMentionMap = null;

	static void buildEntityMentionMap (ExternalDocument doc, AceDocument aceDoc) {
		entityMentionMap = new HashMap();
		ArrayList entities = aceDoc.entities;
		for (int i=0; i<entities.size(); i++) {
			AceEntity entity = (AceEntity) entities.get(i);
			ArrayList perfectMentions = entity.mentions;
			for (int j=0; j<perfectMentions.size(); j++) {
				AceEntityMention mention = (AceEntityMention) perfectMentions.get(j);
				LearnRelations.doc = doc;
				Span head = mention.jetHead; // <<< modified 10 Aug 05
				entityMentionMap.put(new Integer(head.start()),
				                     new Object[] {entity, mention});
			}
		}
	}

	public static boolean validMention (Document doc, Annotation head, String cat) {
		if (entityMentionMap == null) {
			System.err.println ("*** PerfectAce.validMention:  no entityMentionMap");
			return true;
		}
		// for titles, we recognize multi-word titles as heads, while LDC
		// takes only the last word as head ("Minister" of "Prime Minister")
		if (cat == "title") {
			Annotation[] tokens = Jet.Lex.Tokenizer.gatherTokens(doc, head.span());
			for (int i=0; i<tokens.length; i++) {
				Integer start = new Integer(tokens[i].start());
				if (entityMentionMap.get(start) != null) return true;
			}
			return false;
		} else {
			Integer start = new Integer(head.start());
			boolean valid = entityMentionMap.get(start) != null;
			// System.out.println ("For " + head + " valid = " + valid);
			return valid;
		}
	}

	public static String getEntityID (Annotation head) {
		if (entityMentionMap == null)
			return null;
		Integer start = new Integer(head.start());
		Object[] entityMention = (Object[]) entityMentionMap.get(start);
		if (entityMention == null)
			return null;
		AceEntity entity = (AceEntity) entityMention[0];
		return entity.id;
	}

	public static String getTypeSubtype (Annotation head) {
		if (entityMentionMap == null)
			return null;
		Integer start = new Integer(head.start());
		Object[] entityMention = (Object[]) entityMentionMap.get(start);
		if (entityMention == null) {
			System.err.println ("*** No entityMentionMap entry for " + start);
			return "OTHER";
		}
		AceEntity entity = (AceEntity) entityMention[0];
		if (entity.subtype != null && !entity.subtype.equals("")) {
			return entity.type + ":" + entity.subtype;
		} else {
			return entity.type;
		}
	}

	static String getMentionRole (Annotation head) {
		if (entityMentionMap == null)
			return "GPE";
		Integer start = new Integer(head.start());
		Object[] entityMention = (Object[]) entityMentionMap.get(start);
		if (entityMention == null)
			return "GPE";
		AceEntityMention mention = (AceEntityMention) entityMention[1];
		return mention.role;
	}

	static String getMentionType (Annotation head) {
		if (entityMentionMap == null)
			return "NOMINAL";
		Integer start = new Integer(head.start());
		Object[] entityMention = (Object[]) entityMentionMap.get(start);
		if (entityMention == null)
			return "NOMINAL";
		AceEntityMention mention = (AceEntityMention) entityMention[1];
		return mention.type;
	}
	/*

	void createPerfectMentions (Document doc, Span span, AceDocument aceDoc) {
		Vector mentions = new Vector();
		Vector perfectMentions = collectApfMentions (aceDoc);
		Vector textMentions = Resolve.gatherMentions (doc, span);
		HashMap mentionIndex = createMentionIndex (textMentions);
		for (int i=0; i<perfectMentions.size(); i++) {
			AceEntityMention mention = (AceEntityMention) perfectMentions.get(i);
			Span extent = LearnRelations.aceSpanToJetSpan (mention.extent);
			Span head = LearnRelations.aceSpanToJetSpan (mention.head);
			// look up mention in mentions
			Annotation textMention = (Annotation) mentionIndex.get(new Integer(head.start()));
			if (textMention != null) {
				mentions.add(textMention);
			} else {
			// if not present, create mention annotation
	  		Annotation m = new Annotation("mention", extent, new FeatureSet());
	  		doc.addAnnotation(m);
	  		mentions.add(m);
	  	}
	  }

	  Resolve.references (doc, span, mentions);
	}

	Vector collectApfMentions (AceDocument aceDoc) {
		Vector allMentions = new Vector();
		//
		ArrayList entities = aceDoc.entities;
		for (int i=0; i<entities.size(); i++) {
			AceEntity entity = (AceEntity) entities.get(i);
			allMentions.addAll(entity.mentions);
		}
		return allMentions;
	}

	HashMap createMentionIndex (Vector mentions) {
		HashMap mentionStartMap = new HashMap();
		for (int i=0; i<mentions.size(); i++) {
			Annotation mention = (Annotation) mentions.get(i);
			Annotation headC = Resolve.getHeadC (mention);
			int start = headC.start();
			mentionStartMap.put(new Integer(start), mention);
		}
		return mentionStartMap;
	}

	*/

}
