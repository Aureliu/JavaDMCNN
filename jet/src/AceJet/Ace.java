// -*- tab-width: 4 -*-
package AceJet;

//Author:       Ralph Grishman
//Date:         July 10, 2003

import java.util.*;
import java.io.*;
import Jet.*;
import Jet.Control;
import Jet.Refres.Resolve;
import Jet.Lex.Tokenizer;
import Jet.Pat.Pat;
import Jet.Lisp.*;
import Jet.Tipster.*;
import Jet.Parser.SynFun;
import Jet.Parser.ParseTreeNode;
import Jet.Parser.AddSyntacticRelations;
import Jet.Scorer.NameTagger;
import Jet.Time.TimeMain;
import Jet.Time.TimeAnnotator;
import Jet.Format.PTBReader;
import Jet.Format.InvalidFormatException;
import Jet.Zoner.SpecialZoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  procedures for generating ACE output for a Jet document.
 */

public class Ace {

	final static Logger logger = LoggerFactory.getLogger(Ace.class);
	public static boolean useParser = false;
	static final boolean useParseCollection = false;
	public static boolean perfectMentions = false;
	public static boolean perfectEntities = false;
	static final boolean asr = false;

	public static boolean preferRelations = false;
	public static boolean preferEntities = !preferRelations;

	public static boolean entityTrace = false;

	/**
	 *  if true, case information is not considered significant
	 *  (for finding names or sentence boundaries).
	 */

	public static boolean monocase = false;

	static String fileList;
	public static Gazetteer gazetteer;
	static int aceEntityNo;
	static HashMap aceTypeDict;
	static final String suffix = ".sgm.apf";
	// for formal evaluation
	// static final String suffix = ".apf.xml";
	static String sourceType = "text";
 	// relational model for kNN tagger
 	public static RelationPatternSet eve = null;
	static EventTagger eventTagger = null;

 	static String docDir;
	static String outputDir;
	static String parseDir;
	static String glarfDir;
	static int docCount = 0;
	static String parseSuffix = ".sgm.sent.chout";
	static NameTagger realNameTagger;
	/**
	 *  if true, output confidence for events and event arguments as part of APF
	 *  (non-standard APF).
	 */
	static boolean writeEventConfidence = false;

	/**
	 *  generate ACE annotation files (in APF) format for a list of documents.
	 *  Takes four to eight command line parameters:                             <BR>
	 *  propertyFile:  Jet properties file                                     <BR>
	 *  filelist:  a list of the files to be processed                         <BR>
	 *  docDirectory:  the path of the directory containing the input files    <BR>
	 *  outDirectory:  the path of the directory in which APF files are to
	 *                 be written                                              <BR>
	 *  parseDir:      the path of the directory containing parse trees
	 *                 [optional]                                              <BR>
	 *  glarfDir:      the path of the directory containing GLARF tuples
	 *                 [optional]                                              <BR>
	 *  glarfSuffix:   the file suffix for GLARF files                         <BR>
	 *  parseSuffix:   the file suffix for parse files                         <BR>
	 *  For each <I>file</I> in <I>filelist</I>, the document is read from
	 *  <I>docDirectory.file</I>.sgm and the APF file is written to
	 *  <I>outDirectory.file</I>.sgm.apf
	 */

	public static void main (String[] args) throws IOException {
		initForFileIO (args);  //initialize arguments
		// open list of files
		BufferedReader reader = new BufferedReader (new FileReader(fileList));		
		//a list of the files to be processed
		// debug = false ==> try to catch all exceptions and continue
		boolean debug = false;
		String currentDocPath;
		while ((currentDocPath = reader.readLine()) != null) {
			// process file at 'currentDocPath'
			if (debug) {
				processFile(currentDocPath);
			} else {
				try {
					processFile(currentDocPath);
				} catch (Exception e) {
					logger.error ("Error processing document " + 
						fileList + "(" + docCount + ")=" + currentDocPath, e);
				}
			}
		}
	}

	/**
	 *  'initForFileIO' provides the initialization component of the 'main' method.
	 */

	public static void initForFileIO (String[] args) throws IOException {
		// get arguments, initialize arguments
		logger.info("Starting ACE Jet...");
		if (args.length < 4 || args.length > 8) {
			logger.error ("Ace must take 4 to 8 arguments:");
			logger.error ("    properties filelist documentDir outputDir" +
			              " [parseDir [glarfDir [glarfSuffix [parseSuffix]]]]");
			System.exit(1);
		}
		String propertyFile = args[0];
		fileList = args[1];		//a list of the files to be processed
		docDir = args[2];
		outputDir = args[3];
		parseDir = null;
		if (args.length >= 5)	parseDir = args[4];
		String glarfDir = null;
		if (args.length >= 6) glarfDir = args[5];
		if (args.length >= 7) EventTagger.triplesSuffix = args[6];
		if (args.length == 8) parseSuffix = args[7];
		init (propertyFile);
	}

	/**
	 *  initialize ACE:  read property file and load all resources specified
	 *  by the properties file.
	 */

	public static void init (String propertyFile) throws IOException {
		// turn off traces (unless turned on by property file)
		Pat.trace = false; 
		Resolve.trace = false; 
		// initialize Jet
		JetTest.initializeFromConfig (propertyFile);
		realNameTagger = JetTest.nameTagger;
		// get version of Ace
		setAceYear ();
		if (JetTest.getConfig("Ace.PerfectEntities") != null) {
			perfectMentions = true;
			perfectEntities = true;
		}
		// set ACE mode for reference resolution
		Resolve.ACE = true;
		// Resolve.useMaxEnt = true;
		// load entity type dictionary
		EDTtype.readTypeDict();
		EDTtype.readGenericDict ();
		// load values dictionary
		String valueDictFile = JetTest.getConfigFile("Ace.Value.fileName");
		if (valueDictFile != null) {
			FindAceValues.readTypeDict(valueDictFile);
		} else {
			logger.info ("Ace:  no value dictionary file name specified in config file.");
			logger.info ("      Will not tag values.");
		}
		// load time annotation patterns
		String timeRulesFileName = JetTest.getConfigFile("Time.fileName");
		if (timeRulesFileName != null) {
			TimeMain.timeAnnotator = new TimeAnnotator(timeRulesFileName);
		} else {
			logger.info ("Ace:  no time rules file name specified in config file.");
			logger.info ("      Will not tag time expressions.");
		}

		// load relational models
		// ... old (kNN) patterns
		String relationPatternFile = JetTest.getConfigFile("Ace.RelationPatterns.fileName");
		if (relationPatternFile != null) {
			eve = new RelationPatternSet();
			eve.load(relationPatternFile, 0);
		}
		// ... new (maxent) model
		String relationModelFile = JetTest.getConfigFile("Ace.RelationModel.fileName");
		if (relationModelFile != null) {
			RelationTagger.loadModels (relationModelFile);
		}
		// ... dependency path (ICE) model
		String relationDepPathFile = JetTest.getConfigFile("Ace.RelationDepPaths.fileName");
		if (relationDepPathFile != null) {
			DepPathRelationTagger.loadModel (relationDepPathFile);
		}
		if (relationPatternFile == null && relationModelFile == null
			&& relationDepPathFile == null) {
			logger.info ("Ace:  no relation model specified in config file");
			logger.info ("      Will not tag relations.");
		}
		RelationTagger.tagSameSentence = JetTest.getConfigFile("Ace.Relations.tagSameSentence") != null;

		// load event model
		String eventModelsDir = JetTest.getConfigFile("Ace.EventModels.directory");
		if (eventModelsDir != null) {
			eventTagger = new EventTagger();
			EventTagger.useParser = useParser;
			EventTagger.usePA = (glarfDir != null);
			EventTagger.glarfDir = glarfDir;
			String eventPatternFile = eventModelsDir + "eventPatterns.log";
			eventTagger.load(eventPatternFile);
			eventTagger.loadAllModels (eventModelsDir);
		} else {
			logger.info ("Ace:  no event model file name specified in config file");
			logger.info ("      Will not tag events.");
		}
		writeEventConfidence = JetTest.getConfigFile("Ace.writeEventConfidence") != null;
		EventTagger.EVENT_PROBABILITY_THRESHOLD = 
			getConfigDouble("Ace.EventModels.eventProbabilityThreshold",
			                EventTagger.EVENT_PROBABILITY_THRESHOLD);
		EventTagger.ARGUMENT_PROBABILITY_THRESHOLD = 
			getConfigDouble("Ace.EventModels.argumentProbabilityThreshold",
			                          EventTagger.ARGUMENT_PROBABILITY_THRESHOLD);
	}
	
	/**
	 *  if <CODE>paramName</CODE> is a numeric-valued parameter in the Jet configuration
	 *  file, returns that value as a <CODE>double</CODE>, else returns
	 *  <CODE>defaultValue</CODE>.
	 */
	
	static double getConfigDouble (String paramName, double defaultValue) {
		String paramValue = JetTest.getConfig(paramName);
		if (paramValue == null)
			return defaultValue;
		try {
			return Double.parseDouble(paramValue);
		} catch (NumberFormatException e) {
			logger.error ("Error in Jet parameter " + paramName + " = " + paramValue);
			logger.error (e.toString());
			return defaultValue;
		}
	}

	static void setAceYear () {
		String aceYear = JetTest.getConfig("Ace.Year");
		if (aceYear != null)
			setAceYear (aceYear);
	}

	static void setAceYear (String aceYear) {
		if (aceYear.equals("2003")) {
			AceDocument.ace2004 = false;
			AceDocument.ace2005 = false;
		} else if (aceYear.equals("2004")) {
			AceDocument.ace2004 = true;
			AceDocument.ace2005 = false;
		} else if (aceYear.equals("2005")) {
			AceDocument.ace2004 = true;
			AceDocument.ace2005 = true;
		} else {
			logger.error ("Unrecognized value " + aceYear + " for Ace.Year");
		}
	}

	/**
	 *  process a single document to generate an Ace APF file, catching
	 *  any exceptions which occur.
	 */

	public static void processFileAndCatchError (String currentDocPath) throws IOException {
		try {
			processFile(currentDocPath);
		} catch (Exception e) {
			logger.error ("Error processing document " + docCount + 
				" = " + currentDocPath, e);
		}
	}

	/**
	 *  process a file containing a source document and generate an Ace APF file.
	 *
	 *  @param currentDocPath The full path of the file containing the source document
	 */

	public static void processFile (String currentDocPath) throws IOException{
		docCount++;
		logger.info ("\nProcessing document " + docCount + ": " + currentDocPath);
		String currentDocFileName = (new File(currentDocPath)).getName();
		String currentDocId = removeFileExtension(currentDocFileName);
		String currentDocPathBase = removeFileExtension(currentDocPath);
		// read document
		ExternalDocument doc = new ExternalDocument("sgml", docDir + currentDocPath);
		if (perfectMentions) {
			// doc = new ExternalDocument("sgml", docDir + "perfect parses/" + currentDocPath);
			String textFile = docDir + currentDocPath;
			String keyFile = docDir + currentDocPathBase + ".apf.xml";
			// String keyFile = docDir + currentDocPathBase + ".mentions.apf.xml"; //<< for corefeval
			// String keyFile = docDir + currentDocPathBase + ".entities.apf.xml"; //<< for rdreval
			AceDocument keyDoc = new AceDocument(textFile, keyFile);
			PerfectAce.buildEntityMentionMap (doc, keyDoc);
			JetTest.nameTagger = new PerfectNameTagger(keyDoc, realNameTagger);
		}
		doc.setAllTags(true);// if true, all tags should be converted to annotations
		// doc.setEmptyTags(new String[] {"W", "TURN"});
		doc.open();	//initialize the doc
		AceDocument aceDoc = processDocument (doc, currentDocId, currentDocFileName, currentDocPathBase);
		String apfFileName = outputDir + currentDocPathBase + suffix;
		PrintWriter apf = new PrintWriter(apfFileName, JetTest.encoding);
		// write APF file
		aceDoc.write(apf, doc);
	}

	/**
	 *  process a (Jet) document and create a corresponding AceDocument.
	 *
	 *  @param doc          The (Jet) source document
	 *
	 *  @param sourceId     The source document id; typically the 'sourceFile',
	 *                      but without any file extension.  If no DOCID
	 *                      is specified within the document text) as the
	 *                      base for the names of all entities, relations, events, etc.
	 *
	 *  @param sourceFile   The name of the source document.  This is used to fill
	 *                      the source file field of the AceDocument. If 'doc'
	 *                      is read from a file, should be the file name,
	 *                      including extension, but not including directory path.
	 *
	 *  @param docPathBase  The full path to the source document, with the file
	 *                      extension removed;  used to access corresponding parse
	 *                      and GLARF files.  Should be null if these files are not
	 *                      being used
	 *
	 *  @return             The AceDocument
	 */

	public static AceDocument processDocument (Document doc, String sourceId, String sourceFile,
		                                   String docPathBase) throws IOException {
		doc.stretchAll();
		// process document
		monocase = allLowerCase(doc);
		logger.trace ("Monocase is " + monocase);
		gazetteer.setMonocase(monocase);
		Jet.HMM.BigramHMMemitter.useBigrams = monocase;
		Jet.HMM.HMMstate.otherPreference = monocase ? 1.0 : 0.0;
		if (doc.annotationsOfType("dateline") == null && 
		    doc.annotationsOfType("textBreak") == null)
			SpecialZoner.findSpecialZones (doc);
		Control.processDocument (doc, null, docCount == -1, docCount);	// some tag is added, e.g., sentence, token, 
		if (parseDir != null && !parseDir.equals("-")) {
			// read in parses
			String parseFileName = parseDir + docPathBase + parseSuffix;
			try {
				File f = new File(parseFileName);
				PTBReader ptbReader = new PTBReader();
				List<ParseTreeNode> trees = ptbReader.loadParseTrees (f);
				List<Integer> offsets = ptbReader.getOffsets();
				ptbReader.addAnnotations (trees, offsets, doc, "sentence",  new Span(0, doc.text().length()), true);
			} catch (InvalidFormatException e) {
				logger.error ("Format error in reading parse tree from file " + parseFileName);
			} catch (IOException e) {
				logger.error ("IO error reading parse tree from file " + parseFileName);
				logger.error ("   " + e);
			}
			// resolve references
			Vector<Annotation> sentences = doc.annotationsOfType("sentence");
			if (sentences != null) {
				for (Annotation sentence : sentences) {
					AddSyntacticRelations.annotate(doc, sentence.span());
					Resolve.references (doc, sentence.span());
				}
			}
		}
		tagReciprocalRelations(doc); // assigns reciprocal relations subject-1 and object-1
		String docId = getDocId(doc);
		if (docId == null)
			docId = sourceId;
		sourceType = "text";
		Vector doctypes = doc.annotationsOfType("DOCTYPE");
		if (doctypes != null && doctypes.size() > 0) {
			Annotation doctype = (Annotation) doctypes.get(0);
			String source = (String) doctype.get("SOURCE");
			if (source != null)
				sourceType = source;
		}
		// create empty Ace document
		AceDocument aceDoc =
			new AceDocument(sourceFile, sourceType, docId, doc.text());
		// build entities
		buildAceEntities (doc, docId, aceDoc);
		// build TIMEX2 expressioms
		if (TimeMain.timeAnnotator != null)
			buildTimex (doc, aceDoc, docId);
		// build values
		if (FindAceValues.isDictLoaded())
			FindAceValues.buildAceValues (doc, docId, aceDoc);

		// build relations
		// ... either with kNN tagger
		if (eve != null) {
			LearnRelations.findRelations(docId, doc, aceDoc);
		// ... or with maxent tagger
		} else if (RelationTagger.model0 != null) {
			RelationTagger.findRelations(docId, doc, aceDoc);
		// ... or with dependency paths (ICE) tagger
		} else if (DepPathRelationTagger.model != null) {
			DepPathRelationTagger.findRelations(docId, doc, aceDoc);
		}

		// build events
		if (eventTagger != null)
			eventTagger.tag(doc, aceDoc, docPathBase, docId);
		// remove Crimes, Sentences, and Job-Titles not referenced by events
		if (FindAceValues.isDictLoaded())
			FindAceValues.pruneAceValues (aceDoc);
		return aceDoc;
	}

	/**
	 *  returns the document ID of Document <CODE>doc</CODE>, if found,
	 *  else returns <CODE>null</CODE>.  It looks for <br>
	 *  the text of a <B>DOCID</B> annotation (as provided with ACE documents); <br>
	 *  the text of a <B>DOCNO</B> annotation (as provided with TDT4 and TDT5 documents); <br>
	 *  the <B>DOCID</B> feature of a <B>document</B> annotation
	 *       (for GALE ASR transcripts); or <br>
	 *  the <B>id</B> feature of a <B>doc</B> annotation (for newer LDC documents).
	 */

	public static String getDocId (Document doc) {
		Vector docIdAnns = doc.annotationsOfType ("DOCID");
		if (docIdAnns != null && docIdAnns.size() > 0) {
			Annotation docIdAnn = (Annotation) docIdAnns.get(0);
			return doc.text(docIdAnn).trim();
		}
		docIdAnns = doc.annotationsOfType ("DOCNO");
		if (docIdAnns != null && docIdAnns.size() > 0) {
			Annotation docIdAnn = (Annotation) docIdAnns.get(0);
			return doc.text(docIdAnn).trim();
		}
		Vector docAnns = doc.annotationsOfType ("document");
		if (docAnns != null && docAnns.size() > 0) {
			Annotation docAnn = (Annotation) docAnns.get(0);
			Object docId = docAnn.get("DOCID");
			if (docId != null && docId instanceof String) {
				return (String) docId;
			}
		}
		docAnns = doc.annotationsOfType ("DOC");
		if (docAnns != null && docAnns.size() > 0) {
			Annotation docAnn = (Annotation) docAnns.get(0);
			Object docId = docAnn.get("id");
			if (docId != null && docId instanceof String) {
				return (String) docId;
			}
		}
		return null;
	}

	public static boolean allLowerCase (Document doc) {
		Vector<Annotation> textSegments = doc.annotationsOfType ("TEXT");
		Span span;
		if (textSegments != null && textSegments.size() > 0)
			span = textSegments.get(0).span();	//@di ? the span of doc = text to be processed
		else
			span = doc.fullSpan();
		return allLowerCase (doc, span);  //span may indicate the scope of the text to be processed in the document
		//in AFP_ENG_20030304.0250, span is 145-1322
	}

	static float MAX_UPPER = 0.50f;

	/**
	 *  return true if either all the letters in span are
	 *  lower case, or the fraction of letters which are upper case
	 *  exceeds MAX_UPPER.  Either condition is an indication that case
	 *  is not significant in detecting names or sentence boundaries.
	 */

	public static boolean allLowerCase (Document doc, Span span) {
		int countLower = 0;
		int countUpper = 0;
		for (int i=span.start(); i<span.end(); i++) {
			if (Character.isUpperCase(doc.charAt(i)))
				countUpper++;
			if (Character.isLowerCase(doc.charAt(i)))
				countLower++;
			}
		int totalLetters = countLower + countUpper;
		return (countUpper == 0) ||
		       (countUpper > totalLetters * MAX_UPPER);
	}

	static String[] functionWord =
		{"a ", "an ", "the ", "his ", "her ", "its ",
		 "against ", "as ", "at ", "by ", "due to ", "for ", "from ", "in ", 
		 "into ", "of ", "over ", "to ", "with ", "within ",
		 "and ", "not ", "or ",
		 "can ", "be ", "will "};
		 
	/**
	 *  returns true if Span <CODE>span</CODE> of Document <CODE>doc</CODE>
	 *  appears to be capitalized as a title:  if there are no words
	 *  beginning with a lower-case letter except for a small list of
	 *  function words (articles, possessive pronouns, prepositions, ...).
	 */

	public static boolean titleCase (Document doc, Span span) {
		boolean allTitle = true;
		String text = doc.text(span);
	loop:
		for (int i=0; i<text.length()-1; i++) {
			if (Character.isWhitespace(text.charAt(i)) &&
			    Character.isLowerCase(text.charAt(i+1))) {
				for (int j=0; j<functionWord.length; j++)
					if (text.startsWith(functionWord[j], i+1))
						continue loop;
				allTitle = false;
			}
		}
		return allTitle;
	}

	/**
	 *  returns a file path after removing the extension (period and following
	 *  characters), if any, from the file name.
	 */

	static String removeFileExtension (String path) {
		String fileName = (new File(path)).getName();
		int i = fileName.lastIndexOf('.');
		if (i > 0 && i < fileName.length()-1) {
			i+= path.length() - fileName.length();	//identify the length of path
			return path.substring(0, i);
		} else {
			return path;
		}
	}
	
	/**
	 *  create ACE entities from entity annotations produced by refres.
	 */

	public static void buildAceEntities (Document doc, String docId, AceDocument aceDoc) {
		aceEntityNo = 0;
		LearnRelations.resetMentions(); // for relations
		String docText = doc.text();
		Vector<Annotation> entities = doc.annotationsOfType("entity");
		if (entities != null) {
			for (int ientity=0; ientity<entities.size(); ientity++) {
				AceEntity aceEntity =
					buildEntity(entities.get(ientity), ientity, doc, docId, docText);
				if (aceEntity != null)
					aceDoc.addEntity(aceEntity);
			}
		}
		logger.info ("buildAceEntities: generated " + aceDoc.entities.size() + " entities");
	}

	/**
	 *  create an AceEntity from <CODE>entity</CODE>.  If the
	 *  entity is not a valid EDT type, nothing is written.
	 */

	private static AceEntity buildEntity (Annotation entity, int ientity,
			Document doc, String docId, String docText) {
		Vector mentions = (Vector) entity.get("mentions"); //entity mention
		Annotation firstMention = (Annotation) mentions.get(0);
		String aceTypeSubtype;
		aceTypeSubtype = EDTtype.getTypeSubtype (doc, entity, firstMention);
		String aceType = EDTtype.bareType(aceTypeSubtype);
		if (entityTrace)
			logger.trace ("Type of " + Resolve.normalizeName(doc.text(firstMention)) + " is " + aceTypeSubtype);
		if (aceType.equals("OTHER")) return null;
		String aceSubtype = EDTtype.subtype(aceTypeSubtype);
		// reinstate generic tagging for ACS
		// (had been suppressed for 2004 and 2005 ACE evaluations)
		// boolean generic = !AceDocument.ace2004 && isGeneric(doc, firstMention);
		boolean generic = isGeneric(doc, firstMention);

		if (generic) {
			logger.trace ("Identified generic mention " +
			                    Resolve.normalizeName(doc.text(firstMention)));
		}

		aceEntityNo++;
		if (entityTrace)
			logger.trace ("Generating ace entity " + aceEntityNo +
			                   " (internal entity " + ientity + ") = " +
			                   Resolve.normalizeName(doc.text(firstMention)) +
			                   " [" + aceType + "]");
		String entityID = docId + "-" + aceEntityNo;
		AceEntity aceEntity = new AceEntity (entityID, aceType, aceSubtype, generic);
		for (int imention=0; imention<mentions.size(); imention++) {
			Annotation mention = (Annotation) mentions.get(imention);
			Annotation head = Resolve.getHeadC(mention);
			String mentionID = entityID + "-" + imention;
			AceEntityMention aceMention = buildMention (mention, head, mentionID, aceType, doc, docText);
			aceEntity.addMention(aceMention);
			LearnRelations.addMention (aceMention);
			boolean isNameMention = aceMention.type == "NAME";
			if (isNameMention) {
				aceEntity.addName(new AceEntityName(head.span(), docText));
			}
		}
		return aceEntity;
	}

	static final String[] locativePrepositions = {"in", "at", "to", "near"};

	/**
	 *  write the information for <CODE>mention</CODE> with head <CODE>head</CODE>
	 *  in APF format.
	 */

	private static AceEntityMention buildMention
			(Annotation mention, Annotation head, String mentionID, String entityType,
				Document doc, String docText) {
		Span mentionSpan = mention.span();
		Span headSpan = head.span();
		String mentionType = mentionType(head, mention);
		AceEntityMention m =
			new AceEntityMention (mentionID, mentionType, mentionSpan, headSpan, docText);
		if (entityType.equals("GPE")) {
			if (perfectMentions)
				m.role = PerfectAce.getMentionRole(head);
			else {
				String prep = governingPreposition(doc, mention);
				if ((prep != null && in(prep, locativePrepositions)) ||
				     // for location in dateline
				     Resolve.sentenceSet.sentenceNumber(mention.start()) == 0) {
					m.role = "LOC";
				} else {
					m.role = "GPE";
				}
			}
		}
		return m;
	}

	/**
	 *  determine the mention type of a mention (NOMINAL, PRONOUN, or NAME)
	 *  from its <CODE>head</CODE>.
	 */

	private static String mentionType (Annotation head, Annotation mention) {
		if (perfectMentions)
			return PerfectAce.getMentionType (head);
		String cat = (String) head.get("cat");
		String mcat = (String) mention.get("cat");
		// if (mention.get("preName-1") != null || mention.get("nameMod-1") != null ||
		//     cat == "adj")
		// 	return "PRE";
		// else
		if (cat == "n" || cat == "title" || cat == "tv" || cat == "v")
			return "NOMINAL";
		else if (cat == "pro" || cat == "det" /*for possessives - his, its */ ||
			       cat == "adj" || cat == "ven" || cat == "q" ||
		         cat == "np" /* for headless np's */ || cat == "wp" || cat == "wp$")
			return "PRONOUN";
		else // cat == "name"
			if (mention.get("nameWithModifier") != null)
				return "NOMINAL";
			else
				return "NAME";
	}

	static final String[] genericFriendlyDeterminers =
		{"no", "neither", "any", "many", "every", "each"};
	static final String[] clearGenericPronouns =
		{"everyone", "anyone", "everybody", "anybody",
		"something", "who", "whoever", "whomever",
		"wherever", "whatever", "where"};

	private static boolean isGeneric (Document doc, Annotation mention) {
		Annotation ngHead = getNgHead (mention);
		Annotation headC = Resolve.getHeadC (mention);
		if (headC.get("cat") == "n") {
			// is always generic
			String det = SynFun.getDet(mention);
			if (det != null && in(det, genericFriendlyDeterminers))
				return true;
			// OR is generic head
			if (!EDTtype.hasGenericHead(doc, mention)) return false;
			// if (pa.get("number") != "plural") return false;
			if (ngHead.get("poss") != null || det == "poss") return false;
			if (ngHead.get("quant") != null || det == "q") return false;
			//    AND is in generic environment
			Annotation vg = governingVerbGroup(mention);
			if (vg != null) {
				FeatureSet vpa = (FeatureSet) vg.get("pa");
				if (vpa != null && vpa.get("tense") != "past"
				                && vpa.get("aspect") == null) {
				    logger.trace ("Governing verb group = " + doc.text(vg));
				    logger.trace ("Verb group pa = " + vpa);
				    return true;
				}
			}
			return false;
		} else if (headC.get("cat") == "pro" || headC.get("cat") == "np"
		                                     || headC.get("cat") == "det") {
			String pronoun = SynFun.getHead(doc, mention);
			return in(pronoun,clearGenericPronouns) ||
			       in(pronoun,genericFriendlyDeterminers);  // << added Oct. 10
		} else /* head is a name */ return false;
	}

	private static Annotation getNgHead (Annotation ng) {
		Annotation hd = ng;
		while (true) {
			ng = (Annotation) hd.get("headC");
			if (ng == null) return hd;
			if (ng.get("cat") != "np"  || ng.get("possPrefix") == "true") return hd;
			hd = ng;
		}
	}

	/**
	 *  tag the document <CODE>doc</CODE> for time expressions, and create
	 *  (and add to <CODE>aceDoc</CODE> an AceTimex object for each time
	 *  expression.
	 */

	private static void buildTimex (Document doc, AceDocument aceDoc, String docId) {
		TimeMain.processDocument (doc);
		Vector v = doc.annotationsOfType("TIMEX2");
		if (v != null) {
			logger.info ( v.size() + " time expressions found.");
			for (int i=0; i<v.size(); i++) {
				Annotation ann = (Annotation) v.get(i);
				String docText = doc.text();
				String timeId = docId + "-T" + i;
				String val = (String) ann.get("VAL");
				if (val == null)
					logger.warn ("TIMEX " + timeId + " has no VAL.");
				AceTimexMention mention =
					new AceTimexMention (timeId + "-1", ann.span(), docText);
				AceTimex timex = new AceTimex (timeId, val);
				timex.addMention(mention);
				aceDoc.addTimeExpression(timex);
			}
		}
	}

	private static boolean in (Object o, Object[] array) {
		for (int i=0; i<array.length; i++)
			// if (array[i] == o) return true;
			if (array[i] != null && array[i].equals(o)) return true;
		return false;
	}

	/**
	 *  assigns reciprocal relations subject-1 and object-1
	 */

	public static void tagReciprocalRelations (Document doc) {
		Vector constits = doc.annotationsOfType("constit");
		if (constits != null) {
			for (int j = 0; j < constits.size();  j++) {
				Annotation ann = (Annotation) constits.elementAt(j);
				if (ann.get("subject") != null) {
					Annotation subject = (Annotation) ann.get("subject");
					if (subject.get("subject-1") == null) {
						subject.put("subject-1", ann);
					}
				}
				if (ann.get("object") != null) {
					Annotation object = (Annotation) ann.get("object");
					if (object.get("object-1") == null) {
						object.put("object-1", ann);
					}
				}
			}
		}
	}

	static Annotation governingVerbGroup (Annotation ann) {
		Annotation governingConstituent;
		if (ann.get("subject-1") != null) {
			governingConstituent = (Annotation) ann.get("subject-1");
		} else if (ann.get("object-1") != null) {
			governingConstituent = (Annotation) ann.get("object-1");
		} else return null;
		return Resolve.getHeadC(governingConstituent);
	}

	static String governingPreposition (Document doc, Annotation ann) {
		Annotation pp = (Annotation) ann.get("p-obj-1");
		if (pp == null)
			return null;
		Annotation[] ppChildren = (Annotation[]) pp.get("children");
		if (ppChildren.length != 2)
			return null;
		Annotation in = ppChildren[0];
		String prep = doc.text(in).trim();
		return prep;
	}

	// retained for XNameTagger

	public static void setPatternSet (String fileName) throws IOException {
		eve = new RelationPatternSet();
		eve.load(fileName, 0);
	}

	// properties for Ace

	static {
	/* Ace.java: */		JetTest.validProperties.add("Ace.PerfectEntities");
				JetTest.validProperties.add("Ace.Value.fileName");
				JetTest.validProperties.add("Time.fileName");
				JetTest.validProperties.add("Ace.RelationPatterns.fileName");
				JetTest.validProperties.add("Ace.RelationModel.fileName");
				JetTest.validProperties.add("Ace.RelationDepPaths.fileName");
				JetTest.validProperties.add("Ace.Relations.tagSameSentence");
				JetTest.validProperties.add("Ace.EventModels.directory");
				JetTest.validProperties.add("Ace.writeEventConfidence");
				JetTest.validProperties.add("Ace.EventModels.eventProbabilityThreshold");
				JetTest.validProperties.add("Ace.EventModels.argumentProbabilityThreshold");
				JetTest.validProperties.add("Ace.Year");
	/* AceDocument.java: */	JetTest.validProperties.add("Ace.extendedAPF");
	/* EDTtype.java: */	JetTest.validProperties.add("Ace.EDTtype.fileName");
				JetTest.validProperties.add("Ace.generic.fileName");
	/* EventTagger.java: */	JetTest.validProperties.add("Ace.writeEventConfidence");
				JetTest.validProperties.add("Ace.EventModels.eventProbabilityThreshold");
				JetTest.validProperties.add("Ace.EventModels.argumentProbabilityThreshold");
	/* FindAceValues.java: */ JetTest.validProperties.add("Ace.Value.fileName");
	/* Gazetteer.java: */	JetTest.validProperties.add("Gazetteer.fileName");
	/* NameSubtyper.java: */ JetTest.validProperties.add("Ace.NameSubtypeModel.fileName");
	}

}
