// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.81
//Copyright:    Copyright (c) 2015
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package AceJet;

import java.util.*;
import java.io.*;
import Jet.*;
import Jet.Parser.SynFun;
import Jet.Parser.ParseTreeNode;
import Jet.Parser.SyntacticRelationSet;
import Jet.Parser.DepParser;
import Jet.Refres.Resolve;
import Jet.Lisp.*;
import Jet.Pat.Pat;
import Jet.Tipster.*;
import Jet.Zoner.SentenceSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  a relation tagger based on dependency paths and argument types,
 *  as produced by Jet ICE.
 */

public class DepPathRelationTagger {

	final static Logger logger = LoggerFactory.getLogger(DepPathRelationTagger.class);

	static Document doc;
	static AceDocument aceDoc;
	static String currentDoc;

	// model:  a map from AnchoredPath strings to relation types
	static Map<String, String> model = null;

	/**
	 *  relation 'decoder':  identifies the relations in document 'doc' 
	 *  (from file name 'currentDoc') and adds them
	 *  as AceRelations to AceDocument 'aceDoc'.
	 */

	public static void findRelations (String currentDoc, Document d, AceDocument ad) {
		doc = d;
		RelationTagger.doc = d;
		doc.relations.addInverses();
		aceDoc = ad;
		RelationTagger.docName = currentDoc;
		RelationTagger.sentences = new SentenceSet(doc);
		RelationTagger.relationList = new ArrayList<AceRelation> ();
		RelationTagger.findEntityMentions (aceDoc);
		// collect all pairs of nearby mentions
		List<AceEntityMention[]> pairs = RelationTagger.findMentionPairs ();
		// iterate over pairs of adjacent mentions, using model to determine which are ACE relations
		for (AceEntityMention[] pair : pairs)
			predictRelation (pair[0], pair[1], doc.relations);
		// combine relation mentions into relations
		RelationTagger.relationCoref (aceDoc);
		RelationTagger.removeRedundantMentions (aceDoc);
	}

	/**
	 *  load the model used by the relation tagger.  Each line consists of an
	 *  AnchoredPath [a lexicalized dependency path with information on the
	 *  endpoint types], a tab, and a relation type.
	 */

	static void loadModel (String modelFile) throws IOException {
		model = new TreeMap<String, String>();
		BufferedReader reader = new BufferedReader (new FileReader (modelFile));
		String line;
		int n = 0;
		while ((line = reader.readLine()) != null) {
			String[] fields = line.split("\t");
			String pattern = fields[0];
			pattern = pattern.replace("would:vch:", "");
			pattern = pattern.replace("be:vch:", "");
			pattern = pattern.replace("were:vch:", "");
			String outcome = fields[1];
			model.put (pattern, outcome);
			n++;
		}
		System.out.println ("Loaded relation model with " + n + " relation paths.");
	}


	/**
	 *  use dependency paths to determine whether the pair of mentions bears some
	 *  ACE relation;  if so, add the relation to relationList.
	 */

	private static void predictRelation (AceEntityMention m1, AceEntityMention m2,
			SyntacticRelationSet relations) {
		// compute path
		int h1 = m1.getJetHead().start();
		int h2 = m2.getJetHead().start();
		String path = EventSyntacticPattern.buildSyntacticPath(h1, h2, relations);
		if (path == null) return;
		path = AceJet.AnchoredPath.reduceConjunction (path);
		if (path == null) return;
		path = AceJet.AnchoredPath.lemmatizePath (path);
			// simplify path to improve recall
			path = path.replace("would:vch:", "");
			path = path.replace("be:vch:", "");
			path = path.replace("were:vch:", "");
		// build pattern = path + arg types
		String pattern = m1.entity.type + "--" + path + "--" + m2.entity.type;
		// look up path in model
		String outcome = model.get(pattern);
		if (outcome == null) return;
		if (!RelationTagger.blockingTest(m1, m2)) return;
		if (!RelationTagger.blockingTest(m2, m1)) return;
		String[] typeSubtype = outcome.split(":", 2);
		String type = typeSubtype[0];
		String subtype;
		if (typeSubtype.length == 1) {
			subtype = "";
		} else {
			subtype = typeSubtype[1];
		}
		if (subtype.endsWith("-1")) {
			subtype = subtype.replace("-1","");
			AceRelationMention mention = new AceRelationMention("", m2, m1, doc);
			AceRelation relation = new AceRelation("", type, subtype, "", m2.entity, m1.entity);
			relation.addMention(mention);
			RelationTagger.relationList.add(relation);
		} else {
			AceRelationMention mention = new AceRelationMention("", m1, m2, doc);
System.out.println ("Found " + outcome + " relation " + mention.text);  //<<<
			AceRelation relation = new AceRelation("", type, subtype, "", m1.entity, m2.entity);
			relation.addMention(mention);
			RelationTagger.relationList.add(relation);
		}
	}
	
}
