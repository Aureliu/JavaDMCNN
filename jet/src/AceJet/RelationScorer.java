// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.51
//Copyright(c): 2011
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package AceJet;

import java.util.*;
import java.io.*;

import Jet.Tipster.*;

/**
 *  RelationScorer provides a simple procedure for scoring the relations in
 *  an ACE APF file.  Entities are first aligned based on the end of the
 *  head of the entity.  Relations are then considered correct if both
 *  arguments are aligned between key and response and the types of the
 *  key and response relations are correct (subtypes are not considered).
 */

public class RelationScorer {

	static int correctEntityMentions = 0;
	static int missingEntityMentions = 0;
	static int spuriousEntityMentions = 0;
	static int correctRelationMentions = 0;
	static int missingRelationMentions = 0;
	static int spuriousRelationMentions = 0;
	static int relationMentionTypeErrors = 0;

	static Map<String, String> entityAlignment = new HashMap<String, String> ();

	static Set<String> symmetricTypes = new HashSet<String> ();
	static {
		// symmetricTypes.add("PHYS.Located");       // symmetric in 2005 scorer but not a symmetric relation
		symmetricTypes.add("PHYS:Near");             // 2004 and 2005
		symmetricTypes.add("PER-SOC:Business");      // 2004 and 2005
		symmetricTypes.add("PER-SOC:Family");        // 2004 and 2005
		symmetricTypes.add("PER-SOC:Other");         // 2004
		symmetricTypes.add("PER-SOC:Lasting-Personal"); // 2005
		symmetricTypes.add("EMP-ORG:Partner");       // 2004
		symmetricTypes.add("EMP-ORG:Other");         // 2004
		symmetricTypes.add("OTHER-AFF:Other");       // 2004
		}

	/**
	 *  score an APF file for relations.  Invoked by <br>
	 *  RelationScorer docList sourceDir sourceExt responseDir responseExt keyDir keyExt
	 */

	public static void main (String[] args) throws IOException {

		if (args.length < 7 || args.length > 8) {
			System.err.println ("RelationScorer requires 7 or 8 arguments:");
			System.err.println ("    docList sourceDir sourceExt responseDir responseExt keyDir keyExt [year]");
			System.exit (1);
		}
		String docListFile = args[0];
		String sourceDir = args[1];
		String sourceExt = args[2];
		String responseDir = args[3];
		String responseExt = args[4];
		String keyDir = args[5];
		String keyExt = args[6];
		if (args.length == 8)
			Ace.setAceYear (args[7]);
		BufferedReader reader = new BufferedReader (new FileReader (docListFile));
		String docName;
		int docCount = 0;
		while ((docName = reader.readLine()) != null) {
			docCount++;
			System.out.println ("\nScoring document " + docCount + ": " + docName);
			String sourceFile = sourceDir + "/" + docName + "." + sourceExt;
			String responseFile = responseDir + "/" + docName + "." + responseExt;
			String keyFile = keyDir + "/" + docName + "." + keyExt;
			scoreDocument (sourceFile, responseFile, keyFile);
		}
		
		reportScores ();
	}

	static void scoreDocument (String sourceFile, String responseFile, String keyFile) {
		
		ExternalDocument doc = new ExternalDocument("sgml", sourceFile);
		doc.setAllTags(true);
		doc.open();
		AceDocument response = new AceDocument(sourceFile, responseFile);
		AceDocument key = new AceDocument(sourceFile, keyFile);

		// collect key entity mentions
		Set<AceEntityMention> keyEntityMentions = new HashSet<AceEntityMention>();
		for (AceEntity entity : key.entities)
			keyEntityMentions.addAll(entity.mentions);
		Map<Integer, AceEntityMention> keyEntityMentionMap = new HashMap<Integer, AceEntityMention>();
		for (AceEntityMention mention : keyEntityMentions) {
			int end = mention.head.end();
			if(doc.charAt(end) == '.') end--;
			keyEntityMentionMap.put(end, mention);
		}
		
		// align response entity mentions			
		for (AceEntity entity : response.entities) {
			for (AceEntityMention mention : (ArrayList<AceEntityMention>) entity.mentions) {
				int end = mention.head.end();
				if(doc.charAt(end) == '.') end--;
				AceEntityMention keyMention = keyEntityMentionMap.get(end);
				if (keyMention == null) {
					System.out.println ("Spurious mention " + mention.text);
					spuriousEntityMentions++;
				} else if (!keyEntityMentions.contains(keyMention)) {
					System.out.println ("Spurious mention (duplicate head) " + mention.text);
					spuriousEntityMentions++;
				} else {
					entityAlignment.put(mention.id, keyMention.id);
					keyEntityMentions.remove(keyMention);
					correctEntityMentions++;
				}
			}
		}
		for (AceEntityMention keyMention : keyEntityMentions) {
			System.out.println ("Missing mention " + keyMention.text + " [head = " + keyMention.headText + "]");
			missingEntityMentions++;
		}

		// collect key relation mentions
		Set<AceRelationMention> keyRelationMentions = new HashSet<AceRelationMention>();
		for (AceRelation relation : key.relations)
			keyRelationMentions.addAll(relation.mentions);

		// score response relation mentions
		for (AceRelation relation : response.relations) {
			relation_loop:
			for (AceRelationMention mention : (ArrayList<AceRelationMention>) relation.mentions) {
				String type = mention.relation.type;
				String subtype = mention.relation.subtype;
				String responseArg1 = mention.arg1.id;
				String mappedArg1 = entityAlignment.get(responseArg1);
				String responseArg2 = mention.arg2.id;
				String mappedArg2 = entityAlignment.get(responseArg2);
				// look for key relation with those args
				for (AceRelationMention keyMention : keyRelationMentions) {
					String keyArg1 = keyMention.arg1.id;
					String keyArg2 = keyMention.arg2.id;
					String keyType = keyMention.relation.type;
					String keySubtype = keyMention.relation.subtype;
					boolean symmetric = symmetricTypes.contains(keyType + ":" + keySubtype);
					if ((keyArg1.equals(mappedArg1) && keyArg2.equals(mappedArg2)) ||
					    (symmetric && keyArg1.equals(mappedArg2) && keyArg2.equals(mappedArg1))) {
						// check for matching type
						if (type.equals(keyType)) 
							correctRelationMentions++;
						else
							relationMentionTypeErrors++;
						keyRelationMentions.remove(keyMention);
						continue relation_loop;
					}
				}
				// none:  spurious
				spuriousRelationMentions++;
				System.out.println("Spurious relation (" + type + ":" + subtype + "): " + mention.text);
			}
		}
		for (AceRelationMention keyMention : keyRelationMentions) {
			String type = keyMention.relation.type;
			String subtype = keyMention.relation.subtype;
			System.out.println("Missing relation (" + type + ":" + subtype + "): " + keyMention.text);
			missingRelationMentions++;
		}
	}

	static void reportScores () {
		System.out.println ();
		System.out.println ("Correct entity mentions:    " + correctEntityMentions);
		System.out.println ("Missing entity mentions:    " + missingEntityMentions);
		System.out.println ("Spurious entity mentions:   " + spuriousEntityMentions);
		System.out.println ();
		System.out.println ("Correct relation mentions:  " + correctRelationMentions);
		System.out.println ("Relation mention type errs: " + relationMentionTypeErrors);
		System.out.println ("Missing relation mentions:  " + missingRelationMentions);
		System.out.println ("Spurious relation mentions: " + spuriousRelationMentions);
		int responseCount = correctRelationMentions + relationMentionTypeErrors + spuriousRelationMentions;
		int keyCount = correctRelationMentions + relationMentionTypeErrors + missingRelationMentions;
		float precision = (float)  correctRelationMentions / responseCount;
		float recall = (float)  correctRelationMentions / keyCount;
		float f = 2 * precision * recall / (precision + recall);
		System.out.println ();
		System.out.printf ("Precision = %4.1f\n", 100 * precision);
		System.out.printf ("Recall =    %4.1f\n", 100 * recall);
		System.out.printf ("F =         %4.1f\n", 100 * f);
	}

}
