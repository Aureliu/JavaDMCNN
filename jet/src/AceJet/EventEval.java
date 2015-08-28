// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkit
//              (ACE extensions)

package AceJet;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;
import Jet.Parser.SyntacticRelationSet;
import Jet.JetTest;
import Jet.Control;
import Jet.Pat.Pat;
import Jet.Refres.Resolve;

/**
 *  EventEval evaluates an event tagger using simple metrics of events and
 *  arguments found, missing, and spurious.
 */

public class EventEval {

	static int correctArgs, missingArgs, spuriousArgs;
	static int correctEvents, typeErrorEvents, missingEvents, spuriousEvents;
	// static EventTagger evTagger;
	static NewEventTagger evTagger;

	static final String home =
	    "C:/Documents and Settings/Ralph Grishman/My Documents/";
	static final String ace =
			home + "Ace 05/V4/";
	static final String fileListTest = ace + "perfect-parses/head6.txt";


	public static void main (String[] args) throws IOException {
		if (EventTagger.useParser)
			JetTest.initializeFromConfig("props/ace use parses.properties");
		else
			JetTest.initializeFromConfig("props/ME ace 05.properties");
		Ace.gazetteer = new Gazetteer();
		Ace.gazetteer.load("data/loc.dict");
		Pat.trace = false;
		Resolve.trace = false;
		AceDocument.ace2005 = true;
		/*  old event tagger
		evTagger = new EventTagger();
		evTagger.load (EventTagger.eventPatternFile);
		/* */
		/*  new event tagger */
		evTagger = new NewEventTagger("eventTemp/");
		evTagger.loadModels();
		/* */
		evalEvents (fileListTest);
	}

	/**
	 *  evaluate the event tagger using the documents list in file
	 *  <CODE>fileList</CODE>.
	 */

	public static void evalEvents (String fileList) throws IOException {
		EventTagger.useArgumentModel = false;                 //<<<
		EventPattern.useChunkPatterns = true;    							//<<<
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		correctArgs = 0;
		missingArgs = 0;
		spuriousArgs = 0;
		correctEvents = 0;
		typeErrorEvents = 0;
		missingEvents = 0;
		spuriousEvents = 0;
		String currentDocPath;
		while ((currentDocPath = reader.readLine()) != null) {
			System.err.println ("\nProcessing file " + currentDocPath);
		  // String textFile = ace + currentDocPath;
		  String textFile = ace + (EventTagger.useParser ? "perfect-parses/" : "") + currentDocPath;
			String xmlFile = ace + currentDocPath.replaceFirst(".sgm", ".apf.xml");
			String outputFile = ace	 + "output/" + currentDocPath.replaceFirst(".sgm", ".apf");
			// for evaluation --------------
			/*
			textFile = home + "Ace 05/eval/allparses/" + currentDocPath;
			xmlFile = home + "Ace 05/eval/ACE05_diagdata_v1/english/" +
			                        currentDocPath.replaceFirst(".sgm", ".entities.apf.xml");
			outputFile = home + "Ace 05/eval/output/" +
			                        currentDocPath.replaceFirst(".sgm", ".apf.xml");
			*/
			// ------------
			String docId = currentDocPath.replaceFirst(".sgm","");
			ExternalDocument doc = new ExternalDocument("sgml", textFile);
			doc.setAllTags(true);
			doc.open();
			Control.processDocument (doc, null, false, 0);
			AceDocument aceDoc = new AceDocument(textFile, xmlFile);
			evalEvents (doc, aceDoc, docId);
		}
		System.out.println ("Events:  " +
		                    correctEvents + " correct; " +
		                    typeErrorEvents + " type errors; " +
		                    missingEvents + " missing; " +
		                    spuriousEvents + " spurious");
		System.out.println ("Arguments:  " +
		                    correctArgs + " correct; " +
		                    missingArgs + " missing; " +
		                    spuriousArgs + " spurious");

	}

	/**
	 *  evaluate the event tagger on Document <CODE>doc</CODE>.
	 */

	public static void evalEvents (Document doc, AceDocument aceDoc, String docId) {
		SyntacticRelationSet relations = new SyntacticRelationSet();
		if (EventTagger.usePA) {
			relations.readRelations (EventTagger.glarfDir + docId + EventTagger.triplesSuffix);
		} else {
			relations.addRelations(doc);
		}
		ArrayList events = aceDoc.events;
		Vector constituents = doc.annotationsOfType("constit");
		HashSet matchedAnchors = new HashSet();
		for (int i=0; i<constituents.size(); i++) {
			Annotation constit = (Annotation) constituents.get(i);
			String cat = (String) constit.get("cat");
			if (cat == "n" || cat == "v" || cat == "tv" || cat == "ven" ||
				  cat == "ving" || cat == "adj") {
				String anchor = EventPattern.normalizedAnchor (constit, doc, relations);
				Span anchorExtent = constit.span();
				if (matchedAnchors.contains(anchorExtent)) {  //<< added 13 Feb 06
					System.err.println("** Skipping duplicate anchor. **");
					continue;
				}
				AceEventMention keyMention = keyEventMention(anchorExtent, events);
				/*  old event tagger -- test only pattern matching
				AceEvent event = null;
				List patterns = (List) evTagger.anchorMap.get(anchor);
				if (patterns != null)
					event = evTagger.matchPatternSet (patterns, anchorExtent, anchor, doc, relations, aceDoc);
				/*	*/
				/*  old event tagger -- full processing
				AceEvent event = evTagger.eventAnchoredByConstituent
					(constit, doc, aceDoc, docId, relations, 0);
				event = evTagger.pruneEvent (event, constit, doc, relations);
				/* */
				/*  new event tagger */
				AceEvent event = evTagger.eventAnchoredByConstituent
					(constit, doc, aceDoc, docId, relations, 0);
				/* */
				if (keyMention != null) {
					ArrayList keyArguments = keyMention.arguments;
					if (event != null) {
						matchedAnchors.add(anchorExtent);
						if (event.subtype.equals(keyEvent.subtype)) {
							AceEventMention mention = (AceEventMention) event.mentions.get(0);
							ArrayList arguments = mention.arguments;
							ArrayList correctArguments = new ArrayList(arguments);
							correctArguments.retainAll(keyArguments);
							ArrayList spuriousArguments = new ArrayList(arguments);
							spuriousArguments.removeAll(keyArguments);
							ArrayList missingArguments = new ArrayList(keyArguments);
							missingArguments.removeAll(arguments);
							correctArgs += correctArguments.size();
							spuriousArgs += spuriousArguments.size();
							missingArgs += missingArguments.size();
							System.out.println ("For event: " + keyMention.text);
							System.out.println ("   type of event:   " + keyEvent.subtype);
							System.out.println ("   anchor:          " + anchor);
							// System.out.println ("   pattern matched: " + evTagger.patternMatched);
							for (int k=0; k<correctArguments.size(); k++) {
								System.out.println ("   correct  argument:  " + correctArguments.get(k));
							}
							for (int k=0; k<spuriousArguments.size(); k++) {
								System.out.println ("   spurious argument:  " + spuriousArguments.get(k));
							}
							for (int k=0; k<missingArguments.size(); k++) {
								System.out.println ("   missing  argument:  " + missingArguments.get(k));
							}
							correctEvents++;
						} else /* subtypes do not match */ {
							typeErrorEvents++;
						}
					} else /* key event and no system event */ {
						// count all args as missing
						// missingArgs += keyArguments.size();
						// count one event as missing
						missingEvents++;
					}
				} else /* no key event */ {
					if (event != null) {
						// count all args as spurious
						// AceEventMention mention = (AceEventMention) event.mentions.get(0);
						// ArrayList arguments = mention.arguments;
						// spuriousArgs += arguments.size();
						// count one event as spurious
						spuriousEvents++;
						System.err.println ("Spurious event of type " + event.type + ":" +
							event.subtype + " for " + anchor);
					}
				}
			}
		}
	}

	private static AceEvent keyEvent;

	private static AceEventMention keyEventMention (Span anchorExtent, ArrayList keyEvents) {
		for (int i=0; i<keyEvents.size(); i++) {
			keyEvent = (AceEvent) keyEvents.get(i);
			ArrayList keyMentions = keyEvent.mentions;
			for (int j=0; j<keyMentions.size(); j++) {
				AceEventMention keyMention = (AceEventMention) keyMentions.get(j);
				Span keyAnchorExtent = keyMention.anchorJetExtent;
				if (anchorExtent.start() == keyAnchorExtent.start())
					return keyMention;
			}
		}
		return null;
	}

}
