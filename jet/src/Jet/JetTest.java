// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.8.0
//Description:  A Java-based Information Extraction Tool

package Jet;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.*;

import javax.swing.JFileChooser;
import javax.swing.JFrame;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import AceJet.Gazetteer;
import Jet.Chunk.Chunker;
import Jet.Chunk.MENameTagger;
import Jet.Chunk.Onoma;
import Jet.Concepts.ConceptHierarchy;
import Jet.HMM.BIOWriter;
import Jet.HMM.HMMNameTagger;
import Jet.HMM.HMMTagger;
import Jet.HMM.WordFeatureHMMemitter;
import Jet.Lex.EnglishLex;
import Jet.Lex.Lexicon;
import Jet.NE.NameAnnotator;
import Jet.NE.RuleFormatException;
import Jet.NE.TrieDictionary;
import Jet.Parser.Grammar;
import Jet.Parser.StatParser;
import Jet.Parser.DepParser;
import Jet.Pat.Pat;
import Jet.Pat.PatternCollection;
import Jet.Refres.CorefEval;
import Jet.Refres.EntityView;
import Jet.Refres.Resolve;
import Jet.Scorer.SGMLProcessor;
import Jet.Scorer.NameTagger;
import Jet.Time.NumberAnnotator;
import Jet.Time.TimeAnnotator;
import Jet.Tipster.AnnotationColor;
import Jet.Tipster.CollectionAnnotationTool;
import Jet.Tipster.CollectionView;
import Jet.Tipster.Document;
import Jet.Tipster.View;
import Jet.Util.IOUtils;

/**
 * contains the main method for Jet, and the methods for initial loading of
 * linguistic data (grammars, lexicons, patterns, etc.).
 */

public class JetTest {

	/**
	 *  the set of valid properties in a Jet properties file;
	 *  strings ending in "*" indicate that any property name
	 *  with the specified prefix is valid.
	 */
	static public Set<String> validProperties = new HashSet<String>();
	/**
	 * true to run in 'batch' mode, with no console display window
	 */
	static public boolean batchFlag = false;

	static protected Properties defaultConfig = new Properties();

	static protected Properties config = null;

	static private File configFile;

	static protected String dataPath;

	static public PatternCollection pc;

	static public File patternFile;

	static protected Grammar gram;

	static public HMMTagger tagger;

	static public NameTagger nameTagger;

	static protected Vector views = new Vector();

	static public ConceptHierarchy conceptHierarchy;

	static public File conceptHierarchyFile;

	static public String encoding = "ISO-8859-1"; // default: ISO-LATIN-1

	static public String docId = "";

	static public NameAnnotator extendedNameTagger = new NameAnnotator();

	private static TimeAnnotator timeAnnotator = new TimeAnnotator();

	private static DateTime referenceTime;

	private static DateTimeFormatter referenceTimeFormat;

	private static NumberAnnotator numberAnnotator = new NumberAnnotator();

	static {
		defaultConfig.put("processDocument", "tag(TEXT), TEXT:processTextZone");
		defaultConfig.put("processTextZone", "sentenceSplit, sentence:processSentence");
		config = new Properties(defaultConfig);
	}

	/**
	 * read Jet parameter file from file <CODE>configFile</CODE> and load all
	 * specified resources.
	 */

	public static void initializeFromConfig(File configFile, String jetHome) {
		InputStream in = null;
		try {
			in = new FileInputStream(configFile);
			config.load(in);
			batchFlag = config.getProperty("Jet.batch") != null;
			String dataPathProperty = config.getProperty("Jet.dataPath");
			if (dataPathProperty == null)
				if (jetHome == null || jetHome.equals(""))
					dataPath = ".";
				else
					dataPath = jetHome;
			else
				if (jetHome == null || jetHome.equals("") || new File(dataPathProperty).isAbsolute())
					dataPath = dataPathProperty;
				else
					dataPath = jetHome + File.separatorChar + dataPathProperty;
			if (!batchFlag) {
				new AnnotationColor(dataPath);
			}
			initialize();
			System.err.println("Jet Ver. 1.8.1.  Portions (c) 1999-2015 R. Grishman");
			JarDate.print(System.err);
			System.err.println("Licensed under Apache License, Version 2.0.");
			validateConfig (config);
		} catch (IOException ioe) {
			System.err.println("Error: could not open file " + configFile);
			System.err.println("USAGE: JetTest <configFile>");
			System.exit(1);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}
	static {validProperties.add("Jet.batch");}
	static {validProperties.add("Jet.dataPath");}

	/**
	 *  checks whether all entries in 'config' specify
	 *  valid Jet properties;  if not, issues a message
	 *  to standard error.
	 */

	static void validateConfig (Properties config) {
		Iterator iter = config.keySet().iterator();
	    lp:	while (iter.hasNext()) {
			String pname = (String) iter.next();
			if (validProperties.contains(pname))
				continue lp;
			for (String s : validProperties)
				if (s.endsWith("*") && pname.startsWith(s.substring(0, s.length()-1)))
					continue lp;
			System.err.println ("*** Invalid property " + pname + " in properties file.");
		}
	}

	public static void initializeFromConfig(String configFile, String jetHome) {
		initializeFromConfig(new File(configFile), jetHome);
	}

	public static void initializeFromConfig(File configFile) {
		initializeFromConfig(configFile, System.getProperty("jetHome"));
	}

	public static void initializeFromConfig(String configFile) {
		initializeFromConfig(configFile, System.getProperty("jetHome"));
	}

	/**
	 * return property 'property' from the configuration file.
	 */

	public static String getConfig(String property) {
		String value = config.getProperty(property);
		if (value == null)
			return null;
		return value.trim();
	}

	/**
	 * return file name 'property' from the configuration file; if the value is
	 * an absolute path or one beginning "./", return the value of the property;
	 * if it is a relative path, prefix it with the value of 'Jet.dataPath' from the
	 * configuration file.
	 */

	public static String getConfigFile(String property) {
		String fileName = config.getProperty(property);
		if (fileName == null) {
			return null;
		} else if (fileName.startsWith("./") || new File(fileName).isAbsolute()) {
			return fileName;
		} else {
			return dataPath + File.separatorChar + fileName;
		}
	}

	/**
	 * load all resources (lexicon, HMMs, patterns, grammar, concepts) as
	 * specified in the .properties file.
	 */

	public static void initialize() {
		if (config.getProperty("Jet.encoding") != null)
			setEncoding (config.getProperty("Jet.encoding"));
		readLexicons();
		readTags();
		readNameTags();
		readPatterns();
		readGrammar();
		readChunkModel();
		if (config.getProperty("StatParser.grammar.fileName") != null)
			StatParser.initialize(dataPath, config);
		Resolve.readGenderDict(dataPath, config);
		DepParser.initialize(dataPath, config);
		readConcepts();
		readGazetteer();
		readENEData();
		initTimex();
		String trace = config.getProperty("Resolve.trace");
		if (trace != null) {
			if (trace.equals("on"))
				Resolve.trace = true;
			else if (trace.equals("off"))
				Resolve.trace = false;
			else
				System.err.println("*** Invalid value " + trace + " for Resolve.trace "
								   + "(should be 'on' or 'off')");
		}
		Resolve.useMaxEnt = config.getProperty("Resolve.useMaxEnt") != null;
	}
	static {validProperties.add("Jet.encoding");}
	static {validProperties.add("DepParser.model.fileName");}
	static {validProperties.add("DepParser.transformations");}
	static {validProperties.add("StatParser.grammar.fileName");}
	static {validProperties.add("StatParser.properties.fileName");}
	static {validProperties.add("Resolve.trace");}
	static {validProperties.add("Resolve.useMaxEnt");}
	static {validProperties.add("NameGender.fileName");}

	/**
	 * load all lexicons specified by parameters beginning with the string
	 * <CODE>EnglishLex.fileName</CODE>
	 */

	public static void readLexicons() {
		Lexicon.clear();
		// read in lexicons
		Iterator iter = config.keySet().iterator();
		while (iter.hasNext()) {
			String propName = (String) iter.next();
			if (propName.startsWith("EnglishLex.fileName")) {
				String fileName = getConfigFile(propName);
				try {
					EnglishLex.readLexicon(fileName);
				} catch (IOException ioe) {
					System.err.println("Error: reading lexicon file " + fileName + ", "
							+ ioe.getMessage());
				}
			}
		}
	}
	static {validProperties.add("EnglishLex.fileName*");}

	/**
	 * if the parameter <CODE>Tags.fileName</CODE> is set, load a
	 * part-of-speech HMM from that file.
	 */

	public static void readTags() {
		String fileName = getConfigFile("Tags.fileName");
		if (fileName != null) {
			try {
				tagger = new HMMTagger();
				tagger.load(fileName);
			} catch (IOException ioe) {
				System.err.println("Error: reading tag file " + fileName + ", " + ioe.getMessage());
			}
		}
	}
	static {validProperties.add("Tags.fileName*");}

	/**
	 * if the parameter <CODE>NameTags.fileName</CODE> is set, load a name
	 * tagger HMM from that file. If parameter <CODE>NameTags.emitter</CODE>
	 * is set, use the value of that parameter as the name of the emitter class
	 * for the HMM, otherwise used WordFeatureHMMemitter as the default.
	 */

	public static void readNameTags() {
		String hmmFileName = getConfigFile("NameTags.fileName");
		String meFileName = getConfigFile("NameTags.ME.fileName");
		if (hmmFileName != null) {
			try {
				HMMNameTagger hmmNameTagger;
				String emitter = config.getProperty("NameTags.emitter",
						"Jet.HMM.WordFeatureHMMemitter");
				Class emitterClass;
				try {
					emitterClass = Class.forName(emitter);
				} catch (ClassNotFoundException e) {
					System.err.println("Unknown HMM emitter class " + emitter);
					System.err.println("Using class WordFeatureHMMemitter");
					emitterClass = WordFeatureHMMemitter.class;
				}
				hmmNameTagger = new HMMNameTagger(emitterClass);
				hmmNameTagger.load(hmmFileName);
				if (config.getProperty("NameTags.trace") != null)
					hmmNameTagger.annotator.setTrace(true);
				if (config.getProperty("NameTags.recordMargin") != null)
					hmmNameTagger.annotator.setRecordMargin(true);
				nameTagger = hmmNameTagger;
			} catch (IOException ioe) {
				System.err.println("Error: reading name tag file " + hmmFileName + ", "
						+ ioe.getMessage());
			}
		} else if (meFileName != null) {
			try {
				nameTagger = new MENameTagger();
				nameTagger.load(meFileName);
			} catch (IOException ioe) {
				System.err.println("Error: reading name tag file " + meFileName + ", "
						+ ioe.getMessage());
			}
			String onoma = getConfigFile("Onoma.fileName");
			try {
				if (onoma != null)
					Onoma.read (onoma);
			} catch (IOException ioe) {
				System.err.println("Error: reading onoma file " + onoma + ", "
						+ ioe.getMessage());
			}
		}
		String wordClusters = getConfigFile("WordClusters.fileName");
		if (wordClusters != null) {
			try {
				Jet.Chunk.MaxEntNE.loadWordClusters(wordClusters);
			} catch (IOException ioe) {
				System.err.println("Error: reading cluster file " + wordClusters + ", "
								   + ioe.getMessage());
			}
		}
	}
	static {validProperties.add("NameTags.fileName");}
	static {validProperties.add("NameTags.emitter");}
	static {validProperties.add("NameTags.recordMargin");}
	static {validProperties.add("NameTags.trace");}
	static {validProperties.add("NameTags.ME.fileName");}
	static {validProperties.add("Onoma.fileName");}
	static {validProperties.add("WordClusters.fileName");}

	/**
	 * load all pattern files specified by parameters beginning with the string
	 * <CODE>Pattern.fileName</CODE> and set the pattern trace switch if the
	 * <CODE>Pattern.trace</CODE> switch is set.
	 */

	public static void readPatterns() {
		pc = new PatternCollection();
		// read in patterns
		Iterator iter = config.keySet().iterator();
		while (iter.hasNext()) {
			String propName = (String) iter.next();
			if (propName.startsWith("Pattern.fileName")) {
				String fileName = getConfigFile(propName);
				try {
					patternFile = new File(fileName);
					pc.readPatternCollection(new BufferedReader(new FileReader(patternFile)));
				} catch (IOException ioe) {
					System.err.println("Error: reading pattern file " + fileName + ", "
							+ ioe.getMessage());
				}
			}
		}
		pc.makePatternGraph();
		String trace = config.getProperty("Pattern.trace");
		if (trace != null) {
			if (trace.equals("on"))
				Pat.trace = true;
			else if (trace.equals("off"))
				Pat.trace = false;
			else
				System.err.println("*** Invalid value " + trace + " for Pattern.trace "
								   + "(should be 'on' or 'off')");
		}
	}
	static {validProperties.add("Pattern.fileName*");}
	static {validProperties.add("Pattern.trace");}

	/**
	 * if the parameter <CODE>Grammar.fileName</CODE> is set, load a grammar
	 * from that file.
	 */

	public static void readGrammar() {
		String fileName = getConfigFile("Grammar.fileName");
		if (fileName != null) {
			try {
				gram = new Grammar(new BufferedReader(new FileReader(fileName)));
			} catch (IOException ioe) {
				System.err.println("Error: reading grammar file " + fileName + ", "
						+ ioe.getMessage());
			}
		}
	}
	static {validProperties.add("Grammar.fileName");}

	/**
	 * if the parameter <CODE>Chunker.fileName</CODE> is set, load a chunk
	 * model from that file.
	 */

	public static void readChunkModel() {
		String fileName = getConfigFile("Chunker.fileName");
		if (fileName != null) {
			Chunker.loadModel(fileName);
		}
	}
	static {validProperties.add("Chunker.fileName");}

	/**
	 * if the parameter <CODE>Concepts.fileName</CODE> is set, load a concept
	 * hierarchy from that file.
	 */

	public static void readConcepts() {
		String fileName = getConfigFile("Concepts.fileName");
		if (fileName == null) {
			conceptHierarchyFile = null;
			conceptHierarchy = new ConceptHierarchy();
		} else {
			conceptHierarchyFile = new File(fileName);
			conceptHierarchy = new ConceptHierarchy(conceptHierarchyFile);
		}
	}
	static {validProperties.add("Concepts.fileName");}

	/**
	 * if the parameter <CODE>Gazetteer.fileName</CODE> is set, load a
	 * gazetteer.
	 */

	public static void readGazetteer() {
		if (getConfigFile("Gazetteer.fileName") != null) {
			try {
				AceJet.Ace.gazetteer = new Gazetteer();
				AceJet.Ace.gazetteer.load();
			} catch (IOException ioe) {
				System.err.println("Error reading gazetteer: " + ioe.getMessage());
			}
		}
	}
	static {validProperties.add("Gazetteer.fileName");}

	public static void readENEData() {
		String trie = config.getProperty("ENE.dict.trie");
		String cdb = config.getProperty("ENE.dict.cdb");

		if (trie != null && cdb != null) {
			try {
				File trieFile = new File(dataPath, trie);
				File cdbFile = new File(dataPath, cdb);
				TrieDictionary dict = new TrieDictionary(trieFile.getPath(), cdbFile.getPath());
				extendedNameTagger.setDictionary(dict);
			} catch (IOException ex) {
				System.err.println("Error reading ENE dictionary: " + ex.getMessage());
			}
		}

		String rule = config.getProperty("ENE.rule");
		if (rule != null) {
			File ruleFile = new File(dataPath, rule);
			Reader in = null;
			try {
				Charset cs = Charset.forName(encoding);
				in = new BufferedReader(new InputStreamReader(new FileInputStream(ruleFile), cs));
				extendedNameTagger.loadRules(in);
			} catch (IOException ex) {
				System.err.println("Error reading rule file: " + ex.getMessage());
			} catch (RuleFormatException ex) {
				System.err.println("Rule format error: " + ex.getMessage());
			} finally {
				IOUtils.closeQuietly(in);
			}
		}
	}
	static {validProperties.add("ENE.dict.trie");}
	static {validProperties.add("ENE.dict.cdb");}
	static {validProperties.add("ENE.dict.rule");}

	/**
	 * Initialize parameters for annotating TIMEX.
	 */
	public static void initTimex() {
		String ruleFile = config.getProperty("Timex.rule");
		String refTime = config.getProperty("Timex.refTime");
		String refFormat = config.getProperty("Timex.refFormat");

		if (ruleFile != null) {
			File file = new File(dataPath, ruleFile);
			InputStream in = null;
			try {
				in = new FileInputStream(file);
				timeAnnotator.load(in);
			} catch (IOException ex) {
				System.err.println("Error reading rule file: " + ex.getMessage());
			} finally {
				IOUtils.closeQuietly(in);
			}
		}

		if (refTime != null) {
			DateTimeFormatter formatter = ISODateTimeFormat.dateTimeParser();
			referenceTime = formatter.parseDateTime(refTime);
		}

		if (refFormat != null) {
			referenceTimeFormat = DateTimeFormat.forPattern(refFormat);
		}
	}
	static {validProperties.add("Timex.rule");}
	static {validProperties.add("Timex.refTime");}
	static {validProperties.add("Timex.refFormat");}

	/**
	 * Returns reference time for annotating TIMEX.
	 */
	public static DateTime getReferenceTime() {
		return referenceTime;
	}

	/**
	 * Sets reference time for annotating TIMEX.
	 */
	public static void setReferenceTime(DateTime reference) {
		referenceTime = reference;
	}

	/**
	 * Returns format of reference time.
	 */
	public static DateTimeFormatter getReferenceTimeFormat() {
		return referenceTimeFormat;
	}

	/**
	 * Returns instance of <code>Jet.Time.TimeAnnotator</code> for annotating
	 * TIMEX.
	 */
	public static TimeAnnotator getTimeAnnotator() {
		return timeAnnotator;
	}

	/**
	 * Returns instance of <code>Jet.Time.NumberAnnotator</code> for
	 * annotating number expression.
	 */
	public static NumberAnnotator getNumberAnnotator() {
		return numberAnnotator;
	}

	/**
	 * process all the documents on file <CODE>inputFileName</CODE>,
	 * displaying the results in View windows if <CODE>viewable</CODE> is
	 * true. The file should contain one or more documents, each beginning with
	 * a line &lt;DOC> and ending with a line &lt;/DOC> .
	 */

	public static void processFile(String inputFileName, boolean viewable) throws IOException {
		long startTime = System.currentTimeMillis();
		File inputFile;
		if (inputFileName.startsWith("./") || 
		    new File(inputFileName).isAbsolute())
			inputFile = new File(inputFileName);
		else
			inputFile = new File(dataPath, inputFileName);
		BufferedReader rdr = null;
		BufferedWriter writer = null;
		try {
			Charset cs = Charset.forName(encoding);
			rdr = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), cs));
			if (JetTest.config.getProperty("WriteSGML.type") != null) {
				File outputFile = new File(inputFile.getParent(), "response-" + inputFile.getName());
				writer = new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream(outputFile), encoding));
			}
			Document doc;
			int docNo = 0;
			while ((doc = readDocument(rdr)) != null) {
				String readSGML = config.getProperty("ReadSGML.type");
				if (readSGML != null) {
					String[] readSGMLtypes = readSGML.split("\\s*,\\s*");
					doc = SGMLProcessor.sgmlToDoc(doc.text(), readSGMLtypes);
					doc.stretchAll();
				}
				Control.processDocument(doc, writer, viewable, ++docNo);
			}
			if (writer != null)
				writer.close();
		} finally {
			IOUtils.closeQuietly(rdr);
			IOUtils.closeQuietly(writer);
		}
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;
		System.err.println("Processing of file " + inputFileName + " took " + duration + " ms");
	}
	static {validProperties.add("process*");}
	static {validProperties.add("WriteSGML.type");}
	static {validProperties.add("ReadSGML.type");}

	/**
	 * for each Jet parameter beginning <CODE>JetTest.fileName</CODE>, invoke
	 * <CODE>processFile</CODE> on that file of documents.
	 */

	public static void processFiles(boolean viewable) {
		// iterate through all the properties
		Iterator iter = config.keySet().iterator();
		while (iter.hasNext()) {
			String propName = (String) iter.next();
			if (propName.startsWith("JetTest.fileName")) {
				String inputFileName = config.getProperty(propName);
				try {
					processFile(inputFileName, viewable);
				} catch (IOException ioe) {
					System.err.println("Error: could not process document file " + inputFileName
							+ ", " + ioe.getMessage());
				}
			}
		}
	}
	static {validProperties.add("JetTest.fileName*");}

	public static void closeAllViews() {
		for (int i = 0; i < views.size(); i++) {
			((View) views.get(i)).dispose();
		}
		views.clear();
	}

	/**
	 * read a document from <CODE>rdr</CODE>. The document begins with a line
	 * &lt;DOC> and ends with a line &lt;/DOC> .
	 *
	 * @return a Document with no annotations
	 */

	public static Document readDocument(BufferedReader rdr) throws IOException {
		String line;
		do {
			line = rdr.readLine();
			if (line == null)
				return null;
		} while (!line.startsWith("<DOC"));
		Document doc = new Document();
		doc.append(line);
		doc.append("\n");
		int idOffset = line.indexOf("id=\"");
		if (idOffset > 0) {
			idOffset += 4;
			int idEndOffset = line.indexOf("\"", idOffset);
			if (idEndOffset > 0) {
				docId = line.substring(idOffset, idEndOffset);
			}
		}
		do {
			line = rdr.readLine();
			if (line == null) {
				System.err.println("no </DOC>");
				return null;
			}
			if (line.startsWith("<DOCID>") && line.trim().endsWith("</DOCID>")) {
				String docIdLine = line.trim();
				docId = docIdLine.substring(7, docIdLine.length() - 8).trim();
			}
			doc.append(line);
			doc.append("\n");
		} while (!line.trim().equals("</DOC>"));
		return doc;
	}

	/**
	 * set the character set used for reading external documents. If a valid
	 * character set is specified, return true. If the argument is not valid,
	 * print a list of the valid character sets and return false.
	 */

	public static boolean setEncoding(String charset) {
		try {
			if (Charset.isSupported(charset)) {
				encoding = charset;
				return true;
			} else {
				System.err.println("Character set " + charset + " is not supported.");
			}
		} catch (IllegalCharsetNameException e) {
			System.err.println(charset + " is an illegal character set name.");
		}
		SortedMap allSets = Charset.availableCharsets();
		Iterator it = allSets.keySet().iterator();
		System.err.println("Supported character sets are:");
		while (it.hasNext()) {
			String setName = (String) it.next();
			Charset c = (Charset) allSets.get(setName);
			System.err.println("  " + setName + " " + c.aliases());
		}
		System.err.println(" ");
		return false;
	}

	/**
	 * returns true if this program is connected to a terminal which can display
	 * windows. The test is performed by seeing if we can create a Jframe.
	 */

	static boolean hasDisplay() {
		try {
			new JFrame();
			return true;
		} catch (InternalError e) {
			return false;
		}
	}

	public static void main(String[] args) {
		if (args.length > 0) {
			String task = args[0];
			if (task.length() > 0 && task.charAt(0) == '-') {
				if (task.equals("-AnnotateCollection")) {
					CollectionAnnotationTool.task(args);
					return;
				} else if (task.equals("-CorefEval")) {
					CorefEval.task(args);
					System.exit(0);
				} else if (task.equals("-BIOWriter")) {
					BIOWriter.task(args);
					System.exit(0);
				} else if (task.equals("-CorefView")) {
					EntityView.task(args);
					return;
				} else if (task.equals("-CollectionView")) {
					CollectionView.task(args);
					return;
				} else {
					System.err.println("Unknown Jet task: " + task);
					System.exit(1);
				}
			}
		}

		if (args.length == 0) {
			if (!hasDisplay()) {
				System.err.println("Error:  no configuration file selected.");
				System.err.println("USAGE: JetTest <configFile>");
				System.exit(1);
			}
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Select Jet configuration file");
			File propsFile = new File(System.getProperty("user.dir"), "props");
			chooser.setCurrentDirectory(propsFile);
			int option = chooser.showOpenDialog(null);
			if (option == JFileChooser.APPROVE_OPTION) {
				configFile = chooser.getSelectedFile();
			} else {
				System.err.println("Error:  no configuration file selected.");
				System.exit(1);
			}
		} else {
			configFile = new File(args[0]);
			if (args.length > 1)
				System.err.println("Warning:  extra command line arguments ignored.");
		}
		initializeFromConfig(configFile);
		if (batchFlag)
			processFiles(false);
		else
			new Jet.Console();
	}
}
