// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2006
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkit
//              (ACE extensions)

package AceJet;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;
import Jet.MaxEntModel;
import Jet.Parser.*;
import Jet.Control;
import Jet.JetTest;
import Jet.Pat.Pat;
import Jet.Refres.Resolve;

import opennlp.maxent.*;
import opennlp.maxent.io.*;


/**
 *  assigns ACE events to a Document, given the entities, times, and values.
 *  <P>
 *  This tagger is based entirely on a set of four classifier models,
 *  <ul>
 *  <li> eventModel to decide whether an instance of an anchor is an event trigger
 *  <li> argModel to decide whether a mention is an argument of an event
 *  <li> roleModel to decide which role the mention should have
 *  <li> corefModel to decide when to merge eventMentions
 *  </ul>
 */

public class NewEventTagger {

	MaxEntModel eventModel, argModel, roleModel, corefModel;

	/**
	 *  the set of words which serve as event anchors (triggers).
	 */
	Set<String> anchorSet;
	/**
	 *  the directory into which all feature files and event models are stored.
	 */
	String eventDirectory;

	static final String home =
	    "C:/Documents and Settings/Ralph Grishman/My Documents/";
	static final String ace =
			home + "Ace 05/V4/";
	static String triplesDir =
			ace + "011306-fast-tuples/";    // new Charniak parser, fast mode
	static String triplesSuffix =
			".sent.txt.ns-2005-fast-ace-n-tuple92";

	// use predicate-argument roles from GLARF
	static boolean usePA = true;
	// use parser (pre-parsed text), else use chunker
	static boolean useParser = true;
	// if true, main method trains, else main method tags
	static boolean train = false;

	/**
	 *  minimal probability for an arg to be added to an event
	 */
	static double MIN_ARG_PROBABILITY = 0.35;
	/**
	 *  minimal probability for merging two events
	 */
	static double COREF_THRESHOLD = 0.10;
	/**
	 *  minimal confidence for an arg to be used for coref determination
	 *  (should be >= MIN_ARG_PROBABILITY)
	 */
	static double COREF_CONFIDENCE = 0.35;

	NewEventTagger (String dir) {
		eventDirectory = dir;
		eventModel = new MaxEntModel(dir + "eventFeatureFile.log", dir + "eventModel.log");
		argModel   = new MaxEntModel(dir + "argFeatureFile.log", dir + "argModel.log");
		roleModel  = new MaxEntModel(dir + "roleFeatureFile.log", dir + "roleModel.log");
		corefModel = new MaxEntModel(dir + "corefFeatureFile.log", dir + "corefModel.log");
	}

	static final String fileListTrain1 = ace + "perfect-parses/nw-tail.txt";
	static final String fileListTrain2 = ace + "perfect-parses/bn-tail.txt";
	static final String fileListTrain3 = ace + "perfect-parses/bc-tail.txt";
	static final String fileListTrain4 = ace + "perfect-parses/wl-tail.txt";
	static final String fileListTrain5 = ace + "perfect-parses/cts-tail.txt";
	static final String fileListTrain6 = ace + "perfect-parses/un-tail.txt";
	static final String fileListTest = ace + "perfect-parses/head6.txt";


	public static void main (String[] args) throws IOException {
		if (useParser)
			JetTest.initializeFromConfig("props/ace use parses.properties");
		else
			JetTest.initializeFromConfig("props/ME ace 05.properties");
		Ace.gazetteer = new Gazetteer();
		Ace.gazetteer.load("data/loc.dict");
		Pat.trace = false;
		Resolve.trace = false;
		AceDocument.ace2005 = true;

		NewEventTagger et = new NewEventTagger("eventTemp/");
		if (train) {
			String[] filelists = new String[]{fileListTrain1, fileListTrain2,
			                                  fileListTrain3, fileListTrain4,
			                                  fileListTrain5, fileListTrain6};
			et.train (filelists);
		} else {
			et.loadModels ();
			et.tag (fileListTest);
		}
	}

	/**
	 *  trains the event models on the documents in the list of file lists,
	 *  <CODE>fileLists</CODE>.
	 */

	public void train (String[] fileLists) throws IOException {
		anchorSet = new HashSet<String>();
		for (int pass = 1; pass <= 2; pass++) {
			for (String filelist : fileLists) {
				trainOnFilelist (filelist, pass);
			}
		}
		saveAnchorSet();
		eventModel.setCutoff(1);
		eventModel.buildModel();
		eventModel.saveModel();
		argModel.setCutoff(2);
		argModel.buildModel();
		argModel.saveModel();
		roleModel.setCutoff(2);
		roleModel.buildModel();
		roleModel.saveModel();
		corefModel.buildModel();
		corefModel.saveModel();
	}

	private void saveAnchorSet () throws IOException {
		PrintStream writer = new PrintStream (new FileOutputStream (eventDirectory + "anchors.log"));
		for (String anchor : anchorSet)
			writer.println (anchor);
		writer.close();
	}

	/**
	 *  trains an event tagger from a set of text and APF files.
	 *  @param fileList  a list of text file names, one per line.
	 *                   The APF file names are obtained by replacing
	 *                   'sgm' by 'apf.xml'.
	 *  @param pass      =1 to collect anchors, =2 to build classifier models
	 */

	public void trainOnFilelist (String fileList, int pass) throws IOException {
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		String currentDocPath;
		while ((currentDocPath = reader.readLine()) != null) {
			docCount++;
			// if (docCount > 10) break;
			System.out.println ("\nProcessing file " + docCount + ": " +
			                    currentDocPath + " (pass " + pass +")");
		  String textFile = ace + (useParser ? "perfect-parses/" : "") + currentDocPath;
			String xmlFile = ace + currentDocPath.replaceFirst(".sgm", ".apf.xml");
			ExternalDocument doc = new ExternalDocument("sgml", textFile);
			doc.setAllTags(true);
			doc.open();
			doc.stretchAll();
			Resolve.ACE = true;
			Control.processDocument (doc, null, false, 0);
			AceDocument aceDoc = new AceDocument(textFile, xmlFile);
			if (pass == 1) {
				collectAnchorsFromDocument (doc, aceDoc, currentDocPath.replaceFirst(".sgm",""));
			} else {
				trainOnDocument (doc, aceDoc, currentDocPath.replaceFirst(".sgm",""));
			}
		}
		reader.close();
	}

	/**
	 *  collects the anchors (triggers) from an annotated document.
	 */

	public void collectAnchorsFromDocument
			(Document doc, AceDocument aceDoc, String docPath) {
		SyntacticRelationSet relations = new SyntacticRelationSet();
		if (usePA) {
			relations.readRelations (triplesDir + docPath + triplesSuffix);
		} else {
			relations.addRelations(doc);
		}
		ArrayList events = aceDoc.events;
		for (int i=0; i<events.size(); i++) {
			AceEvent event = (AceEvent) events.get(i);
			ArrayList mentions = event.mentions;
			for (int j=0; j<mentions.size(); j++) {
				AceEventMention m = (AceEventMention) mentions.get(j);
				// System.out.println ("\nProcessing mention " + m.id + " = " + m.text);
				// .. get anchor extent and text
				Span anchorExtent = m.anchorJetExtent;
				String anchor =
					EventPattern.normalizedAnchor (anchorExtent, m.anchorText, doc, relations);
				anchorSet.add(anchor);
			}
		}
	}

	/**
	 *  trains the four statistical models on an annotated document.
	 */

	public void trainOnDocument
			(Document doc, AceDocument aceDoc, String docPath) {
		SyntacticRelationSet relations = new SyntacticRelationSet();
		if (usePA) {
			relations.readRelations (triplesDir + docPath + triplesSuffix);
		} else {
			relations.addRelations(doc);
		}
		trainCorefNewDocument();
		ArrayList events = aceDoc.events;
		// iterate over potential anchors
		Vector constituents = doc.annotationsOfType("constit");
		for (int i=0; i<constituents.size(); i++) {
			Annotation constit = (Annotation) constituents.get(i);
			if (anchorConstit(constit)) {
				String anchor = EventPattern.normalizedAnchor (constit, doc, relations);
				if (!anchorSet.contains(anchor))
					continue;
				Span anchorExtent = constit.span();
				String anchorText = doc.text(anchorExtent);
				Datum d = eventFeatures (anchorExtent, anchor, anchorText, doc, aceDoc, relations);
				// outcome:
				//    is this the anchor of a true event?
				AceEvent event = findEventAt(anchorExtent, events);
				boolean isEvent = event != null;
				d.setOutcome(isEvent ? (event.type + ":" + event.subtype): "noEvent");
				eventModel.addEvent(d);
				if (isEvent) {
					trainArgClassifier (event, eventMentionAtAnchor, doc, aceDoc, relations);
					trainCoref (eventMentionAtAnchor, anchor, event.id, event.type, event.subtype);
				}
			}
		}
	}

	private static boolean anchorConstit (Annotation constit) {
		String cat = (String) constit.get("cat");
		return cat == "n" || cat == "v" || cat == "tv" || cat == "ven" ||
				   cat == "ving" || cat == "adj";
	}

	private AceEventMention eventMentionAtAnchor;

	/**
	 *  returns the AceEvent in <CODE>keyEvents</CODE>, if any, which is anchored
	 *  at <CODE>anchorExtent</CODE>.  If no such event exists, returns null.
	 */

	private AceEvent findEventAt (Span anchorExtent, ArrayList keyEvents) {
		for (int i=0; i<keyEvents.size(); i++) {
			AceEvent keyEvent = (AceEvent) keyEvents.get(i);
			ArrayList keyMentions = keyEvent.mentions;
			for (int j=0; j<keyMentions.size(); j++) {
				AceEventMention keyMention = (AceEventMention) keyMentions.get(j);
				Span keyExtent = keyMention.anchorJetExtent;
				if (anchorExtent.within(keyExtent)) {
				  eventMentionAtAnchor = keyMention;
					return keyEvent;
				}
			}
		}
		return null;
	}

	private Datum eventFeatures (Span anchorExtent, String anchor, String anchorText,
			Document doc, AceDocument aceDoc, SyntacticRelationSet relations) {
		//   build feature entry
		Datum d = new Datum();
		//   = anchor word
		d.addFV ("anchor", anchor);
		int anchorPosition = anchorExtent.start();
		SyntacticRelation r = relations.getRelation(anchorPosition, "PRT");
		if (r != null) {
			String particle = r.targetWord;
			d.addFV ("anchorWithParticle", anchor + "_" + particle);
		}
		// ---------
		Annotation sentence = findContainingSentence (doc, anchorExtent);
		if (sentence == null) return d;
		// iterate over mentions in sentence
		for (AceMention mention : mentionsInSpan (aceDoc, sentence.span())) {
			// compute syntactic path from anchor
			String spath = EventSyntacticPattern.buildSyntacticPath
			  (anchorPosition, mention.getJetHead().start(), relations);
			// if connected by a single syntactic link, use as feature
			if (spath != null && spath.indexOf(':') < 0)
				d.addFV ("arg", anchor + ":" + spath + ":" + mention.getType());
		}
		// ---------
		return d;
	}

	private void trainArgClassifier (AceEvent event, AceEventMention eventMention,
			Document doc, AceDocument aceDoc, SyntacticRelationSet relations) {
		// get anchor
		Span anchorExtent = eventMention.anchorJetExtent;
		String anchorText = eventMention.anchorText;
		String anchor = EventPattern.normalizedAnchor (anchorExtent, anchorText, doc, relations);
		AceEventAnchor anchorMention =
			new AceEventAnchor (anchorExtent, anchorExtent, anchorText, doc);
		// find sentence containing anchor
		Annotation sentence = findContainingSentence (doc, anchorExtent);
		if (sentence == null) return;
		// iterate over mentions in sentence
		Set<String> rolesFilled = new HashSet<String>();
		for (AceMention mention : closestMentionsFirst (mentionsInSpan (aceDoc, sentence.span()),
		                                                anchorExtent.start())) {
			// determine if mention has role in event
		  ArrayList arguments = eventMention.arguments;
		  String role = "noArg";
		  for (int ia=0; ia<arguments.size(); ia++) {
		  	AceEventMentionArgument argument = (AceEventMentionArgument) arguments.get(ia);
		  	if (argument.value.equals(mention)) {
		  		role = argument.role;
		  		break;
		  	}
		  }
		  Datum d = argumentFeatures (doc, anchor, event, mention, anchorMention,
		  	rolesFilled, relations);
			//   outcome = argument role
			if (role == "noArg") {
				d.setOutcome("noArg");
				argModel.addEvent(d);
			} else {
				d.setOutcome("arg");
				argModel.addEvent(d);
				d.setOutcome (role);
				roleModel.addEvent(d);
				rolesFilled.add(role);
			}
		}
	}

	/**
	 *  returns all AceMentions (entity, value, and timex mentions) within
	 *  Span <CODE>span</CODE> of AceDocument <CODE>aceDoc</CODE>.
	 */

	private ArrayList<AceMention> mentionsInSpan (AceDocument aceDoc, Span span) {
		ArrayList<AceMention> mentions = new ArrayList();
		for (AceMention mention : aceDoc.getAllMentions())
			if (mention.jetExtent.within(span))
				mentions.add(mention);
		return mentions;
	}

	private ArrayList<AceMention> closestMentionsFirst (ArrayList<AceMention> mentions, int p) {
		ArrayList<AceMention> m = new ArrayList<AceMention>(mentions);
		for (int i=0; i<mentions.size()-1; i++) {
			for (int j=i+1; j<mentions.size(); j++) {
				int iDistance = Math.abs(mentions.get(i).getJetHead().start() - p);
				int jDistance = Math.abs(mentions.get(j).getJetHead().start() - p);
				if (iDistance > jDistance) {
					AceMention temp = mentions.get(i);
					mentions.set(i, mentions.get(j));
					mentions.set(j, temp);
				}
			}
		}
		return mentions;
	}

	private Datum argumentFeatures
	  (Document doc, String anchor, AceEvent event, AceMention mention,
	   AceEventAnchor anchorMention, Set<String> rolesFilled, SyntacticRelationSet relations) {
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
		if (anchorMention.passive)
			direction += "/passive";
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
		for (String role : rolesFilled)
			d.addFV ("filled", role);
		return d;
	}

	/**
	 *  returns the sentence in Document <CODE>doc</CODE> which contains Span
	 *  <CODE>span</CODE>.
	 */

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

	/* ------------ tagging methods -------------------------------------- */

	public void loadModels () {
		eventModel.loadModel();
		argModel.loadModel();
		roleModel.loadModel();
		corefModel.loadModel();
		loadAnchorSet();
	}

	public void loadAnchorSet () {
		try {
			anchorSet = new HashSet<String>();
			BufferedReader reader = new BufferedReader (new FileReader(eventDirectory + "anchors.log"));
			String anchor;
			while ((anchor = reader.readLine()) != null) {
				anchorSet.add(anchor);
			}
		} catch (IOException e) {
			System.err.println ("***** EventTagger:  cannot read anchors.log");
			System.err.println (e);
		}
	}

	/**
	 *  tags all the documents in <CODE>fileList</CODE> with events.
	 */

	public void tag (String fileList) throws IOException {
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		String currentDocPath;
		while ((currentDocPath = reader.readLine()) != null) {
			System.out.println ("\nProcessing file " + currentDocPath);
		  // String textFile = ace + currentDocPath;
		  String textFile = ace + (useParser ? "perfect-parses/" : "") + currentDocPath;
			String xmlFile = ace + currentDocPath.replaceFirst(".sgm", ".apf.xml");
			String outputFile = ace	 + "output/" + currentDocPath.replaceFirst(".sgm", ".apf");
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
	 *  identify ACE events in Document 'doc' and add them to 'aceDoc'.
	 */

	public void tag (Document doc, AceDocument aceDoc, String currentDocPath, String docId) {
		SyntacticRelationSet relations = new SyntacticRelationSet();
		if (usePA) {
			relations.readRelations (triplesDir + currentDocPath + triplesSuffix);
		} else {
			relations.addRelations(doc);
		}
		LearnRelations.findEntityMentions (aceDoc);
		LearnRelations.findConjuncts (doc);
		int aceEventNo = 1;
		Vector constituents = doc.annotationsOfType("constit");
		HashSet matchedAnchors = new HashSet();
		if (constituents != null) {
			for (int i=0; i<constituents.size(); i++) {
				Annotation constit = (Annotation) constituents.get(i);
				if (anchorConstit(constit)) {
				  Span anchorExtent = constit.span();
				  if (matchedAnchors.contains(anchorExtent)) continue; //<< added 13 Feb 06
					AceEvent event = eventAnchoredByConstituent
					  (constit, doc, aceDoc, docId, relations, aceEventNo);
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

	AceEvent eventAnchoredByConstituent
		(Annotation constit, Document doc, AceDocument aceDoc, String docId,
		 SyntacticRelationSet relations, int aceEventNo) {
		String anchor = EventPattern.normalizedAnchor (constit, doc, relations);
		if (!anchorSet.contains(anchor))
			return null;
		Span anchorExtent = constit.span();
		String anchorText = doc.text(anchorExtent);
		//
		// classify as event / non-event & determine type
		//
		Datum d = eventFeatures (anchorExtent, anchor, anchorText, doc, aceDoc, relations);
		String eventType = eventModel.bestOutcome(d);
		if (eventType == "noEvent")
			return null;
		System.out.println ("Generating " + eventType + " event for " + anchor); // <<<
		//
		// build event mention and event
		//
		String[] eventTypeSubtype = eventType.split(":");
		if (eventTypeSubtype.length != 2) {
			System.err.println ("*** EventTagger: Invalid event type:subtype " + eventType);
			return null;
		}
		String eventId = docId + "-EV" + aceEventNo;
		AceEventMention eventMention = new AceEventMention
			(eventId + "-1", anchorExtent, anchorExtent, doc.text());
		AceEvent event = new AceEvent
			(eventId, eventTypeSubtype[0], eventTypeSubtype[1]);
		event.addMention(eventMention);
		// collect additional arguments using statistical model
		collectArguments (event, eventMention, doc, aceDoc, relations);
		// should recompute extent based on arguments!!
		return event;
	}

	/**
	 *  add arguments to 'event' using classifiers to decide if a mention is
	 *  an argument, and to assign that argument a role.
	 */

	private void collectArguments (AceEvent event, AceEventMention eventMention,
			Document doc, AceDocument aceDoc, SyntacticRelationSet relations) {
		// get anchor
		Span anchorExtent = eventMention.anchorJetExtent;
		String anchorText = eventMention.anchorText;
		String anchor = EventPattern.normalizedAnchor (anchorExtent, anchorText, doc, relations);
		AceEventAnchor anchorMention = new AceEventAnchor (anchorExtent,
		  	  eventMention.anchorJetExtent, anchorText, doc);
		Set rolesFilled = new HashSet();
		Set argumentsUsed = new HashSet();
		// find sentence containing anchor
		Annotation sentence = findContainingSentence (doc, anchorExtent);
		if (sentence == null) return;
		// iterate over mentions in sentence
		HashMap bestRoleProb = new HashMap();
		HashMap bestRoleFiller = new HashMap();
		for (AceMention mention : closestMentionsFirst(mentionsInSpan (aceDoc, sentence.span()),
		                                               anchorExtent.start())) {
			if (mention.getJetHead().within(anchorExtent)) continue; // Nov. 4 2005
			//   build feature entry
			Datum d = argumentFeatures (doc, anchor, event, mention, anchorMention,
			                            rolesFilled, relations);
			//   classify:
			//      probability that this is an argument
			//      most likely role assignment
			double argProb = argModel.prob(d, "arg");
			String role = roleModel.bestOutcome(d).intern();
			//      if not a valid role for this event type, continue
			if (!AceEventArgument.isValid(event.subtype, role, mention)) continue;
			//      if likely argument, add to event
			if (argProb > MIN_ARG_PROBABILITY) {
				// don't use an argument twice
				AceEventArgumentValue argValue = mention.getParent();
				if (argumentsUsed.contains(argValue)) continue;
				AceEventMentionArgument mentionArg =
					new AceEventMentionArgument(mention, role);
				mentionArg.confidence = argProb;
				eventMention.arguments.add(mentionArg);
				AceEventArgument eventArg = new AceEventArgument(argValue, role);
				eventArg.confidence = argProb;
				event.arguments.add(eventArg);
				// System.out.println ("Adding " + mention.getHeadText() + " in role " +
				//                      role + " with prob " + argProb);
				argumentsUsed.add(argValue);
				rolesFilled.add(role);
			}
		}
	}

	// ------------ e v e n t    c o r e f e r e n c e ---------------------

	private ArrayList<AceEvent> priorEvents;

	private void trainCorefNewDocument () {
		priorEvents = new ArrayList<AceEvent>();
	}

	/**
	 *  trains the coref model.  This method is invoked once for each eventMention
	 *  in a document.  It determines (based on eventId) whether this mention is
	 *  part of a previously mentioned event (an event on <CODE>priorEvents</CODE>.
	 *  If so, it records the features for that event/eventMention pair with
	 *  outcome "merge";  for all other prior events, the event/eventMention pair
	 *  is recorded with outcome "noMerge".
	 */

	private void trainCoref (AceEventMention evMention, String anchor,
			String eventId, String eventType, String eventSubtype) {
		AceEvent event = buildEventFromMention (evMention, eventId, eventType, eventSubtype);
		boolean noAntecedent = true;
		for (AceEvent priorEvent : priorEvents) {
			Datum d = corefFeatures (priorEvent, event, anchor);
			if (priorEvent.id.equals(event.id)) {
		    priorEvent.arguments = mergeArguments (event.arguments, priorEvent.arguments);
				priorEvent.addMention(evMention);
		    d.setOutcome("merge");
		    noAntecedent = false;
		  } else {
		    d.setOutcome("noMerge");
		  }
			corefModel.addEvent(d);
		}
		if (noAntecedent)
			priorEvents.add(event);
	}

	private AceEvent buildEventFromMention (AceEventMention evMention,
			String eventId, String eventType, String eventSubtype) {
	  AceEvent event = new AceEvent (eventId, eventType, eventSubtype);
	  event.addMention(evMention);
	  for (AceEventMentionArgument mentionArg : evMention.arguments) {
	  	String role = mentionArg.role;
	  	AceMention mention = mentionArg.value;
	  	AceEventArgumentValue argValue = mention.getParent();
	  	AceEventArgument eventArg = new AceEventArgument(argValue, role);
	  	eventArg.confidence = mentionArg.confidence;
			event.arguments.add(eventArg);
		}
		return event;
	}

	/**
	 *  returns a set of features used to decide on event coreference between events
	 *  <CODE>priorEvent</CODE> and <CODE>event</CODE>.  These features include
	 *  <ul>
	 *  <li> the type / subtype of event (only events of the same type and subtype
	 *       can be merged)
	 *  <li> the (normalized) anchor of the second event mention
	 *  <li> the distance between the event mentions
	 *  <li> whether the anchors match
	 *  <li> whether the arguments match (same role and value) or conflict
	 *       (same role, different values)
	 *  </ul>
	 */

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
				if (role1.equals(role2))
					if (id1.equals(id2))
						d.addFV ("overlap", role1);
					else
						d.addFV ("conflict", role1);
			}
		}
		// determiner of noun anchor
		return d;
	}

	/**
	 *  performs coreference on the events in an Ace document.  On entry, the
	 *  AceDocument <CODE>aceDoc</CODE> should have a set of events each with a
	 *  single mention.  The event mentions which are believed to corefer are
	 *  combined into a single event.
	 */

	public void eventCoref (AceDocument aceDoc, Document doc, SyntacticRelationSet relations) {
		ArrayList events = aceDoc.events;
		System.out.println ("eventCoref: " + events.size() + " event mentions");
		ArrayList<AceEvent> newEvents = new ArrayList<AceEvent>();
		nextevent:
		for (int i=0; i<events.size(); i++) {
			AceEvent event = (AceEvent) events.get(i);
			// is there a prior event on newEvents of the same type
			// such that the arguments are compatible?
			int priorEventIndex =  -1;
			double priorEventProb = 0.;
			for (int j=newEvents.size()-1; j>=0; j--) {
				AceEvent newEvent = newEvents.get(j);
				if (event.type.equals(newEvent.type) &&
				    event.subtype.equals(newEvent.subtype)) {
				  AceEventMention m = (AceEventMention) event.mentions.get(0);
		    	String anchor =
		    		EventPattern.normalizedAnchor (m.anchorExtent, m.anchorText, doc, relations);
				  Datum d = corefFeatures (newEvent, event, anchor);
				  double prob = corefModel.prob(d, "merge");
					if (prob > COREF_THRESHOLD && prob > priorEventProb) {
						priorEventIndex = j;
						priorEventProb = prob;
					}
				}
			}
			if (priorEventIndex >= 0) {
				AceEvent priorEvent = newEvents.get(priorEventIndex);
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
		System.out.println ("eventCoref: " + newEvents.size() + " events");
	}

	/**
	 *  computes the union of the event argument lists <CODE>args1</CODE> and
	 *  <CODE>args2</CODE>, returning a list including all arguments
	 *  <I>except</I> those whose role and value are identical in the two lists.
	 */

	private ArrayList<AceEventArgument> mergeArguments
			(ArrayList<AceEventArgument> args1, ArrayList<AceEventArgument> args2) {
		ArrayList<AceEventArgument> result = new ArrayList<AceEventArgument>(args1);
		nextarg:
		for (AceEventArgument arg2 : args2) {
			String role2 = arg2.role;
			String id2 = arg2.value.id;
			for (AceEventArgument arg1 : args1) {
				String role1 = arg1.role;
				String id1 = arg1.value.id;
				if (role1.equals(role2) && id1.equals(id2))
					continue nextarg;
			}
			result.add(arg2);
		}
		return result;
	}

}
