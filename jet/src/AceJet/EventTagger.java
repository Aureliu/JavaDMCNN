// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2005, 2013
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkit
//              (ACE extensions)

package AceJet;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;
import Jet.Parser.*;
import Jet.Control;
import Jet.JetTest;
import Jet.Pat.Pat;
import Jet.Refres.Resolve;

import opennlp.maxent.*;
import opennlp.maxent.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  assigns ACE events to a Document, given the entities, times, and values.
 *  <p>
 *  This is a corpus-trained tagger which uses a dual-pronged approach to
 *  identifying events and their arguments.
 *  <p>
 *  One approach is based on creating a pattern from each event mention in
 *  the training corpus.  The pattern encompasses the event trigger and all 
 *  of its arguments, recording the connection between the trigger and the
 *  arguments either as a sequence of chunks or as a path in a syntax
 *  graph.  At decoding time, a partial match of this pattern against the
 *  input can trigger an event.
 *  <p>
 *  The other approach is based on a set of maximum entropy classifiers.  An
 *  argument classifier decides whether a given entity mention is an argument
 *  of a trigger, and a separate role classifier decides which role this
 *  argument takes. After all potential arguments have been classified, an
 *  event classifier decides whether, based on the arguments found, an
 *  event should be triggered.   
 */

public class EventTagger {

	final static Logger logger = LoggerFactory.getLogger(EventTagger.class);

	// parameters
	//  use statistical model to gather arguments
	static boolean useArgumentModel = true;
	//  events below this probability are dropped
	static double EVENT_PROBABILITY_THRESHOLD = 0.50;
	//  args below this prob are never added to an event, and not used
	//  in estimating event probability
	static double MIN_ARG_PROBABILITY = 0.10;
	//  args below this probability are dropped from the final event
	static double ARGUMENT_PROBABILITY_THRESHOLD = 0.10;
	// minimal confidence for an arg to be used for coref determination
	static double COREF_CONFIDENCE = 0.10;
	// minimal probability for merging two events
	static double COREF_THRESHOLD = 0.40;

	// mapping from an anchor to a list of EventPatterns
	TreeMap<String, List<EventPattern>> anchorMap;

	PrintStream eventFeatureWriter;
	// PrintStream evTypeFeatureWriter;
	PrintStream argFeatureWriter;
	PrintStream roleFeatureWriter;
	PrintStream corefFeatureWriter;

	static GISModel eventModel;
	// static GISModel evTypeModel;
	static GISModel argModel;
	static GISModel roleModel;
	static GISModel corefModel;

	// file extension for GLARF triples
	static String triplesSuffix;
	// if true, use predicate-argument roles from GLARF
	static boolean usePA = false;
	// useParser:  no longer used by EventTagger, but still set and used elsewhere
	static boolean useParser;
	//
	
	public EventTagger () {
		anchorMap = new TreeMap<String, List<EventPattern>>();
	}

	String[] patternTypeList = {"CHUNK", "SYNTAX", "PA"};

	/**
	 *  trains the tagger from document 'doc' and corresponding AceDocument
	 *  (APF file) aceDoc.
	 */

	public void acquirePatterns (Document doc, AceDocument aceDoc, String docId) {
		SyntacticRelationSet relations = new SyntacticRelationSet();
		if (DepParser.isInitialized()) {
			relations = DepParser.parseDocument(doc);
 		} else if (usePA) {
			relations.readRelations (glarfDir + docId + triplesSuffix);
		} else {
			relations.addRelations(doc);
		}
		ArrayList events = aceDoc.events;
		for (int i=0; i<events.size(); i++) {
			AceEvent event = (AceEvent) events.get(i);
			ArrayList mentions = event.mentions;
			for (int j=0; j<mentions.size(); j++) {
				AceEventMention m = (AceEventMention) mentions.get(j);
				System.out.println ("\nProcessing event mention " + m.id + " = " + m.text);
				// .. get anchor extent and text
				Span anchorExtent = m.anchorJetExtent;
				String anchor =
					EventPattern.normalizedAnchor (anchorExtent, m.anchorText, doc, relations);
				// generate patterns
				for (String patternType : patternTypeList) {
					EventPattern ep = new EventPattern (patternType, doc, relations, event, m);
					if (ep.empty()) continue;
					System.out.println (patternType + " pattern = " + ep);
					addPattern (anchor, ep);
					// try event pattern
					AceEvent builtEvent = ep.match(anchorExtent, anchor, doc, relations, aceDoc);
					if (builtEvent == null)
						System.err.println ("**** match failed ****");
					// else
					 	// System.out.println ("Reconstructed event = " + builtEvent);
					// prepare training data for argument classifier
				}
				trainArgClassifier (event, m, doc, aceDoc, relations);
			}
		}
	}

	private void addPattern (String anchor, EventPattern ep) {
		List<EventPattern> patternList = anchorMap.get(anchor);
		if (patternList == null) {
			patternList = new ArrayList<EventPattern>();
			anchorMap.put(anchor, patternList);
		}
		if (!patternList.contains(ep))
			patternList.add(ep);
		// --
		String relatedForm = (String) SyntacticRelationSet.nomVmap.get(anchor);
		if (relatedForm != null) {
			EventPattern epClone = new EventPattern(ep);
			epClone.anchor = relatedForm;
			epClone.paths = null;
			patternList = anchorMap.get(relatedForm);
			if (patternList == null) {
				patternList = new ArrayList<EventPattern>();
				anchorMap.put(relatedForm, patternList);
			}
			if (!patternList.contains(epClone))
				patternList.add(epClone);
		}
		// ---
	}

	private void addBasicPattern (String anchor, EventPattern ep) {
		List<EventPattern> patternList = anchorMap.get(anchor);
		if (patternList == null) {
			patternList = new ArrayList<EventPattern>();
			anchorMap.put(anchor, patternList);
		}
		if (!patternList.contains(ep))
			patternList.add(ep);
	}

	/**
	 *  trains two statistical models for event arguments:
	 *  - argModel to decide whether a mention is an argument of an event
	 *  - roleModel to decide which role the mention should have
	 */

	private void trainArgClassifier (AceEvent event, AceEventMention eventMention,
			Document doc, AceDocument aceDoc, SyntacticRelationSet relations) {
		// get anchor
		Span anchorExtent = eventMention.anchorJetExtent;
		String anchorText = eventMention.anchorText;
		String anchor = EventPattern.normalizedAnchor (anchorExtent, anchorText, doc, relations);
		AceMention anchorMention =
			new AceEventAnchor (anchorExtent, anchorExtent, anchorText, doc);
		// find sentence containing anchor
		Annotation sentence = findContainingSentence (doc, anchorExtent);
		if (sentence == null) return;
		Span sentenceSpan = sentence.span();
		// iterate over mentions in sentence
		ArrayList mentions = aceDoc.getAllMentions();
		for (int im=0; im<mentions.size(); im++) {
			AceMention mention = (AceMention) mentions.get(im);
			if (!mention.jetExtent.within(sentenceSpan)) continue;
		// - compute syntactic path
		// - determine if mention has role in event
			ArrayList arguments = eventMention.arguments;
		  	String role = "noArg";
		  	for (int ia=0; ia<arguments.size(); ia++) {
		  		AceEventMentionArgument argument = (AceEventMentionArgument) arguments.get(ia);
		  		if (argument.value.equals(mention)) {
		  			role = argument.role;
		  			break;
		  		}
		  	}
		  	Datum d = argumentFeatures (doc, anchor, event, mention, anchorMention, relations);
			//   outcome = argument role
			if (role == "noArg") {
				d.setOutcome("noArg");
				argFeatureWriter.println(d.toString());
			} else {
				d.setOutcome("arg");
				argFeatureWriter.println(d.toString());
				d.setOutcome (role);
				roleFeatureWriter.println(d.toString());
			}
		}
	}

	private Datum argumentFeatures
	  (Document doc, String anchor, AceEvent event, AceMention mention,
	   AceMention anchorMention, SyntacticRelationSet relations) {
  	String direction;
		ChunkPath cpath;
		// - compute chunk path from anchor
		if (anchorMention.getJetHead().start() < mention.getJetHead().start()) {
			direction = "follow";
			cpath = new ChunkPath (doc, anchorMention, mention);
		} else {
			direction = "precede";
			cpath = new ChunkPath (doc, mention, anchorMention);
		}
		String spath = EventSyntacticPattern.buildSyntacticPath
		  (anchorMention.getJetHead().start(), mention.getJetHead().start(), relations);
		// System.out.println ("spath = " + spath);
		//   build feature entry
		Datum d = new Datum();
		//   = anchor word
		d.addFV ("anchor", anchor);
		//   = event type
		d.addFV ("evType", event.type);
		//   = EDT type of mention
		d.addFV ("menType", mention.getType());
		//   = head of mention (by itself and coupled with event subtype)
		//     (intuition:  US attacks Iraq, bombers kill, etc.)
		String headText = Resolve.normalizeName(mention.getHeadText()).replace(' ','_');
		d.addFV ("arg", headText);
		d.addFV ("evTypeArg", event.subtype + ":" + headText);
		//   = word preceding mention
		int pos = mention.jetExtent.start();
		Annotation token = doc.tokenEndingAt(pos);
		if (token != null) {
			d.addFV ("prevToken", doc.text(token).trim());
			d.addFV ("prevTokenAndType", event.type + "_" + doc.text(token).trim());
		}
		//   = chunk path, direction, distance
		if (cpath == null || cpath.toString() == null)
			d.addFV ("noChunkPath", null);
		else {
			String cpathString = cpath.toString().replace(' ','_');
			d.addFV ("chunkPath", direction + "_" + cpathString);
			d.addFV ("chunkPathAndType", event.type + "_" + direction + "_" + cpathString);
			d.addFV ("dist", Integer.toString(cpath.size()));
		}
		//   = syntactic path
		if (spath == null)
			d.addF ("noSynPath");
		else {
			d.addFV ("synPath", spath);
			d.addFV ("synPathEvType", event.type + "_" + spath);
			d.addFV ("synPathTypes", event.type + "_" + mention.getType() + "_" + spath);
		}
		return d;
	}

	private Annotation findContainingSentence (Document doc, Span span) {
		Vector sentences = doc.annotationsOfType ("sentence");
		if (sentences == null) {
			System.err.println ("findContainingSentence:  no sentences found");
			return null;
		}
		Annotation sentence = null;
		for (int i=0; i<sentences.size(); i++) {
			Annotation s = (Annotation) sentences.get(i);
			if (span.within(s.span())) {
				sentence = s;
				break;
			}
		}
		if (sentence == null) {
			System.err.println ("findContainingSentence:  can't find sentence with span");
			return null;
		}
		return sentence;
	}

	static GISModel loadClassifierModel (String modelFileName) {
		try {
			File f = new File(modelFileName);
			GISModel m = (GISModel) new SuffixSensitiveGISModelReader(f).getModel();
			System.err.println ("GIS model " + f.getName() + " loaded.");
		  return m;
		} catch (Exception e) {
		  e.printStackTrace();
		  System.exit(0);
		  return null; // required by compiler
		}
	}

	/**
	 *  add arguments to 'event' using classifiers to decide if a mention is
	 *  an argument, and to assign that argument a role.
	 *  <p>
	 *  For each mention in the sentence, determine the probability 'P' 
	 *  that it is an argument of this event and determine its most likely role.  
	 *  If P < MIN_ARG_PROBABILITY, ignore this mention.  If the most likely
	 *  role was filled by pattern matching or is not a valid role for this
	 *  event, ignore this mention.    If there are several mentions which have 
	 *  the same 'most likely role', select the one for which P is highest, 
	 *  and ignore the other mentions.  Add the remaining mentions as arguments
	 *  of the event, subject to the constraint that an entity may only
	 *  appear once as the argument of an event (so that, in particular,
	 *  entities already assigned as arguments by the pattern matcher will
	 *  be skipped here).
	 */

	private void collectArguments (AceEvent event, AceEventMention eventMention,
			Document doc, AceDocument aceDoc, SyntacticRelationSet relations) {
		// get anchor
		Span anchorExtent = eventMention.anchorJetExtent;
		String anchorText = eventMention.anchorText;
		String anchor = EventPattern.normalizedAnchor (anchorExtent, anchorText, doc, relations);
		AceMention anchorMention = new AceEventAnchor (anchorExtent,
		  	  eventMention.anchorJetExtent, anchorText, doc);
		// identify roles already filled
		Set rolesFilled = rolesFilledInEvent(event);
		// find sentence containing anchor
		Annotation sentence = findContainingSentence (doc, anchorExtent);
		if (sentence == null) return;
		Span sentenceSpan = sentence.span();
		// get arguments already used
		HashSet<AceEventArgumentValue> argumentsUsed = argumentValues(event);
		// iterate over mentions in sentence
		Map<String, Double> bestRoleProb = new HashMap<String, Double>();
		Map<String, AceMention> bestRoleFiller = new HashMap<String, AceMention>();
		Map<String, Double> bestRoleRoleProb = new HashMap<String, Double>();
		ArrayList mentions = aceDoc.getAllMentions();
		for (int im=0; im<mentions.size(); im++) {
			AceMention mention = (AceMention) mentions.get(im);
			if (!mention.jetExtent.within(sentenceSpan)) continue;
			if (mention.getJetHead().within(anchorExtent)) continue; // Nov. 4 2005
			//   build feature entry
			Datum d = argumentFeatures (doc, anchor, event, mention, anchorMention, relations);
			//   classify:
			//      probability that this is an argument
			//      most likely role assignment
			double argProb = argModel.eval(d.toArray())[argModel.getIndex("arg")];
			String role = roleModel.getBestOutcome(roleModel.eval(d.toArray())).intern();
			double roleProb = roleModel.eval(d.toArray())[roleModel.getIndex(role)];
			// System.out.println ("argProb of " + mention.getHeadText() + " is " + argProb);
			/*  the following code chooses the best valid role
			double[]  roleProbs = roleModel.eval(d.toArray());
			String role = null;
			double best = -1;
			for (int i=0; i<roleModel.getNumOutcomes(); i++) {
				String r = roleModel.getOutcome(i);
				if (roleProbs[i] > best
				    && AceEventArgument.isValid(event.subtype, r, mention)) {
					role = r;
					best = roleProbs[i];
				}
			}
			if (role == null) continue; */
			//      if role already filled, continue
			if (rolesFilled.contains(role)) continue;
			//      if not a valid role for this event type, continue
			if (!AceEventArgument.isValid(event.subtype, role, mention)) continue;
			//      if likely argument, add to event
			if (argProb > MIN_ARG_PROBABILITY) {
				// if this mention has the highest probability of filling this role,
				//    record it
				if (bestRoleProb.get(role) == null ||
				    argProb > bestRoleProb.get(role).doubleValue()) {
					bestRoleProb.put(role, argProb);
					bestRoleRoleProb.put(role, roleProb);
					bestRoleFiller.put(role, mention);
				}
			}
		}
		for (String role : bestRoleFiller.keySet()) {
			AceMention mention = (AceMention) bestRoleFiller.get(role);
			// don't use an argument twice
			AceEventArgumentValue argValue = mention.getParent();
			if (argumentsUsed.contains(argValue)) continue;
			double argProb = bestRoleProb.get(role);
			AceEventMentionArgument mentionArg =
				new AceEventMentionArgument(mention, role);
			mentionArg.confidence = argProb;
			mentionArg.roleConfidence = bestRoleRoleProb.get(role);
			eventMention.arguments.add(mentionArg);
			AceEventArgument eventArg = new AceEventArgument(argValue, role);
			eventArg.confidence = argProb;
			event.arguments.add(eventArg);
			// System.out.println ("Adding " + mention.getHeadText() + " in role " +
			//                      role + " with prob " + argProb);
			argumentsUsed.add(argValue);
		}
	}

	private static boolean evalTrace = false;
	private static int eventWeight = 100;

	/**
	 *  applies the learned patterns to Document 'doc' and records the
	 *  number of times it produced a correct or incorrect event.
	 */

	public void evaluatePatterns (Document doc, AceDocument aceDoc, String docId) {
		SyntacticRelationSet relations = new SyntacticRelationSet();
		if (DepParser.isInitialized()) {
			relations = DepParser.parseDocument(doc);
		} else if (usePA) {
			relations.readRelations (glarfDir + docId + triplesSuffix);
		} else {
			relations.addRelations(doc);
		}
		ArrayList events = aceDoc.events;
		Vector constituents = doc.annotationsOfType("constit");
		for (int i=0; i<constituents.size(); i++) {
			Annotation constit = (Annotation) constituents.get(i);
			String cat = (String) constit.get("cat");
			if (cat == "n" || cat == "v" || cat == "tv" || cat == "ven" ||
				  cat == "ving" || cat == "adj") {
				String anchor = EventPattern.normalizedAnchor (constit, doc, relations);
				Span anchorExtent = constit.span();
				List patterns = (List) anchorMap.get(anchor);
				if (patterns != null) {
					String eventType = null;
					// record success / failure for individual patterns
					for (int j=0; j<patterns.size(); j++) {
						EventPattern ep = (EventPattern) patterns.get(j);
						AceEvent event = ep.match(anchorExtent, anchor, doc, relations, aceDoc);
						if (event != null) {
							if (evalTrace) System.out.println ("Evaluating " + ep);
							if (evalTrace) System.out.println ("  for matched event " + event);
							AceEventMention mention = (AceEventMention) event.mentions.get(0);
							if (evalTrace) System.out.println ("  with extent " + doc.text(mention.jetExtent));
							AceEvent keyEvent = correctEvent(anchorExtent, event, events);
							if (keyEvent != null) {
								// if event is correct, count correct and spurious arguments
								ArrayList<AceEventMentionArgument> arguments = mention.arguments;
								ArrayList<AceEventMentionArgument> keyArguments = correctEventMention.arguments;
								ArrayList<AceEventMentionArgument> correctArguments = 
									new ArrayList<AceEventMentionArgument>(arguments);
								correctArguments.retainAll(keyArguments);
								ArrayList<AceEventMentionArgument> spuriousArguments = 
									new ArrayList<AceEventMentionArgument>(arguments);
								spuriousArguments.removeAll(keyArguments);
								int successCount = eventWeight + correctArguments.size();
								int failureCount = spuriousArguments.size();
								// ---
								ep.evaluation.recordSuccess(mention.arguments, successCount);
								ep.evaluation.recordFailure(mention.arguments, failureCount);
								if (evalTrace) System.out.println ("    a success");
								eventType = event.type + ":" + event.subtype;
							} else {
								// if event is incorrect, count all arguments as spurious
								int failureCount = eventWeight + mention.arguments.size();
								ep.evaluation.recordFailure(mention.arguments, failureCount);
								if (evalTrace) System.out.println ("    a failure");
							}
						}
					}
				}
			}
		}
	}

	AceEventMention correctEventMention = null;

	/**
	 *  if 'event', triggered by the text in 'anchorExtent'
	 *  (a Jet span), matches one of the events in 'keyEvents', returns
	 *  the event (in keyEvents), else null.
	 */

	private AceEvent correctEvent (Span anchorExtent, AceEvent event, ArrayList keyEvents) {
		AceEventMention mention = (AceEventMention) event.mentions.get(0);
		for (int i=0; i<keyEvents.size(); i++) {
			AceEvent keyEvent = (AceEvent) keyEvents.get(i);
			ArrayList keyMentions = keyEvent.mentions;
			for (int j=0; j<keyMentions.size(); j++) {
				AceEventMention keyMention = (AceEventMention) keyMentions.get(j);
				Span keyExtent = keyMention.jetExtent;
				if (anchorExtent.within(keyExtent) &&
				    event.type.equals(keyEvent.type) &&
				    event.subtype.equals(keyEvent.subtype)) {
				  correctEventMention = keyMention;
					return keyEvent;
				}
			}
		}
		return null;
	}

	void trainEventModel (Document doc, AceDocument aceDoc, String docId) {
		SyntacticRelationSet relations = new SyntacticRelationSet();
		if (DepParser.isInitialized()) {
			relations = DepParser.parseDocument(doc);
		} else if (usePA) {
			relations.readRelations (glarfDir + docId + triplesSuffix);
		} else {
			relations.addRelations(doc);
		}
		ArrayList events = aceDoc.events;
		// iterate over potential anchors
		Vector constituents = doc.annotationsOfType("constit");
		for (int i=0; i<constituents.size(); i++) {
			Annotation constit = (Annotation) constituents.get(i);
			String cat = (String) constit.get("cat");
			if (cat == "n" || cat == "v" || cat == "tv" || cat == "ven" ||
				  cat == "ving" || cat == "adj") {
				String anchor = EventPattern.normalizedAnchor (constit, doc, relations);
				Span anchorExtent = constit.span();
				List patterns = (List) anchorMap.get(anchor);
				if (patterns == null) continue;
				CONFIDENT_ARG = 0.10;
				AceEvent event = eventAnchoredByConstituent
				  (constit, doc, aceDoc, docId, relations, 0);
				// if no event (success count too low), skip for now
				if (event == null) continue;
				EventPattern pattern = (EventPattern) patternMatched;
				// outcome:
				//    is this the anchor of a true event (even if of different type)
				Datum d = eventFeatures (doc, anchor, constit, event, pattern);
				boolean isEvent = correctEvent(anchorExtent, event, events) != null;
				d.setOutcome(isEvent ? "event" : "noEvent");
				eventFeatureWriter.println(d.toString());
			}
		}
	}

	/**
	 *  the features for deciding whether an event is reportable are
	 *  - the anchor
	 *  - the fraction of times the anchor is reportable
	 *  - the probability that it has each argument
	 */

	private Datum eventFeatures (Document doc, String anchor, Annotation anchorAnn, AceEvent event, EventPattern pattern) {
		Datum d = new Datum();
		d.addF (anchor);
		int patmatch = pattern.evaluation.test(event.arguments, 1.00);
		if (patmatch > 0)
			d.addFV ("patmatch", Integer.toString(patmatch / 10));
		else
			d.addFV ("patmatch", "N");
		return d;
	}

	void trainCorefModel (Document doc, AceDocument aceDoc, String docId) {
		SyntacticRelationSet relations = new SyntacticRelationSet();
		if (DepParser.isInitialized()) {
			relations = DepParser.parseDocument(doc);
		} else if (usePA) {
			relations.readRelations (glarfDir + docId + triplesSuffix);
		} else {
			relations.addRelations(doc);
		}
		// event list from key
		ArrayList<AceEvent> events = aceDoc.events;
		// system-generated event list
		ArrayList<AceEvent> systemEvents = new ArrayList<AceEvent>();
		//
		HashMap<String, Integer> keyIdToSystemEventMap = new HashMap<String, Integer>();
		int aceEventNo = 1;
		// iterate over potential anchors
		Vector constituents = doc.annotationsOfType("constit");
		for (int i=0; i<constituents.size(); i++) {
			Annotation constit = (Annotation) constituents.get(i);
			String cat = (String) constit.get("cat");
			if (cat == "n" || cat == "v" || cat == "tv" || cat == "ven" ||
				  cat == "ving" || cat == "adj") {
				String anchor = EventPattern.normalizedAnchor (constit, doc, relations);
				Span anchorExtent = constit.span();
				AceEvent event = eventAnchoredByConstituent
				  (constit, doc, aceDoc, docId, relations, aceEventNo);
				event = pruneEvent (event, constit, doc, relations);
				if (event != null) {
					AceEvent keyEvent = correctEvent(anchorExtent, event, events);
					// if not a correct event, continue
					if (keyEvent == null) continue;
					String keyEventId = keyEvent.id;
					// determine which system event it should be folded into, if any
					Integer I = (Integer) keyIdToSystemEventMap.get(keyEventId);
					int systemEventIndex = (I == null) ? -1 : I.intValue();
					// compare to all events in systemEvents
					for (int iEvent = 0; iEvent < systemEvents.size(); iEvent++) {
						AceEvent priorEvent = (AceEvent) systemEvents.get(iEvent);
						if (!priorEvent.subtype.equals(event.subtype)) continue;
						// if same type/subtype, generate training example
						// (with outcome = whether it belongs to this systemEvent)
						Datum d = corefFeatures (priorEvent, event, anchor);
						d.setOutcome((iEvent == systemEventIndex) ? "merge" : "dontMerge");
						corefFeatureWriter.println(d.toString());
					}
					// if it should be folded in, do so; else create new event
					if (systemEventIndex >= 0) {
						// fold event into prior event
						AceEvent priorEvent = (AceEvent) systemEvents.get(systemEventIndex);
						priorEvent.arguments = mergeArguments (event.arguments, priorEvent.arguments);
						AceEventMention m = (AceEventMention) event.mentions.get(0);
						priorEvent.addMention(m);
						m.setId(priorEvent.id + "-" + priorEvent.mentions.size());
					} else {
						systemEvents.add(event);
						keyIdToSystemEventMap.put(keyEventId, new Integer(systemEvents.size()-1));
						aceEventNo++;
					}
				}
			}
		}
	}

	private Datum corefFeatures (AceEvent priorEvent, AceEvent event, String anchor) {
		Datum d = new Datum();
		// type/subtype of events
		d.addFV ("subtype", event.subtype);
		// normalized anchor
		d.addFV ("anchor", anchor);
		// nominal anchor
		if (anchor.endsWith("/n")) d.addF("nomAnchor");
		// distance (100's of chars, up to 10)
		AceEventMention lastMentionPriorEvent =
			(AceEventMention) priorEvent.mentions.get(priorEvent.mentions.size() -1);
		int posnPriorEvent = lastMentionPriorEvent.anchorExtent.start();
		AceEventMention lastMentionOfEvent =
			(AceEventMention) event.mentions.get(event.mentions.size() -1);
		int posnEvent = lastMentionOfEvent.anchorExtent.start();
		int distance = posnEvent - posnPriorEvent;
		d.addFV ("distance", Integer.toString(Math.min(distance /100, 9)));
		// matching anchors
		if (lastMentionOfEvent.anchorText.equals(lastMentionPriorEvent.anchorText))
			d.addF("anchorMatch");
		// overlapping and conflicting roles with confidence
		ArrayList priorArgs = priorEvent.arguments;
		ArrayList args = event.arguments;
		for (int i=0; i<priorArgs.size(); i++) {
			AceEventArgument arg1 = (AceEventArgument) priorArgs.get(i);
			if (arg1.confidence < COREF_CONFIDENCE) continue;
			String role1 = arg1.role;
			String id1 = arg1.value.id;
			for (int j=0; j<args.size(); j++) {
				AceEventArgument arg2 = (AceEventArgument) args.get(j);
				if (arg2.confidence < COREF_CONFIDENCE) continue;
				String role2 = arg2.role;
				String id2 = arg2.value.id;
				int confidence = (int) (Math.min(arg1.confidence, arg2.confidence) * 5);
				if (role1.equals(role2))
					if (id1.equals(id2))
						d.addFV ("overlap", role1 + ":" + confidence);
					else
						d.addFV ("conflict", role1 + ":" + confidence);
			}
		}
		// determiner of noun anchor
		return d;
	}

	// minimum confidence to consider arg a confident argument
	private static double CONFIDENT_ARG = 0.20;

	/**
	 *  identify ACE events in Document 'doc' and add them to 'aceDoc'.
	 */

	public void tag (Document doc, AceDocument aceDoc, String currentDocPath, String docId) {
		SyntacticRelationSet relations = new SyntacticRelationSet();
		if (DepParser.isInitialized()) {
			relations = DepParser.parseDocument(doc);
		} else if (usePA) {
			relations.readRelations (glarfDir + currentDocPath + triplesSuffix);
		} else {
			relations.addRelations(doc);
		}
		int aceEventNo = 1;
		Vector<Annotation> constituents = doc.annotationsOfType("constit");
		HashSet<Span> matchedAnchors = new HashSet<Span>();
		if (constituents != null) {
			for (Annotation constit : constituents) {
				String cat = (String) constit.get("cat");
				if (cat == "n" || cat == "v" || cat == "tv" || cat == "ven" ||
				    cat == "ving" || cat == "adj") {
				  Span anchorExtent = constit.span();
				  if (matchedAnchors.contains(anchorExtent)) continue; //<< added 13 Feb 06
					AceEvent event = eventAnchoredByConstituent
					  (constit, doc, aceDoc, docId, relations, aceEventNo);
					event = pruneEvent (event, constit, doc, relations);
					if (event != null) {
						aceDoc.addEvent (event);
						aceEventNo++;
						matchedAnchors.add(anchorExtent);
					}
				}
			}
		}
		eventCoref (aceDoc, doc, relations);
	}

	/**
	 *  if 'constit' is the anchor of an event, return the event.
	 */

	AceEvent eventAnchoredByConstituent (Annotation constit, Document doc, 
			AceDocument aceDoc, String docId, SyntacticRelationSet relations, int aceEventNo) {
		String anchor = EventPattern.normalizedAnchor (constit, doc, relations);
		Span anchorExtent = constit.span();
		// search patterns for match
		List patterns = (List) anchorMap.get(anchor);
		if (patterns == null)
			return null;
		AceEvent bestEvent =
			matchPatternSet (patterns, anchorExtent, anchor, doc, relations, aceDoc);
		if (bestEvent == null)
			return null;
		Annotation sentence = EventPattern.containingSentence(doc, anchorExtent);
		int slash = docId.lastIndexOf('/');
			if (slash >= 0)
				docId = docId.substring(slash + 1);
		String eventId = docId + "-EV" + aceEventNo;
		bestEvent.setId(eventId);
		AceEventMention bestMention = (AceEventMention) bestEvent.mentions.get(0);
		bestMention.setId(eventId + "-1");
		// collect additional arguments using statistical model
		if (useArgumentModel)
			collectArguments (bestEvent, bestMention, doc, aceDoc, relations);
		return bestEvent;
	}

	/**
	 *  applies maxent event model and removes doubtful events and event arguments.  
	 *  An event is removed if its probability (based on the event model) is below
	 *  EVENT_PROBABILITY_THRESHOLD and it does not have any arguments
	 *  assigned by patterns.  An argument is removed if (probability of event)
	 *  * (probability of argument) is below ARGUMENT_PROBABILITY_THRESHOLD.
	 */
	 
	AceEvent pruneEvent (AceEvent event, Annotation constit, Document doc,
			SyntacticRelationSet relations) {
		if (event == null)
			return null;
		String anchor = EventPattern.normalizedAnchor (constit, doc, relations);
		EventPattern pattern = (EventPattern) patternMatched;
		Datum d = eventFeatures (doc, anchor, constit, event, pattern);
		double eventProb = eventModel.eval(d.toArray())[eventModel.getIndex("event")];
		logger.trace ("event maxent model p = {}", eventProb);
		AceEventMention emention = (AceEventMention)event.mentions.get(0);
		emention.confidence = eventProb;
		if (eventProb < EVENT_PROBABILITY_THRESHOLD) {
			logger.trace ("probability below threshold, event rejected");
			return null;
		} else {
			logger.trace ("probability above threshold, event accepted");
		}
		ArrayList args = event.arguments;
		Iterator it = args.iterator();
		while (it.hasNext()) {
			AceEventArgument ea = (AceEventArgument) it.next();
			if (ea.confidence * eventProb < ARGUMENT_PROBABILITY_THRESHOLD) it.remove();
		}
		args = emention.arguments;
		it = args.iterator();
		while (it.hasNext()) {
			AceEventMentionArgument ea = (AceEventMentionArgument) it.next();
			if (ea.confidence * eventProb < ARGUMENT_PROBABILITY_THRESHOLD) it.remove();
		}
		return event;
	}

	EventPattern patternMatched;

	AceEvent matchPatternSet (List patterns, Span anchorExtent, String anchor,
		  Document doc, SyntacticRelationSet relations, AceDocument aceDoc) {
		patternMatched = null;
		AceEvent bestEvent = null;
		EventPattern bestPattern = null;
		int bestMatchScore = 0;
		for (int j=0; j<patterns.size(); j++) {
			EventPattern ep = (EventPattern) patterns.get(j);
			// if (ep.patternType.equals("PA")) continue; // <<< SYNTAX only
			// try event pattern
			AceEvent event = ep.match(anchorExtent, anchor, doc, relations, aceDoc);
			// if it matches
			if (event != null && ep.evaluation.test(event.arguments, 0.50) > 0) {
 			  	int score = ep.getMatchScore() + ep.evaluation.test(event.arguments, 0.50);
			  	if (ep.patternType != null && ep.patternType.equals("SYNTAX")) score += 50; //<<<<<<<< favor SYNTAX over PA
				if (score > bestMatchScore) {
					bestMatchScore = score;
					bestEvent = event;
					bestPattern = ep;
				}
			}
		}
		patternMatched = bestPattern;
		if (bestPattern != null) {
			logger.trace ("");
			logger.trace ("For anchor   = {}", anchor);
			Annotation sentence = findContainingSentence (doc, anchorExtent);
			if (sentence != null)
				logger.trace ("in {}",doc.normalizedText(sentence));
			logger.trace ("best pattern = {}", bestPattern);
			logger.trace ("best event = {}", bestEvent);
			logger.trace ("match score  = {}", bestMatchScore);
			logger.trace ("event generation score  = {}", bestPattern.evaluation.test(bestEvent.arguments, 0.50));
		}
		return bestEvent;
	}

	/**
	 *  returns the set of argument roles filled in 'event'.
	 */

	private Set<String> rolesFilledInEvent (AceEvent event) {
		ArrayList args = event.arguments;
		Set<String> roles = new HashSet<String>();
		for (int i=0; i<args.size(); i++) {
			AceEventArgument arg = (AceEventArgument) args.get(i);
			roles.add(arg.role);
		}
		return roles;
	}

	/**
	 *  returns a count of the number of arguments whose confidence
	 *  is above a threshold.
	 */

	private int confidentRoleCount (AceEvent event, double threshold) {
		ArrayList args = event.arguments;
		int count = 0;
		for (int i=0; i<args.size(); i++) {
			AceEventArgument arg = (AceEventArgument) args.get(i);
			if (arg.confidence > threshold) count++;
		}
		return count;
	}

	/**
	 *  returns a set of the values of the arguments of the event.
	 */

	private HashSet<AceEventArgumentValue> argumentValues (AceEvent event) {
		ArrayList args = event.arguments;
		HashSet<AceEventArgumentValue> values = new HashSet<AceEventArgumentValue>();
		for (int i=0; i<args.size(); i++) {
			AceEventArgument arg = (AceEventArgument) args.get(i);
			values.add(arg.value);
		}
		return values;
	}
	
	/**
	 *  add event information to the APF files of the documents on
	 *  <CODE>fileList</CODE>.
	 */

	public void tag (String fileList) throws IOException {
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		String currentDocPath;
		while ((currentDocPath = reader.readLine()) != null) {
			System.out.println ("\nProcessing file " + currentDocPath);
		  String textFile = docDir + currentDocPath + ".sgm";
			String xmlFile = docDir + currentDocPath + ".apf.xml";
			String outputFile = outputDir + currentDocPath + ".apf";
			ExternalDocument doc = new ExternalDocument("sgml", textFile);
			doc.setAllTags(true);
			doc.open();
			doc.stretchAll();
			Control.processDocument (doc, null, false, 0);
			AceDocument aceDoc = new AceDocument(textFile, xmlFile);
			aceDoc.events.clear();
			tag (doc, aceDoc, currentDocPath.replaceFirst(".sgm",""), aceDoc.docID);
			aceDoc.write(new PrintWriter (new FileWriter (outputFile)), doc);
		}
	}

	/**
	 *  performs coreference on the events in an Ace document.  On entry, the
	 *  AceDocument <CODE>aceDoc</CODE> should have a set of events each with a
	 *  single mention.  The event mentions which are believed to corefer are
	 *  combined into a single event.
	 */

	public void eventCoref (AceDocument aceDoc, Document doc, SyntacticRelationSet relations) {
		ArrayList<AceEvent> events = aceDoc.events;
		logger.info ("eventCoref:  {} event mentions", events.size());
		ArrayList<AceEvent> newEvents = new ArrayList<AceEvent>();
		nextevent:
		for (int i=0; i<events.size(); i++) {
			AceEvent event = (AceEvent) events.get(i);
			// is there a prior event on newEvents of the same type
			// such that the arguments are compatible?
			int priorEventIndex =  -1;
			double priorEventProb = 0.;
			for (int j=newEvents.size()-1; j>=0; j--) {
				AceEvent newEvent = (AceEvent) newEvents.get(j);
				if (event.type.equals(newEvent.type) &&
				    event.subtype.equals(newEvent.subtype)) {
				  AceEventMention m = (AceEventMention) event.mentions.get(0);
		    	String anchor =
		    		EventPattern.normalizedAnchor (m.anchorExtent, m.anchorText, doc, relations);
				  Datum d = corefFeatures (newEvent, event, anchor);
				  double prob = corefModel.eval(d.toArray())[corefModel.getIndex("merge")];
					if (prob > COREF_THRESHOLD && prob > priorEventProb) {
						priorEventIndex = j;
						priorEventProb = prob;
					}
				}
			}
			if (priorEventIndex >= 0) {
				AceEvent priorEvent = (AceEvent) newEvents.get(priorEventIndex);
				priorEvent.arguments = mergeArguments (event.arguments, priorEvent.arguments);
				AceEventMention m = (AceEventMention) event.mentions.get(0);
				priorEvent.addMention(m);
				//     fix id for new mention
				m.setId(priorEvent.id + "-" + priorEvent.mentions.size());
			} else {
				// if not, put event on newEvents
				newEvents.add(event);
			}
		}
		aceDoc.events = newEvents;
		logger.info ("eventCoref:  {} events", events.size());
	}

	private boolean compatibleArguments (ArrayList args1, ArrayList args2) {
		boolean intersect = false;
		for (int i=0; i<args1.size(); i++) {
			AceEventArgument arg1 = (AceEventArgument) args1.get(i);
			if (arg1.confidence < COREF_CONFIDENCE) continue;
			String role1 = arg1.role;
			String id1 = arg1.value.id;
			for (int j=0; j<args2.size(); j++) {
				AceEventArgument arg2 = (AceEventArgument) args2.get(j);
				if (arg2.confidence < COREF_CONFIDENCE) continue;
				String role2 = arg2.role;
				String id2 = arg2.value.id;
				if (role1.equals(role2))
					if (id1.equals(id2))
						intersect = true;
					else
						return false;
			}
		}
		return intersect;
	}

	private ArrayList<AceEventArgument> mergeArguments 
		(ArrayList<AceEventArgument> args1, ArrayList<AceEventArgument> args2) {
		ArrayList<AceEventArgument> result = new ArrayList<AceEventArgument>(args1);
		nextarg:
		for (int i=0; i<args2.size(); i++) {
			AceEventArgument arg2 = args2.get(i);
			String role2 = arg2.role;
			String id2 = arg2.value.id;
			for (int j=0; j<args1.size(); j++) {
				AceEventArgument arg1 = args1.get(j);
				String role1 = arg1.role;
				String id1 = arg1.value.id;
				if (role1.equals(role2) && id1.equals(id2))
					continue nextarg;
			}
			result.add(arg2);
		}
		return result;
	}

	/**
	 *  write a human-readable report of all the event patterns to file
	 *  <CODE>reportFile</CODE>.
	 */
	 
	public void report (String reportFile) throws IOException {
		PrintWriter reportWriter = new PrintWriter (new FileWriter (reportFile));
		Set anchors = anchorMap.keySet();
		Iterator iter = anchors.iterator();
		while (iter.hasNext()) {
			String anchor = (String) iter.next();
			reportWriter.println("\n" + anchor + " ================================");
			List patterns = (List) anchorMap.get(anchor);
			for (int j=0; j<patterns.size(); j++) {
				EventPattern ep = (EventPattern) patterns.get(j);
				reportWriter.println(ep.toString());
			}
		}
		reportWriter.close();
	}

	/**
	 *  write out all the patterns to file 'fileName' in a form which
	 *  can be reloaded by the 'load' method.
	 */

	public void save (String fileName) throws IOException {
		PrintWriter writer = new PrintWriter (new FileWriter (fileName));
		Set anchors = anchorMap.keySet();
		Iterator iter = anchors.iterator();
		while (iter.hasNext()) {
			String anchor = (String) iter.next();
			List patterns = (List) anchorMap.get(anchor);
			for (int j=0; j<patterns.size(); j++) {
				EventPattern ep = (EventPattern) patterns.get(j);
				ep.write(writer);
			}
		}
		writer.close();
	}

	/**
	 *  load an event pattern set in the form saved by the 'save' method.
	 */

	public void load (String fileName) throws IOException {
		BufferedReader reader = new BufferedReader (new FileReader (fileName));
		int patternCount = 0;
		String line = reader.readLine();
		while (line != null) {
			EventPattern ep = new EventPattern(reader);
			addBasicPattern (ep.anchor, ep);
			patternCount++;
			line = reader.readLine();
		}
		System.out.println (patternCount + " patterns loaded");
	}

	static String docDir;
	static String modelDir;
	static String outputDir;
	static String glarfDir;
	
	public void loadAllModels (String modelDir) throws IOException {
		eventModel = loadClassifierModel(modelDir + "eventModel.log");
		// evTypeModel = loadClassifierModel(evTypeModelFileName);
		argModel = loadClassifierModel(modelDir + "argModel.log");
		roleModel = loadClassifierModel(modelDir + "roleModel.log");
		corefModel = loadClassifierModel(modelDir + "corefModel.log");
	}

	/**
	 *  tag a set of documents with ACE event information, using entity
	 *  and value information from an existing APF file, typically the
	 *  key.
	 *  Takes the following arguments:
	 *  <ul>
	 *  <li> props:  Jet property file
	 *  <li> fileList:  list of files to be processed (one per line)
	 *  <li> docDir:    directory containing text and APF files
	 *  <li> modelDir:  directory containing event patterns and models
	 *  <li> outDir:    directory to receive updated APF files
	 *                  including event information
	 *  <li> glarfDir:  directory containing glarf triples (optional)
	 *  <li> glarfSuffix:  file extension for glarf files
	 *  </ul>
	 */

	public static void main (String[] args) throws IOException {
		System.out.println("Starting ACE event tagger.");
		if (args.length != 5 && args.length != 7) {
			System.out.println ("EventTagger must take 5 or 7 arguments:");
			System.out.println ("    properties filelist documentDir modelDir outputDir" +
			                    " [glarfDir glarfSuffix]");
			System.exit(1);
		}
		String propertyFile = args[0];
		String fileListTest = args[1];
		docDir = args[2];
		if (!docDir.endsWith("/")) docDir += "/";
		modelDir = args[3];
		if (!modelDir.endsWith("/")) modelDir += "/";
		outputDir = args[4];
		if (!outputDir.endsWith("/")) outputDir += "/";
		glarfDir = null;
		if (args.length == 7) { 
			glarfDir = args[5];
			if (!glarfDir.endsWith("/")) glarfDir += "/";
			triplesSuffix = args[6];
			usePA = true;
		}

		// initialize Jet
		JetTest.initializeFromConfig (propertyFile);
		Pat.trace = false;
		Resolve.trace = false;
		Resolve.ACE = true;
		AceDocument.ace2005 = true;
		Ace.writeEventConfidence = JetTest.getConfigFile("Ace.writeEventConfidence") != null;
		EVENT_PROBABILITY_THRESHOLD = 
			Ace.getConfigDouble("Ace.EventModels.eventProbabilityThreshold",
			                    EVENT_PROBABILITY_THRESHOLD);
		ARGUMENT_PROBABILITY_THRESHOLD = 
			Ace.getConfigDouble("Ace.EventModels.argumentProbabilityThreshold",
			                    ARGUMENT_PROBABILITY_THRESHOLD);
		EventTagger et = new EventTagger();
		et.load (modelDir + TrainEventTagger.eventPatternFile);
		et.loadAllModels (modelDir);
		et.tag (fileListTest);
	}
}
