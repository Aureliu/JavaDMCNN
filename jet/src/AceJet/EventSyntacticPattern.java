// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkil
//              (ACE extensions)

package AceJet;

import java.util.*;
import Jet.Tipster.*;
import Jet.Parser.SyntacticRelation;
import Jet.Parser.SyntacticRelationSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventSyntacticPattern {

	final static Logger logger = LoggerFactory.getLogger(EventSyntacticPattern.class);

	/**
	 *  maximum length of path in syntax graph from anchor to an argument which
	 *  will be included in syntactic pattern.
	 */

	public static final int radius = 4;

	/**
	 *  builds a syntactic pattern connecting the anchor to the event arguments.
	 *  @param anchorStart   the position of the anchor
	 *  @param argumentPosn  the position of the arguments
	 *  @param relations     the syntactic relations for this document
	 *
	 *  todo:       set of {offset in document} of nodes to be processed
	 *  indexMap:   offset in document --> index in syntactic pattern
	 *                                     (+n for n-th argument,
	 *                                      -n for n-th variable node)
	 *  wordMap:    offset in document --> word (normalized form)
	 *  path:       offset in document --> set of syntactic relations which
	 *                                     reach this path from anchor
	 */

	static SyntacticRelationSet buildPattern (String patternType,
			int anchorStart, int argumentPosn[], SyntacticRelationSet relations) {
		Map<Integer,Integer> indexMap = new HashMap<Integer,Integer>();
		for (int i=0; i<argumentPosn.length; i++) {
			indexMap.put(argumentPosn[i], i);
		}
		Map wordMap = new HashMap();
		Map<Integer,HashSet> path = new HashMap<Integer,HashSet>();
		int argsToConnect = argumentPosn.length - 1;
		int variable = 0;
		LinkedList<Integer> todo = new LinkedList<Integer>();
		todo.add(anchorStart);
		path.put(anchorStart, new HashSet());
		while (todo.size() > 0 && argsToConnect > 0) {
			Integer from = todo.removeFirst();
			if (path.get(from).size() >= radius) continue;
			logger.trace ("from = " + from);
			int fromIndex = indexMap.get(from);
 			SyntacticRelationSet fromSet = relations.getRelationsFrom(from.intValue());
 			logger.trace ("fromSet = " + fromSet);
			for (int ifrom = 0; ifrom < fromSet.size(); ifrom++) {
				SyntacticRelation r = fromSet.get(ifrom);
				if (patternType == "SYNTAX" && isPaRelation(r)) continue;
				Integer to = new Integer(r.targetPosn);
				logger.trace ("to = " + to);
				// avoid loops
				if (path.get(to) == null) {
					todo.add(to);
					wordMap.put(to, r.targetWord);
					// if 'to' is an argument
					if (indexMap.get(to) != null && indexMap.get(to) >= 0) {
						argsToConnect--;
					} else {
					// 'to' is a non-argument node
						indexMap.put(to, --variable);
					}
				}
				SyntacticRelation rp = new SyntacticRelation
					(fromIndex, r.sourceWord, r.type, indexMap.get(to), r.targetWord);
				HashSet topath = new HashSet(path.get(from));
				topath.add(rp);
				logger.trace ("topath = " + topath);
				HashSet oldpath = path.get(to);
				if (oldpath == null ||
				    (patternType == "PA" && topath.size() <= oldpath.size() &&
				     paRelationCount(topath) > paRelationCount(oldpath)))
					path.put(to, topath);
			}
		}
		// build pattern from union of paths to all arguments
		SyntacticRelationSet pattern = new SyntacticRelationSet();
		for (int i=0; i<argumentPosn.length; i++) {
			HashSet h = path.get(argumentPosn[i]);
			if (h != null) pattern.addAll(h);
		}
		return pattern;
	}

	static boolean isPaRelation (SyntacticRelation r) {
		return r.type.startsWith("ARG");
	}

	static int paRelationCount (HashSet<SyntacticRelation> path) {
		int count = 0;
		for (SyntacticRelation r : path)
			if (isPaRelation(r)) count++;
		return count;
	}

	/**
	 *  returns the syntactic path from the anchor to an argument.
	 */

	public static String buildSyntacticPath
			(int anchorStart, int argumentPosn, SyntacticRelationSet relations) {
		Map<Integer, String> path = new HashMap<Integer, String>();
		int variable = 0;
		LinkedList<Integer> todo = new LinkedList<Integer>();
		todo.add(new Integer(anchorStart));
		path.put(new Integer(anchorStart), "");
		while (todo.size() > 0) {
			Integer from = todo.removeFirst();
			logger.trace ("from = " + from);
 			SyntacticRelationSet fromSet = relations.getRelationsFrom(from.intValue());
 			logger.trace ("fromSet = " + fromSet);
			for (int ifrom = 0; ifrom < fromSet.size(); ifrom++) {
				SyntacticRelation r = fromSet.get(ifrom);
				Integer to = new Integer(r.targetPosn);
				// avoid loops
				if (path.get(to) != null) continue;
				logger.trace ("to = " + to);
				// if 'to' is target
				if (to.intValue() == argumentPosn) {
					logger.trace ("TO is an argument");
					return (path.get(from) + ":" + r.type).substring(1);
				} else {
				// 'to' is another node
					path.put(to, path.get(from) + ":" + r.type + ":" + r.targetWord);
				}
				todo.add(to);
			}
		}
		return null;
	}

    /**
     *
     *  returns the syntactic path from the anchor to an argument. path is not allowed if
     *  one of localMentions is on the path (but not at the beginning or end of the path)
     */
    public static String buildSyntacticPath
    (int anchorStart, int argumentPosn, SyntacticRelationSet relations, List<AceEntityMention> localMentions) {
        Map<Integer, String> path = new HashMap<Integer, String>();
        int variable = 0;
        LinkedList<Integer> todo = new LinkedList<Integer>();
        todo.add(new Integer(anchorStart));
        path.put(new Integer(anchorStart), "");
        while (todo.size() > 0) {
            Integer from = todo.removeFirst();
            logger.trace ("from = " + from);
            SyntacticRelationSet fromSet = relations.getRelationsFrom(from.intValue());
            logger.trace ("fromSet = " + fromSet);
            for (int ifrom = 0; ifrom < fromSet.size(); ifrom++) {
                SyntacticRelation r = fromSet.get(ifrom);
                Integer to = new Integer(r.targetPosn);
                // avoid loops
                if (path.get(to) != null) continue;
                // disallow mentions
                if (to.intValue() != argumentPosn &&
                        matchMention(to, localMentions)) {
                    continue;
                }
                logger.trace ("to = " + to);
                 // if 'to' is target
                if (to.intValue() == argumentPosn) {
                    logger.trace ("TO is an argument");
                    return (path.get(from) + ":" + r.type).substring(1);
                } else {
                    // 'to' is another node
                    path.put(to, path.get(from) + ":" + r.type + ":" + r.targetWord);
                }
                todo.add(to);
            }
        }
        return null;
    }

	/**
	 *
	 *  returns the syntactic path from the anchor to an argument. path is not allowed if
	 *  one of localMentions is on the path (but not at the beginning or end of the path)
	 */
	public static String buildSyntacticPathOnSpans
	(int fromPosn, int toPosn, SyntacticRelationSet relations, List<Span> localSpans) {
		Map<Integer, String> path = new HashMap<Integer, String>();
		int variable = 0;
		LinkedList<Integer> todo = new LinkedList<Integer>();
		todo.add(new Integer(fromPosn));
		path.put(new Integer(fromPosn), "");
		while (todo.size() > 0) {
			Integer from = todo.removeFirst();
			logger.trace ("from = " + from);
			SyntacticRelationSet fromSet = relations.getRelationsFrom(from.intValue());
			logger.trace ("fromSet = " + fromSet);
			for (int ifrom = 0; ifrom < fromSet.size(); ifrom++) {
				SyntacticRelation r = fromSet.get(ifrom);
				Integer to = new Integer(r.targetPosn);
				// avoid loops
				if (path.get(to) != null) continue;
				// disallow mentions
				if (to.intValue() != toPosn &&
						matchSpan(to, localSpans)) {
					continue;
				}
				logger.trace ("to = " + to);
				// if 'to' is target
				if (to.intValue() == toPosn) {
					logger.trace ("TO is an argument");
					return (path.get(from) + ":" + r.type).substring(1);
				} else {
					// 'to' is another node
					path.put(to, path.get(from) + ":" + r.type + ":" + r.targetWord);
				}
				todo.add(to);
			}
		}
		return null;
	}

	private static boolean matchSpan(int posn, List<Span> spans) {
		for (Span span : spans) {
			if (span.start() == posn) {
				return true;
			}
		}
		return false;
	}

    private static boolean matchMention(int posn, List<AceEntityMention> mentions) {
        for (AceMention mention : mentions) {
            if (mention.jetExtent.start() == posn) {
                return true;
            }
        }
        return false;
    }


	/**
	 *  matches the syntactic pattern 'syntax' of this EventPattern against
	 *  the syntactic relations 'relations' of the current sentence.  If a
	 *  subgraph of 'syntax' rooted at the anchor matches a subgraph of
	 *  'relations', returns a list of the matching arguments (a list of
	 *  AceEventMentionArguments).
	 *  <p>
	 *  To keep track of the correspondence between the two graphs, uses
	 *
	 *   offsetMap:  index in pattern --> list of offsets in sentence
	 *               [normally a one-element list, but there may be more than
	 *                one for a conjoined structure]
	 *
	 *   toDo:  list of nodes in 'syntax' yet to be processed
	 *          (initialized to anchor node and then expanding outward in a
	 *           breadth-first search)
	 */


	static ArrayList match (EventPattern pattern, int anchorStart, Document doc,
			SyntacticRelationSet relations, AceDocument aceDoc) {
		pattern.syntaxMatchScore = 0;
		if (pattern.syntax == null || pattern.syntax.size() == 0) return null;
		ArrayList argumentValue = new ArrayList();
		HashMap<Integer,ArrayList<Integer>> offsetMap =
			new HashMap<Integer,ArrayList<Integer>>();
		// initialize correspondence map:  align anchors in 'syntax' and 'relations'
		addToMap (offsetMap, pattern.anchorPosn, anchorStart);
		// initialize todo -- start search at anchor
		LinkedList<Integer> todo = new LinkedList<Integer>();
		todo.add(pattern.anchorPosn);
		HashSet<AceMention> mentionsUsed = new HashSet<AceMention>();
		while (todo.size() > 0) {
			int from = todo.removeFirst();
			logger.trace ("from = " + from);
			ArrayList<Integer> fromOffsets = offsetMap.get(from);
			// get 'syntax' arcs starting from this node
			SyntacticRelationSet fromPatternSet = pattern.syntax.getRelationsFrom(from);
			logger.trace ("fromPatternSet = " + fromPatternSet);
			for (int i=0; i<fromOffsets.size(); i++) {
				// get corresponding node in sentence
				int fromOffset = fromOffsets.get(i);
				logger.trace ("fromOffset = " + fromOffset);
				// and arcs originating from that node
	 			SyntacticRelationSet fromSet = relations.getRelationsFrom(fromOffset);
	 			logger.trace ("fromSet = " + fromSet);
	 			// iterate over pairs of arcs
				for (int ifromPat = 0; ifromPat < fromPatternSet.size(); ifromPat++) {
					SyntacticRelation rp = fromPatternSet.get(ifromPat);
					// is there a relation in sentence with same type?
					for (int ifrom = 0; ifrom < fromSet.size(); ifrom++) {
						SyntacticRelation r = fromSet.get(ifrom);
						if (rp.type.equals(r.type)) {
							int to = rp.targetPosn;
							logger.trace ("to = " + to);
							int toOffset = r.targetPosn;
							addToMap (offsetMap, to, toOffset);
							if (to >= 0) {
								// argument
								AcePatternNode node = (AcePatternNode) pattern.nodes.get(to);
								int nodeScore = node.matchOnHead(toOffset, doc, aceDoc);
								if (nodeScore < AcePatternNode.MATCH_SCORE_TYPE_MATCH)
									break;
								// String role = roles[to];
								ArrayList roleSet = pattern.roles[to];
								String role0 = (String) roleSet.get(0);
								AceMention mention = node.getMatchedMention();
								if (!AceEventArgument.isValid(pattern.eventSubtype, role0, mention))
									break;
								if (mentionsUsed.contains(mention))
									break;
								pattern.syntaxMatchScore += nodeScore;
								// argumentValue[to] = mention;
								for (int irole=0; irole<roleSet.size(); irole++) {
									String role = (String) roleSet.get(irole);
									argumentValue.add(new AceEventMentionArgument (mention, role));
								}
								mentionsUsed.add(mention);
							} else {
								// variable node
								if (!r.targetWord.equals(rp.targetWord)) {
									break;
								}
							}
							if (!todo.contains(to)) {
								todo.add(to);
							}
						}
					}
				}
			}
		}
		return argumentValue;
	}

	static void addToMap (HashMap<Integer,ArrayList<Integer>> map, int index, int value) {
		ArrayList<Integer> y = map.get(index);
		if (y == null) y = new ArrayList<Integer>();
		if (!y.contains(value)) y.add(value);
		map.put(index, y);
	}

}
