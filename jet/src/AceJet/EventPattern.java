// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkil
//              (ACE extensions)

package AceJet;

import java.util.*;
import Jet.Tipster.*;
import Jet.Parser.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// for testing
import java.io.*;
import Jet.JetTest;
import Jet.Control;
import Jet.Refres.Resolve;

/**
 *  a pattern which matches a word sequence and produces an Ace Event.
 */

public class EventPattern {

	final static Logger logger = LoggerFactory.getLogger(EventPattern.class);

	/**
	 *  the type of pattern:  "CHUNK", "SYNTAX", or "PA".
	 */
	String patternType;

	// an ArrayList of AcePatternNode
	// (the node corresponding to the anchor is null)
	ArrayList nodes = new ArrayList();

	// the argument roles of node(i) ... each a List of Strings
	ArrayList[] roles;

	// (for CHUNK patterns)
	// the sequences of chunks between the nodes (length = nodes.length - 1)
	ArrayList<ChunkPath> paths = null;

	// the set of syntactic relations connecting the anchor
	// to the arguments
	SyntacticRelationSet syntax = null;

	// the position of the anchor (in nodes)
	int anchorPosn;

	// the text of the anchor (uninflected form)
	String anchor;

	// the type and subtype of event
	String eventType, eventSubtype;

	// an ArrayList of EventPatternArgument
	ArrayList eventArgs = new ArrayList();

	PatternEvaluation evaluation = new PatternEvaluation();

	/**
	 *  if true, use chunk as well as syntactic patterns
	 */

	static boolean useChunkPatterns = true;

	/**
	 *  create a new EventPattern based on <CODE>eventMention</CODE>.
	 *  @param  type           the type of pattern, "CHUNK", "SYNTAX", or "PA"
	 *  @param  doc            the document containing the event
	 *  @param  relations      the set of syntactic relations in the document
	 *  @param  event          the event for which is pattern is being created
	 *  @param  eventMention   the mention of the event to be used as a model
	 */

	EventPattern (String patternType, Document doc, SyntacticRelationSet relations,
		AceEvent event, AceEventMention eventMention) {
		this.patternType = patternType;
		if (patternType == "CHUNK") {
			paths = new ArrayList<ChunkPath>();
		} else if (patternType == "SYNTAX" || patternType == "PA") {
			syntax = new SyntacticRelationSet();
		} else {
			logger.error ("Invalid patternType " + patternType +
			                    " in EventPattern constructor.");
			return;
		}
		// 1. collect arguments = argument mentions (excluding place and time)
		ArrayList allArguments = eventMention.arguments;
		ArrayList arguments = new ArrayList(allArguments);
		// 1a. collect argument values into argValues
		//     and map from arguments to argValues into argValPtr
		ArrayList argVals = new ArrayList();
		int[] argValPtr = new int[arguments.size()];
		for (int i=0; i<arguments.size(); i++) {
			AceMention m = ((AceEventMentionArgument) arguments.get(i)).value;
			if (argVals.contains(m)) {
				argValPtr[i] = argVals.indexOf(m);
			} else {
				argValPtr[i] = argVals.size();
				argVals.add(m);
			}
		}
		// 2. put argVals in order by extent.start
		for (int i=0; i<argVals.size()-1; i++) {
			for (int j=i+1; j<argVals.size(); j++) {
				AceMention argi = (AceMention) argVals.get(i);
				AceMention argj = (AceMention) argVals.get(j);
				if (argi.jetExtent.start() > argj.jetExtent.start()) {
					argVals.set(i, argj);
					argVals.set(j, argi);
					for (int k=0; k<argValPtr.length; k++) {
						if (argValPtr[k] == i)
							argValPtr[k] = j;
						else if (argValPtr[k] == j)
							argValPtr[k] = i;
					}
				}
			}
		}
		// 3. get anchor
		Span anchorExtent = eventMention.anchorJetExtent;
		String anchorText = eventMention.anchorText;
		anchorPosn = -1;
		// 3a. determine anchor position and insert null into argument list
		for (int i=0; i<argVals.size(); i++) {
			AceMention argi = (AceMention) argVals.get(i);
			if (argi.getJetHead().start() > anchorExtent.start()) {
				anchorPosn = i;
				argVals.add(i, null);
				for (int j=0; j<argValPtr.length; j++) {
					if (argValPtr[j] >= anchorPosn) {
						argValPtr[j]++;
					}
				}
				break;
			}
		}
		if (anchorPosn < 0) {
			anchorPosn = argVals.size();
			argVals.add(null);
		}
		// 3b. determine normalized form of anchor
		anchor = normalizedAnchor (anchorExtent, anchorText, doc, relations);
		int toHead = 0;
		AceMention mention = null, lastMention = null;
		// 4. iterate over argument mentions, building PatternNodes and
		//    (for CHUNK patterns) ChunkPaths
		int[] argumentPosn = new int[argVals.size()];
		for (int iarg=0; iarg<argVals.size(); iarg++) {
			if (iarg == anchorPosn) {
				mention = new AceEventAnchor (eventMention.anchorExtent,
		  	  eventMention.anchorJetExtent, eventMention.anchorText, doc);
		  	argumentPosn[iarg] = anchorExtent.start();
				nodes.add(null);
			} else {
				//    5a. create node for mention
				mention = (AceMention)argVals.get(iarg);
				toHead = mention.getJetHead().start();
				argumentPosn[iarg] = toHead;
				nodes.add(new AcePatternNode(mention));
			}
			//    5b. create path preceding this node
			if (iarg > 0 && patternType == "CHUNK") {
				ChunkPath p = new ChunkPath (doc, lastMention, mention);
				if (p == null) {
					logger.warn ("EventPattern:  unable to build chunk path.");
				}
				paths.add(p);
			}
			lastMention = mention;
		}
		// 5. (for SYNAX and PA patterns) build syntactic pattern
		if (patternType == "SYNTAX" || patternType == "PA")
			syntax = EventSyntacticPattern.buildPattern (patternType,
				anchorExtent.start(), argumentPosn, relations);
		// 6. set event type and subtype
		eventType = event.type;
		eventSubtype = event.subtype;
		// 7. build eventArgs
		roles = new ArrayList[nodes.size()];
		for (int i=0; i<roles.length; i++)
			roles[i] = new ArrayList();
		for (int iarg=0; iarg<arguments.size(); iarg++) {
			//    create node for mention
			int iArgVal = argValPtr[iarg];
			AceEventMentionArgument a = (AceEventMentionArgument)arguments.get(iarg);
			String role = a.role;
			eventArgs.add(new EventPatternArgument(role, new Integer(iArgVal+1)));
			roles[iArgVal].add(role);
		}
	}

	/**
	 *  create a shallow copy of EventPattern ep.
	 */

	public EventPattern (EventPattern ep) {
		patternType = ep.patternType;
		nodes = ep.nodes;
		roles = ep.roles;
		paths = ep.paths;
		syntax = ep.syntax;
		anchorPosn = ep.anchorPosn;
		anchor = ep.anchor;
		eventType = ep.eventType;
		eventSubtype = ep.eventSubtype;
		eventArgs = ep.eventArgs;
		evaluation = ep.evaluation;
	}

	/**
	 *  returns true if this is a pattern which can never match:  a syntax or
	 *  PA pattern with no syntactic relations.
	 */

	public boolean empty () {
		return (patternType == "SYNTAX" || patternType == "PA") &&
		       (syntax == null || syntax.size() == 0);
	}

	public boolean equals (Object o) {
		if (!(o instanceof EventPattern))
			return false;
		EventPattern p = (EventPattern) o;
		if (paths == null) {
			if (p.paths != null)
				return false;
		} else {
			if (!paths.equals(p.paths))
				return false;
		}
		if (syntax == null) {
			if (p.syntax != null)
				return false;
		} else {
			if (!syntax.equals(p.syntax))
				return false;
		}
		return nodes.equals(p.nodes) &&
		       anchorPosn == p.anchorPosn &&
		       anchor.equals(p.anchor) &&
		       eventType.equals(p.eventType) &&
		       eventSubtype.equals(p.eventSubtype) &&
		       eventArgs.equals(p.eventArgs);
	}

	public int hashCode () {
		return (anchor + nodes.size()).hashCode();
	}

	/**
	 *  returns the uninflected form of the anchor (or the first word of the
	 *  anchor) at span 'anchorSpan' of 'doc'.
	 */

	static String normalizedAnchor (Span anchorSpan, String anchorText, Document doc,
			SyntacticRelationSet relations) {
		int posn = anchorSpan.start();
		Vector constits = doc.annotationsAt (posn, "constit");
		if (constits != null) {
			for (int i=0; i<constits.size(); i++) {
				Annotation constit = (Annotation) constits.get(i);
				String cat = (String) constit.get("cat");
				if (cat == "n" || cat == "v" || cat == "tv" || cat == "ven" || cat == "ving" ||
				    cat == "adj") {
					return normalizedAnchor(constit, doc, relations);
				}
			}
		}
		return anchorText;
	}

	static String normalizedAnchor (Annotation constit, Document doc, SyntacticRelationSet relations) {
		String cat = (String) constit.get("cat");
		String sense = ""; // getSenseOfWordAt (constit.start(), relations);
		if (cat == "n")
			return SynFun.getHead(doc, constit).toLowerCase() + sense + "/n";
		else
			return SynFun.getHead(doc, constit).toLowerCase() + sense;
	}

	private static String getSenseOfWordAt (int posn, SyntacticRelationSet relations) {
		SyntacticRelationSet fromSet = relations.getRelationsFrom(posn);
		for (int i=0; i<fromSet.size(); i++) {
			SyntacticRelation r = fromSet.get(i);
			if (r.type.endsWith("-1")) continue;
			return r.sourceWordSense;
		}
		return "1";
	}

	public int matchScore = 0;
	private int chunkMatchScore = 0;
	int syntaxMatchScore = 0;

	/**
	 *  match an anchor and its context against the event patterns;  if the
	 *  match is successful, build and return an AceEvent.
	 */

	public AceEvent match (Span anchorExtent, String anchor, Document doc,
			SyntacticRelationSet relations, AceDocument aceDoc) {
		if (!this.anchor.equals(anchor))
			return null;
		ArrayList chunkArgumentValue =
		  chunkMatch (anchorExtent, doc, relations, aceDoc);
		ArrayList syntaxArgumentValue =
			EventSyntacticPattern.match (this, anchorExtent.start(), doc, relations, aceDoc);
		ArrayList argumentValue = null;
		if (chunkArgumentValue == null && syntaxArgumentValue == null)
				return null;
		// if (nonNullLength(chunkArgumentValue) >= nonNullLength(syntaxArgumentValue))
		if (chunkArgumentValue != null && chunkMatchScore >= syntaxMatchScore) {
			argumentValue = chunkArgumentValue;
			matchScore = chunkMatchScore;
		} else {
			argumentValue = syntaxArgumentValue;
			matchScore = syntaxMatchScore;
		}
		Span extent = computeExtent (anchorExtent, argumentValue);
		// build new event, with type and subtype
		AceEvent event = new AceEvent ("id", eventType, eventSubtype);
		// build event mention
		AceEventMention emention = new AceEventMention ("id", extent, anchorExtent, doc.text());
		// add mention to event
		event.addMention(emention);
		// add arguments to event and mention,
		// making sure no mention or entity is used twice for one argument slot
		for (int iarg=0; iarg < argumentValue.size(); iarg++) {
			AceEventMentionArgument marg = (AceEventMentionArgument) argumentValue.get(iarg);
			addEventMentionArgument (emention, marg.role, marg.value);
			AceEventArgumentValue entity = marg.value.getParent();
			addEventArgument (event, marg.role, entity);
		}
		// no longer need event pattern arguments
		return event;
	}

	private void addEventMentionArgument
	  (AceEventMention emention, String role, AceMention mention) {
  	ArrayList arglist = emention.arguments;
		for (int i=0; i<arglist.size(); i++) {
			AceEventMentionArgument arg = (AceEventMentionArgument) arglist.get(i);
			if (arg.role.equals(role) && arg.value.equals(mention))
				return;
		}
		emention.addArgument(new AceEventMentionArgument(mention, role));
	}

	private void addEventArgument
	  (AceEvent event, String role, AceEventArgumentValue entity) {
  	ArrayList arglist = event.arguments;
		for (int i=0; i<arglist.size(); i++) {
			AceEventArgument arg = (AceEventArgument) arglist.get(i);
			if (arg.role.equals(role) && arg.value.equals(entity))
				return;
		}
		event.addArgument(new AceEventArgument(entity, role));
	}

	public int getMatchScore () {
		return matchScore;
	}

	int MIN_MATCH_SCORE = 0;

	/**
	 *  matches the chunk pattern portion of this event pattern against
	 *  Document 'doc' anchored at 'anchorExtent'.  Returns a list of
	 *  AceEventMentionArguments (role/mention pairs).
	 */

	private ArrayList chunkMatch (Span anchorExtent, Document doc,
			SyntacticRelationSet relations, AceDocument aceDoc) {
		ArrayList argumentValue = new ArrayList();
		chunkMatchScore = 0;
		if (paths == null)
			return null;
		if (!useChunkPatterns)
			return null;
		// find sentence containing trigger
		Annotation sentence = containingSentence (doc, anchorExtent);
		if (sentence == null) {
			logger.warn ("** Cannot find sentence containing trigger");
			return null;
		}
		// collect mentions in sentence preceding and following trigger
		ArrayList mentions = aceDoc.getAllMentions();
		LinkedList mentionsBeforeTrigger = new LinkedList();
		LinkedList mentionsAfterTrigger = new LinkedList();
		for (int i=0; i<mentions.size(); i++) {
			AceMention m = (AceMention) mentions.get(i);
			if (m.getJetHead().within(sentence.span())) {
				if (m.getJetHead().start() < anchorExtent.start())
					mentionsBeforeTrigger.add(m);
				else
					mentionsAfterTrigger.add(m);
			}
		}
		// -- chunkPath match --
		int nodeScore = 0;
		AceEventAnchor anchorMention =
			// don't worry about getting ace extent quite right for anchor;  use
			// Jet extent for both
			new AceEventAnchor (anchorExtent, anchorExtent, doc.text(anchorExtent).trim(), doc);
		// match nodes preceding anchor ...
		AceMention lastM = anchorMention;
		for (int inode = anchorPosn-1; inode >= 0; inode--) {
			boolean matchedNode = false;
			ChunkPath path = paths.get(inode);
			AcePatternNode node = (AcePatternNode) nodes.get(inode);
			ArrayList roleSet = roles[inode];
			String role0 = (String) roleSet.get(0);
			while (!mentionsBeforeTrigger.isEmpty()) {
				AceMention m = (AceMention) mentionsBeforeTrigger.removeLast();
				if ((nodeScore = node.match(m)) > MIN_MATCH_SCORE &&
						AceEventArgument.isValid(eventSubtype, role0, m) &&
				    new ChunkPath (doc, m, lastM).equals(path)) {
					// match
					chunkMatchScore += nodeScore;
					// argumentValue[inode] = m;
					for (int irole=0; irole<roleSet.size(); irole++) {
						String role = (String) roleSet.get(irole);
						argumentValue.add(new AceEventMentionArgument (m, role));
					}
					lastM = m;
					matchedNode = true;
					break;
				}
			}
			// if we couldn't match this node, don't try for more
			if (!matchedNode) break;
		}
		// match nodes following anchor ...
		lastM = anchorMention;
		for (int inode = anchorPosn; inode < nodes.size() - 1; inode++) {
			boolean matchedNode = false;
			ChunkPath path = paths.get(inode);
			AcePatternNode node = (AcePatternNode) nodes.get(inode+1);
			ArrayList roleSet = roles[inode+1];
			String role0 = (String) roleSet.get(0);
			while (!mentionsAfterTrigger.isEmpty()) {
				AceMention m = (AceMention) mentionsAfterTrigger.removeFirst();
				if ((nodeScore = node.match(m)) > MIN_MATCH_SCORE &&
						AceEventArgument.isValid(eventSubtype, role0, m) &&
				    new ChunkPath (doc, lastM, m).equals(path)) {
					// match
					chunkMatchScore += nodeScore;
					// argumentValue[inode+1] = m;
					for (int irole=0; irole<roleSet.size(); irole++) {
						String role = (String) roleSet.get(irole);
						argumentValue.add(new AceEventMentionArgument (m, role));
					}
					lastM = m;
					matchedNode = true;
					break;
				}
			}
			// if we couldn't match this node, don't try for more
			if (!matchedNode) break;
		}
		return argumentValue;
	}

	private static int nonNullLength (Object[] ray) {
		if (ray == null) return -1;
		int count = 0;
		for (int i=0; i<ray.length; i++)
			if (ray[i] != null) count++;
		return count;
	}

	static Annotation containingSentence (Document doc, Span span) {
		Vector sentences = doc.annotationsOfType("sentence");
		Annotation sentence = null;
		for (int i=0; i<sentences.size(); i++) {
			Annotation s = (Annotation) sentences.get(i);
			if (span.within(s.span())) {
				sentence = s;
				break;
			}
		}
		return sentence;
	}

	/**
	 *  computes the extent of an event:  the span from the leftmost argument
	 *  (or anchor) to the rightmost argument (or anchor).
	 */

	private Span computeExtent (Span anchorExtent, ArrayList argumentValue) {
		int min = anchorExtent.start();
		int max = anchorExtent.end();
		if (argumentValue != null) {
			for (int i=0; i<argumentValue.size(); i++) {
				AceEventMentionArgument arg = (AceEventMentionArgument) argumentValue.get(i);
				AceMention mention = arg.value;
				min = Math.min (min, mention.jetExtent.start());
				max = Math.max (max, mention.jetExtent.end());
			}
		}
		return new Span(min, max);
	}

	/**
	 *  produce a readable, one-line representation of the entire pattern
	 */

	public String toString() {
		StringBuffer result = new StringBuffer();
		for (int i=0; i<nodes.size(); i++) {
			if (i == anchorPosn) {
				result.append ("$" + anchor + "$");
			} else {
				result.append(nodes.get(i));
			}
			result.append(" ");
			if (i < nodes.size() - 1) {
				result.append("[" + (paths==null ? "" : paths.get(i)) + "]");
				result.append(" ");
			}
		}
		if (syntax != null)
			result.append(" " + syntax);
		result.append(" --> " + eventType + ":" + eventSubtype);
		if (eventArgs.size() > 0) {
			result.append("( ");
			for (int j=0; j<eventArgs.size(); j++) {
				result.append(eventArgs.get(j) + " ");
			}
			result.append(")");
		}
		if (evaluation.successCount > 0 || evaluation.failureCount > 0)
			result.append("\n  " + evaluation);
		return result.toString();
	}

	/**
	 *  write the event pattern to <CODE>w</CODE> in a form which can easily
	 *  be reloaded
	 */

	public void write (PrintWriter pw) {
		pw.println ("$eventPattern");
		pw.println ("$type " + patternType);
		pw.println ("$nodes");
		for (int i=0; i<nodes.size(); i++) {
			AcePatternNode apn = (AcePatternNode) nodes.get(i);
			if (apn == null)
				pw.println ("anchor=" + anchor);
			else
				apn.write(pw);
		}
		if (paths != null) {
			pw.println ("$paths");
			for (int i=0; i<paths.size(); i++)
				paths.get(i).write(pw);
		}
		if (syntax != null) {
			pw.println ("$syntax");
			syntax.write(pw);
		}
		pw.println ("$event");
		pw.println (eventType + ":" + eventSubtype);
		for (int i=0; i<eventArgs.size(); i++)
			((EventPatternArgument) eventArgs.get(i)).write(pw);
		pw.println ("$eval");
		evaluation.write(pw);
		pw.println ("$endPattern");
	}

	/**
	 *  creates an EventPattern from a sequence of lines read from
	 *  <CODE>reader</CODE>, as written by {@link write}.
	 */

	public EventPattern (BufferedReader reader) throws IOException {
		String line = reader.readLine();
		while (line != null && !line.equals("$endPattern")) {
			if (line.startsWith("$type ")) {
				patternType = line.substring(6).trim();
				line = reader.readLine();
			} else if (line.equals("$nodes")) {
				line = reader.readLine();
				while (line != null && !line.startsWith("$")) {
					AcePatternNode apn;
					if (line.startsWith("anchor=")) {
						anchor = line.substring(7);
						anchorPosn = nodes.size();
						nodes.add(null);
					} else {
						apn = new AcePatternNode(line);
						// add PatternNode unless there was a constructor argument error
						if (apn.type != null)
							nodes.add(apn);
					}
					line = reader.readLine();
				}
				roles = new ArrayList[nodes.size()];
				for (int i=0; i<roles.length; i++)
					roles[i] = new ArrayList();
			} else if (line.equals("$paths")) {
				paths = new ArrayList<ChunkPath>();
				line = reader.readLine();
				while (line != null && !line.startsWith("$")) {
					ChunkPath cp = new ChunkPath(line);
					paths.add(cp);
					line = reader.readLine();
				}
			} else if (line.equals("$syntax")) {
				line = reader.readLine();
				syntax = new SyntacticRelationSet();
				while (line != null && !line.startsWith("$")) {
					SyntacticRelation sr = new SyntacticRelation(line);
					syntax.add(sr);
					line = reader.readLine();
				}
			} else if (line.equals("$event")) {
				line = reader.readLine();
				String fields[] = line.split(":");
				eventType = fields[0];
				eventSubtype = fields[1];
				line = reader.readLine();
				eventArgs = new ArrayList();
				while (line != null && !line.startsWith("$")) {
					EventPatternArgument epa = new EventPatternArgument(line);
					eventArgs.add(epa);
					roles[((Integer)epa.source).intValue()-1].add(epa.role);
					line = reader.readLine();
				}
			} else if (line.equals("$eval")) {
				evaluation = new PatternEvaluation(reader);
				line = reader.readLine();
			} else {
				logger.warn ("EventPattern:  invalid input line " + line);
				line = reader.readLine();
			}
		}
		if (paths != null && paths.size() == 0) paths = null;
	}

	static final String home =
	    "C:/Documents and Settings/Ralph Grishman/My Documents/";
	static final String ace =
			home + "Ace 05/V4/";
	static final String triplesDir =
			ace + "011306-fast-tuples/";    // new Charniak parser, fast mode
	static final String triplesSuffix =
			".sent.txt.ns-2005-fast-ace-n-tuple92";

	public static void main (String[] args) throws IOException {
		// JetTest.initializeFromConfig("props/ME ace.properties");
		JetTest.initializeFromConfig("props/ace use parses.properties");
		Ace.gazetteer = new Gazetteer();
		Ace.gazetteer.load("data/loc.dict");
		AceDocument.ace2005 = true;
	  // String xmlFile = ace + "nw/AFP_ENG_20030413.0098.apf.xml";
	  // String textFile = ace + "perfect parses/nw/AFP_ENG_20030413.0098.sgm";
	  // String xmlFile = ace + "nw/AFP_ENG_20030304.0250.apf.xml";
	  // String textFile = ace + "perfect parses/nw/AFP_ENG_20030304.0250.sgm";
	  String docId = "nw/XIN_ENG_20030423.0011";
	  String xmlFile = ace + "nw/XIN_ENG_20030423.0011.apf.xml";
	  String textFile = ace + "perfect-parses/nw/XIN_ENG_20030423.0011.sgm";
		ExternalDocument doc = new ExternalDocument("sgml", textFile);
		doc.setAllTags(true);
		doc.open();
		Resolve.trace = false;
		Control.processDocument (doc, null, false, 0);
		AceDocument aceDoc = new AceDocument(textFile, xmlFile);
		SyntacticRelationSet relations = new SyntacticRelationSet();
		// relations.addRelations(doc);
		relations.readRelations (triplesDir + docId + triplesSuffix);  // for Adam's triples
		ArrayList events = aceDoc.events;
		for (int i=0; i<events.size(); i++) {
			AceEvent event = (AceEvent) events.get(i);
			ArrayList mentions = event.mentions;
			for (int j=0; j<mentions.size(); j++) {
				// create event pattern
				AceEventMention m = (AceEventMention) mentions.get(j);
				System.out.println ("Processing mention " + m.id + " = " + m.text);
				EventPattern chunkEvPat = new EventPattern ("CHUNK", doc, relations, event, m);
				System.out.println ("chunkEvPat = " + chunkEvPat);
				// try event patterns
				// .. get anchor extent and text
				Span anchorExtent = m.anchorJetExtent;
				String anchor = normalizedAnchor (anchorExtent, m.anchorText, doc, relations);
				AceEvent builtEvent = chunkEvPat.match(anchorExtent, anchor, doc, relations, aceDoc);
				if (builtEvent == null)
					System.out.println ("**** match failed ****");
				else {
					System.out.println ("Original      event " + m);
					System.out.println ("Reconstructed " + builtEvent);
				}
				EventPattern synEvPat = new EventPattern ("SYNTAX", doc, relations, event, m);
				System.out.println ("synEvPat = " + synEvPat);
				// try event pattern
				// .. get anchor extent and text
				builtEvent = synEvPat.match(anchorExtent, anchor, doc, relations, aceDoc);
				if (builtEvent == null)
					System.out.println ("**** match failed ****");
				else {
					System.out.println ("Original      event " + m);
					System.out.println ("Reconstructed " + builtEvent);
				}
				EventPattern paEvPat = new EventPattern ("PA", doc, relations, event, m);
				System.out.println ("paEvPat = " + paEvPat);
				// try event pattern
				// .. get anchor extent and text
				builtEvent = paEvPat.match(anchorExtent, anchor, doc, relations, aceDoc);
				if (builtEvent == null)
					System.out.println ("**** match failed ****");
				else {
					System.out.println ("Original      event " + m);
					System.out.println ("Reconstructed " + builtEvent);
				}
			}
		}
	}
}
