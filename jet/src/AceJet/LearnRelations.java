// -*- tab-width: 4 -*-
package AceJet;

//Author:       Ralph Grishman
//Date:         July 25, 2003

import java.util.*;
import java.io.*;
import Jet.*;
import Jet.Control;
import Jet.Parser.SynFun;
import Jet.Parser.ParseTreeNode;
import Jet.Refres.Resolve;
import Jet.Lex.Tokenizer;
import Jet.Lisp.*;
import Jet.Pat.Pat;
import Jet.Tipster.*;
import Jet.Chunk.Chunker;
import Jet.Zoner.SentenceSet;

/**
 *  procedures for learning the syntactic indications of ACE relations
 *     1. process document, creating constituents with syntactic relations
 *     2. read in ACE relations, building relMentionList
 *     3. iterate over syntactic relations;
 *          if pair links two candidates, record it on candidate list
 *     4. iterate over pairs of consecutive mentions, record on candidate list
 *     5. review candidates [this is done by processCandidates],
 *        report each either as relation (if it appear on relMentionList)
 *        or as non-relation
 *     6. report ACE relations not covered [processLeftovers]
 */

public class LearnRelations {

	static boolean useParser = false;
	static boolean useParseCollection = true;
	public static boolean relationTrace = false;
	static String textFileSuffix = ".sgm";
	static String apfFileSuffix = ".apf.xml";
	static Document doc;
	static String currentDoc;
	// the set of sentences in doc
	static SentenceSet sentences;
	static final String[] relations = {"of", "poss", "nameMod"};
	// maximum dissimilarity of pattern and test case
	// (above this maximum, generate no relation)
	static int maxDistance = 21;

	// mapping from mention ID to Mention
	static HashMap mentionIDMap;
	// mapping from start of head to Mention
	static HashMap mentionStartMap;
	// set of mentions (excluding generic)
	static TreeSet<AceEntityMention> mentionSet;
	// set of all mentions, including generics
	static TreeSet<AceEntityMention> allMentionSet;
	// list of relation mentions (from APF file)
	static ArrayList<RelationMention> relMentionList;
	// list of entity mention pairs which are candidates for relations
	static ArrayList candidates;
	// list of relations (formed from relation mentions)
	static ArrayList<AceRelation> relationList;
	// set of generated patterns (with their frequency)
	static TreeMap<String, Integer> patternSet = new TreeMap<String, Integer>();
	// file onto which patterns are written
	static PrintStream writer;
	// name of current doc (used in generating ID's)
	static String docName;
	// true if writing relations file
	static boolean writingRelations = false;
	// set true to expand conjuncts
	static final boolean expandConjuncts = true;
	// map from a mention to following conjunct
	static HashMap conjunctf = new HashMap();
	// map from a mention to prior conjunct
	static HashMap conjunctb = new HashMap();

	/**
	 *  creates a new relation learner, using Jet properties file 'configFile',
	 *  which writes its output (a file of patterns) to 'patternFile'.
	 */

	LearnRelations (String configFile, String patternFile) throws IOException {
		writingRelations = true;
		// initialize Jet
		System.out.println("Learning relations ...");
		JetTest.initializeFromConfig (configFile);
		Pat.trace = false;
		Resolve.trace = false;
		Resolve.ACE = true;

		writer = new PrintStream (new FileOutputStream (patternFile));
	}

	/**
	 *  terminate a relation learner, writing a report on patterns learned
	 *  to standard output.
	 */

	void finish () {
		reportPatterns();
		writer.close();
	}

	void learnFromFileList (String fileList, String textDir, String apfDir) throws IOException {
		// open list of files
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		while ((currentDoc = reader.readLine()) != null) {
			// process file 'currentDoc'
			docCount++;
			// if (docCount >= 150) break;
			System.out.println ("\nProcessing document " + docCount + ": " + currentDoc);
			// read document
			String textFile;
			textFile = textDir + currentDoc + textFileSuffix;
			ExternalDocument xdoc = new ExternalDocument("sgml", textFile);
			xdoc.setAllTags(true);
			xdoc.open();
			doc = xdoc;
			// read key file with relation information
			readACErelations (textFile, apfDir + currentDoc + apfFileSuffix);
			// process document (unless starting from parse, identify named
			//                   entities, syntactic structures, and coreference)
			Ace.monocase = Ace.allLowerCase(doc);
			System.out.println (">>> Monocase is " + Ace.monocase);
			Control.processDocument (doc, null, docCount < 0, docCount);
			sentences = new SentenceSet(doc);
			// clear table of candidate mention pairs
			// this will record mention pairs which *might* be connected by a relation
			candidates = new ArrayList();
			// iterate over syntactic relations, record candidates for ACE relations
			findSyntacticPatterns (doc);
			// iterate over pairs of adjacent mentions, record candidates for ACE relations
			findAdjacencyPatterns ();
			// process candidates:  for each candidate, see if it corresponds to a
			//                      relation in the ACE key
			processCandidates();
			// process remaining ACE relations in key (should not be many)
			processLeftovers ();
		}
	}

	/**
	 *  relation 'decoder':  using previously learned patterns, identifies
	 *  the relations in document 'd' (from file name 'currentDoc') and adds them
	 *  as AceRelations to AceDocument 'aceDoc'.
	 */

	public static void findRelations (String currentDoc, Document d, AceDocument aceDoc) {
		doc = d;
		docName = currentDoc;
		sentences = new SentenceSet(doc);
		// System.out.println ("PartitiveMap: " + Ace.partitiveMap);
		// clear table of candidate mention pairs
		candidates = new ArrayList();
		// record conjunct links for mentions
		findConjuncts (doc);
		// iterate over syntactic relations, record candidates for ACE relations
		findSyntacticPatterns (doc);
		// iterate over pairs of adjacent mentions, record candidates for ACE relations
		findAdjacencyPatterns ();
		// use model to determine which are ACE relations
		predictRelations ();
		// combine relation mentions into relations
		relationCoref (aceDoc);
	}

	/**
	 *  iterate over all syntactic relations;  for each, check if it
	 *  corresponds to an ACE relation.
	 */

	private static void findSyntacticPatterns (Document doc) {
		Vector constits = doc.annotationsOfType("constit");
		if (constits != null) {
			for (int j = 0; j < constits.size();  j++) {
				Annotation ann = (Annotation) constits.elementAt(j);
				for (int r = 0; r < relations.length; r++) {
					String relation = relations[r];
					if (ann.get(relation) != null) {
						Annotation value = (Annotation) ann.get(relation);
						checkSyntacticRelation (ann, relation, value);
					}
				}
				String verb = SynFun.getImmediateHead(ann);
				if (verb != null) {
					Annotation subject = (Annotation) ann.get("subject");
					Annotation object = (Annotation) ann.get("object");
					Annotation pp = (Annotation) ann.get("pp");
					if (subject != null && object != null) {
					/* -- version for use with chunker
					Annotation vp = (Annotation) ann.get("headC");
					if (vp.get("object") != null) {
						Annotation object = (Annotation) vp.get("object");
						String verb = SynFun.getNameOrHead(doc, vp);
						*/
						checkSyntacticRelation (subject, verb, object);
					}
					if (pp != null) {
						Annotation[] ppChildren = ParseTreeNode.children(pp);
						if (ppChildren != null & ppChildren.length == 2) {
							Annotation pNode = ppChildren[0];
							Annotation pObject = ppChildren[1];
							String p = SynFun.getHead(doc, pNode);
							if (subject != null)
								checkSyntacticRelation (subject, "s-" + verb + "-" + p, pObject);
							if (object != null)
								checkSyntacticRelation (object, "o-" + verb + "-" + p, pObject);
						}
					}
				}
			}
		}
	}

	/**
	 *  given a syntactic relation between two constituents, arg1 and arg2,
	 *  look for corresponding mentions;  if found, record as candidate for
	 *  relation.
	 */

	private static void checkSyntacticRelation
		(Annotation arg1, String relation, Annotation arg2) {
		// for each argument (constituent), find correponding Mention (if any)
		Annotation arg1Head = Resolve.getHeadC (arg1);
		Span span1 = arg1Head.span();
		int start1 = span1.start();
		AceEntityMention m1 = (AceEntityMention) mentionStartMap.get(new Integer(start1));
		if (m1 == null) return;
		Annotation arg2Head = Resolve.getHeadC (arg2);
		Span span2 = arg2Head.span();
		int start2 = span2.start();
		AceEntityMention m2 = (AceEntityMention) mentionStartMap.get(new Integer(start2));
		if (m2 == null) return;
		// if two mentions co-refer, they can't be in a relation
		if (!canBeRelated(m1, m2)) return;
		if (m1.compareTo(m2) < 0) {
			ArrayList rels = recordCandidateWithConjuncts (m1, m2);
			recordSyntacticLink(rels, relation);
		} else {
			ArrayList rels = recordCandidateWithConjuncts (m2, m1);
			recordSyntacticLink(rels, relation + "-1");
		}
	}

	private static boolean canBeRelated (AceEntityMention m1, AceEntityMention m2) {
		if (m1.entity.id.equals(m2.entity.id)) return false;
		return true;
	}

	private static final int mentionWindow = 3;
	private static final int maxPatternLength = 8;

  /**
   *  iterate over all pairs of mentions separated by at most mentionWindow
   *  mentions and record them as patterns
   */

	private static void findAdjacencyPatterns () {
		if (mentionSet.isEmpty()) return;
		ArrayList mentionList = new ArrayList(mentionSet);
		for (int i=0; i<mentionList.size()-1; i++) {
			for (int j=1; j<=mentionWindow && i+j<mentionList.size(); j++) {
				AceEntityMention m1 = (AceEntityMention) mentionList.get(i);
				AceEntityMention m2 = (AceEntityMention) mentionList.get(i+j);
				// if two mentions co-refer, they can't be in a relation
				if (!canBeRelated(m1, m2)) continue;
				// if two mentions are not in the same sentence, they can't be in a relation
				if (!sentences.inSameSentence(m1.jetHead.start(), m2.jetHead.start())) continue;
				// try for pattern between m1 and m2
				ChunkPath chunkPath = new ChunkPath (doc, m1, m2);
				// if pattern crosses sentence boundary, skip (don't use)
				if (chunkPath.size() >= 0 && chunkPath.size() <= maxPatternLength
				                          && !chunkPath.contains("]")) {
					ArrayList rels = recordCandidateWithConjuncts (m1, m2);
					recordLinearLink(rels, chunkPath);
				}
			}
		}
	}

	/**
	 *  returns a candidate relationMention connecting entity mentions 'm1' and
	 *  'm2'.  If such a candidate already exists (on candidate list), returns
	 *  it, else creates a new relationMention.
	 */

	private static RelationMention recordCandidate (AceEntityMention m1, AceEntityMention m2) {
		// assumes m1 precedes m2
		for (int i=0; i<candidates.size(); i++) {
			RelationMention r = (RelationMention) candidates.get(i);
			if (r.mention1 == m1 && r.mention2 == m2) return r;
		}
		RelationMention r = new RelationMention (m1, m2);
		candidates.add(r);
		return r;
	}

	/**
	 *  process all 'candidates' during training:  for each one, create a pattern
	 *  and then call checkRelation to see if it corresponds to an ACE pattern
	 *  in the key.
	 */

	private static void processCandidates () {
		for (int i=0; i<candidates.size(); i++) {
			RelationMention r = (RelationMention) candidates.get(i);
			AceEntityMention m1 = r.mention1;
			AceEntityMention m2 = r.mention2;
			if (m1.entity.generic || m2.entity.generic) continue;
			String type1 = m1.entity.type;
			String type2 = m2.entity.type;
			String subtype1 = (m1.entity.subtype.equals("")) ? "*" : m1.entity.subtype;
			String subtype2 = (m2.entity.subtype.equals("")) ? "*" : m2.entity.subtype;
			String pattern =     type1 + " " + subtype1 + " " + getHead(m1)
		                     + " [ " + r.syntacticLink + " : " + concat(r.linearLink) + " ] "
		                     + type2 + " " + subtype2 + " " + getHead(m2);
			checkRelation (m1, pattern, m2);
		}
	}

	/**
	 *  given two mentions, m1 and m2, connected by [syntactic or linear] relation
	 *  'pattern', determines whether they are connected by an ACE relation
	 */

	private static void checkRelation (AceEntityMention m1, String pattern, AceEntityMention m2) {
		// if these mentions are part of the same entity, ignore
		if (!canBeRelated(m1, m2)) return;
		boolean found = false;
		for (int i=0; i<relMentionList.size(); i++) {
			boolean match = false;
			String prefix = null;
			RelationMention rel = relMentionList.get(i);
			if (rel.mention1.equals(m1) && rel.mention2.equals(m2)) {
				match = true;
				prefix = "arg1-arg2";
			} else if (rel.mention1.equals(m2) && rel.mention2.equals(m1)) {
				match = true;
				prefix = "arg2-arg1";
			}
			if (match) {
			  System.out.println ("For " + doc.text(m1.jetHead) + " and " + doc.text(m2.jetHead));
				recordPattern (prefix + " " + pattern + " --> " + rel.relationType + " " + rel.relationSubtype);
				rel.setAnalyzed();
				found = true;
			}
		}
		// specific prefix doesn't matter for patterns which yield no relation
		if (! found) recordPattern ("arg1-arg2 " + pattern + " --> 0");
	}

	/**
	 *  look for ACE relations from the answer key which have not been processed
	 *  yet -- by syntactic or adjacency patterns.
	 */

	private static void processLeftovers () {
		for (int i=0; i<relMentionList.size(); i++) {
			RelationMention rel = relMentionList.get(i);
			if (!rel.analyzed) {
				AceEntityMention m1 = rel.mention1;
				AceEntityMention m2 = rel.mention2;
				String pattern;
				String type1 = m1.entity.type;
				String type2 = m2.entity.type;
				String subtype1 = (m1.entity.subtype.equals("")) ? "*" : m1.entity.subtype;
				String subtype2 = (m2.entity.subtype.equals("")) ? "*" : m2.entity.subtype;
				if (m1.compareTo(m2) < 0) {
					pattern = "arg1-arg2 "
			                  + type1 + " " + subtype1 + " " + getHead(m1)
			                  + " [ " + "0 : " + new ChunkPath (doc, m1, m2) + " ] "
			                  + type2 + " " + subtype2 + " " + getHead(m2);
		        } else if (m1.compareTo(m2) > 0) {
		        	pattern = "arg2-arg1 "
			                  + type2 + " " + subtype2 + " " + getHead(m2)
			                  + " [ " + "0 : " + new ChunkPath (doc, m2, m1) + " ] "
			                  + type1 + " " + subtype1 + " " + getHead(m1);
			    } else /* m1 == m2 */ {
			    	System.err.println ("*** Relation with two identical arguments -- ignored.");
			    	return;
			    }
				System.out.println ("Leftover -- for " + doc.text(m1.jetHead) + " and " + doc.text(m2.jetHead));
				recordPattern (pattern + " --> " + rel.relationType + " " + rel.relationSubtype);
			}
		}
	}

	static String concat (ArrayList strings) {
		if (strings == null) return null;
		if (strings.size() == 0) return "";
		StringBuffer result = new StringBuffer((String) strings.get(0));
		for (int i=1; i<strings.size(); i++) {
			result.append(" ");
			result.append((String) strings.get(i));
		}
		return result.toString();
	}

	/**
	 *  returns the pa head associated with a mention, or the text if
	 *  there is no pa
	 */

	static String getHead (AceEntityMention m) {
		Vector anns = doc.annotationsAt(m.jetHead.start(), "constit");
		if (anns != null) {
			// we search backwards so that names (which are added last)
			// will be found first
			for (int i=anns.size()-1; i>=0; i--) {
				Annotation ann = (Annotation) anns.get(i);
				String cat = (String) ann.get("cat");
				if (cat == "n" || cat == "pro" || cat == "name" ||
				    cat == "adj" || cat == "ven" ||
				    (cat == "det" && ann.get("tposs") == "t")) {
				    if (cat == "name") {
				    	String[] name = Resolve.getNameTokens(doc, ann);
				    	if (Ace.gazetteer.isCountry(name)) return "country";
				    	if (Ace.gazetteer.isNationality(name)) return "nationality";
				    }
					FeatureSet pa = (FeatureSet) ann.get("pa");
					if (pa != null) {
						String head = (String) pa.get("head");
						if (head != null) return head.replace(' ','-').replace('\n', '-');
					}
				}
			}
		}
		return doc.text(m.jetHead).trim().replace(' ','-').replace('\n', '-');
	}

	// ---- CONJUNCTION FUNCTIONS ----

	/**
	 *  finds all the conjuncts in Document doc, and builds the tables
	 *  conjunctf and conjunctb used by <CODE>getConjuncts</CODE>.
	 */

	static void findConjuncts (Document doc) {
		Vector constits = doc.annotationsOfType("constit");
		if (constits != null) {
			for (int j = 0; j < constits.size();  j++) {
				Annotation ann = (Annotation) constits.elementAt(j);
				Annotation conj = (Annotation) ann.get("conj");
				if (conj != null) {
					ArrayList conjuncts = new ArrayList();
					conjuncts.add(ann);
					while (conj != null) {
						conjuncts.add(conj);
						conj = (Annotation) conj.get("conj");
					}
					recordConjunct (conjuncts);
				}
			}
		}
	}

	static void recordConjunct (ArrayList conjuncts) {
		// System.out.println ("### recording conjuncts " + conjuncts);
		String type = "";
		AceEntityMention m;
		ArrayList mentions = new ArrayList();
		for (int i=0; i<conjuncts.size(); i++) {
			Annotation ann = (Annotation) conjuncts.get(i);
			m = mentionForAnnotation(ann);
			if (m == null) return;
			mentions.add(m);
			if (i == 0) {
				type = m.type;
			} else {
				if (!type.equals(m.type)) return;
			}
		}
		for (int i=0; i<mentions.size()-1; i++) {
			AceEntityMention m1 = (AceEntityMention) mentions.get(i);
			AceEntityMention m2 = (AceEntityMention) mentions.get(i+1);
			conjunctf.put(m1, m2);
			conjunctb.put(m2, m1);
			if (relationTrace)
				System.out.println ("Found conjuncts " +
					doc.text(m1.jetHead) + " and " + doc.text(m2.jetHead));
		}
		return;
	}

	static AceEntityMention mentionForAnnotation (Annotation a) {
		Annotation argHead = Resolve.getHeadC (a);
		Span span = argHead.span();
		int start = span.start();
		return (AceEntityMention) mentionStartMap.get(new Integer(start));
	}

	/**
	 *  returns a list of all the conjuncts of AceEntityMention <CODE>m</CODE>.
	 */

	static ArrayList getConjuncts (AceEntityMention m) {
		ArrayList a = new ArrayList();
		a.add(m);
		if (!expandConjuncts) return a;
		AceEntityMention n = m;
		while (conjunctf.get(n) != null) {
			n = (AceEntityMention) conjunctf.get(n);
			a.add(n);
		}
		n = m;
		while (conjunctb.get(n) != null) {
			n = (AceEntityMention) conjunctb.get(n);
			a.add(n);
		}
		return a;
	}

	static ArrayList recordCandidateWithConjuncts (AceEntityMention m1, AceEntityMention m2) {
		ArrayList relations = new ArrayList();
		if (m1.jetExtent.end() < m2.jetExtent.start() ||
		    m2.jetExtent.end() < m1.jetExtent.start()) {
			ArrayList a1 = getConjuncts (m1);
			ArrayList a2 = getConjuncts (m2);
			if (a1.contains(m2))
				return relations;
			for (int i=0; i<a1.size(); i++) {
				for (int j=0; j<a2.size(); j++) {
					AceEntityMention c1 = (AceEntityMention) a1.get(i);
					AceEntityMention c2 = (AceEntityMention) a2.get(j);
					RelationMention r = recordCandidate(c1, c2);
					relations.add(r);
				}
			}
		} else {
			RelationMention r = recordCandidate(m1, m2);
			relations.add(r);
		}
		return relations;
	}

	static void recordSyntacticLink (ArrayList r, String link) {
		for (int i=0; i<r.size(); i++) {
			RelationMention rm = (RelationMention) r.get(i);
			rm.syntacticLink = link;
		}
	}

	static void recordLinearLink (ArrayList r, ChunkPath chunkPath) {
		ArrayList chunks = chunkPath.getChunks();
		for (int i=0; i<r.size(); i++) {
			RelationMention rm = (RelationMention) r.get(i);
			// set link if current link is absent ("0") or longer
			if (rm.linearLink.size() > chunkPath.size() ||
			    (rm.linearLink.size() > 0 && rm.linearLink.get(0).equals("0"))) {
			    rm.linearLink = chunks;
			    // System.out.println ("+++ Setting linear link to " + link);
			    // System.out.println ("    in " + rm);
			}
		}
	}

	/**
	 *  reads the APF file from 'apfFile" and extracts the entity and relation
	 *  mentions.
	 */

	private static void readACErelations (String textFile, String apfFile) {
		AceDocument aceDoc = new AceDocument(textFile, apfFile);
		findEntityMentions (aceDoc);
		findRelationMentions (aceDoc);
	}

	/**
	 *  traverses APF document (apfDOC) and
	 *     creates Mention objects and places them in mentionSet
	 *     creates MentionStartMap mapping start of head to mention
	 *     creates mentionIDMap mapping mentionID to mention
	 */

	static void findEntityMentions (AceDocument aceDoc) {
		resetMentions ();
		ArrayList entities = aceDoc.entities;
		for (int i=0; i<entities.size(); i++) {
			AceEntity entity = (AceEntity) entities.get(i);
			String type = entity.type;
			String subtype = entity.subtype;
			ArrayList mentions = entity.mentions;
			for (int j=0; j<mentions.size(); j++) {
				AceEntityMention mention = (AceEntityMention) mentions.get(j);
				addMention (mention);
			}
		}
	}

	static void resetMentions () {
		mentionStartMap = new HashMap();
		mentionIDMap = new HashMap();
		mentionSet = new TreeSet<AceEntityMention>();
		allMentionSet = new TreeSet<AceEntityMention>();
	}

	static void addMention (AceEntityMention m) {
		if (!m.entity.generic) mentionSet.add(m);
		allMentionSet.add(m);
		mentionStartMap.put(new Integer(m.jetHead.start()), m);
		mentionIDMap.put(m.id, m);
	}

	/**
	 *  converts AceRelationMentions in 'aceDoc' to RelationMentions
	 *  and adds them to relMentionList.
	 *  IMPLICIT relations (used prior to 2004) are ignored.
	 */

	private static void findRelationMentions (AceDocument aceDoc) {
		relMentionList = new ArrayList<RelationMention>();
		ArrayList relations = aceDoc.relations;
		for (int i=0; i<relations.size(); i++) {
			AceRelation relation = (AceRelation) relations.get(i);
			String relationClass = relation.relClass;
			if (relationClass.equals("IMPLICIT")) continue;
			ArrayList relationMentions = relation.mentions;
			for (int j=0; j<relationMentions.size(); j++) {
				AceRelationMention relationMention = (AceRelationMention) relationMentions.get(j);
				// create new RelationMention with type & subtype
				RelationMention acerel = new RelationMention (relation.type, relation.subtype);
				acerel.setArg(1, relationMention.arg1);
				acerel.setArg(2, relationMention.arg2);
				relMentionList.add(acerel);
			}
		}
	}

	private static void recordPattern (String pattern) {
		if (!pattern.endsWith("--> 0")) System.out.println (">> " + pattern);
		Integer countI = patternSet.get(pattern);
		int count = (countI==null) ? 0 : countI.intValue();
		patternSet.put(pattern, new Integer(count+1));
		writer.println(pattern);
	}

	private static void reportPatterns () {
		Iterator it = patternSet.keySet().iterator();
		while (it.hasNext()) {
			String pattern = (String) it.next();
			int count = patternSet.get(pattern).intValue();
			if (count > 1)
				System.out.println (count + "X: " + pattern);
		}
	}

	/**
	 *  given a set of candidate RelationMentions (entity mentions which
	 *  are adjacent in the sentence or co-occur in some syntactic relation),
	 *  uses models to identify actual RelationMentions.
	 */

	private static void predictRelations () {
		relMentionList = new ArrayList<RelationMention>();
		for (int i=0; i<candidates.size(); i++) {
			RelationMention rm = (RelationMention) candidates.get(i);
			if (relationTrace)
				System.out.println ("For relation mention " + i + " = " + rm);
			if (rm.mention1.entity.generic || rm.mention2.entity.generic) {
				if (relationTrace)
					System.out.println (" mention is generic -- suppressed");
				continue;
			}
			RelationPattern match = Ace.eve.findMatch(rm, maxDistance);
			rm.confidence = Ace.eve.getMatchConfidence();
			String predictedType = "0", predictedSubtype = "";
			if (match != null) {
				if (relationTrace)
					System.out.println ("Best corpus pattern = " + match.string);
				predictedType = match.relationType;
				predictedSubtype = match.relationSubtype;
			}
			if (!predictedType.equals("0")) {
				if (predictedType.endsWith("-1")) {
					rm.swapArgs();
					predictedType = predictedType.substring(0,predictedType.length()-2);
				}
				rm.relationType = predictedType;
				rm.relationSubtype = predictedSubtype;
				relMentionList.add(rm);
				rm.id = relMentionList.size() + "";
			}
			if (relationTrace)
				System.out.println ("     Predicting ACE relation " + predictedType +
			                      " " + predictedSubtype);
		}
	}

	/**
	 *  if true, multiple relation mentions (with different types) between a single pair
	 *  of entities are merged (coref'ed) into a single relation, with the type
	 *  of the first mention.
	 */

	public static boolean mergeMultipleRelations = false;

	/**
	 *  takes the set of RelationMentions (in relMentionList) and combines them
	 *  into aceRelations (on relationList), and adds them to 'aceDoc'.
	 */

	private static void relationCoref (AceDocument aceDoc) {
		relationList = new ArrayList<AceRelation>();
		System.out.println ("RelationCoref: " + relMentionList.size() + " relation mentions");
	loop: for (int i=0; i<relMentionList.size(); i++) {
			RelationMention rm = relMentionList.get(i);
			String eid1 = rm.mention1.entity.id;
			String eid2 = rm.mention2.entity.id;
			for (AceRelation r : relationList) {
				if (eid1 == r.arg1.id && eid2 == r.arg2.id &&
				    (mergeMultipleRelations ||
				     (rm.relationType.equals(r.type) && rm.relationSubtype.equals(r.subtype)))) {
				  int mentionIndex = r.mentions.size() + 1;
					r.addMention(rm.toAce(r.id + "-" + mentionIndex, doc, aceDoc));
					continue loop;
				}
			}
			String relID = docName + "-R" + (relationList.size() + 1);
			AceRelation newr = new AceRelation (relID, rm.relationType, rm.relationSubtype, "EXPLICIT",
			  aceDoc.findEntity(eid1), aceDoc.findEntity(eid2));
			newr.addMention(rm.toAce(relID + "-1", doc, aceDoc));
			relationList.add(newr);
			aceDoc.addRelation(newr);
		}
		System.out.println ("RelationCoref: " + relationList.size() + " relations");
	}

}
