// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2008
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
import opennlp.model.*;

/**
 *  contains top-level methods for training EventTagger.
 */
 
public class TrainEventTagger {
	
	static final String eventFeatureFileName =  "eventFeatureFile.log";
	static final String eventModelFileName =  "eventModel.log";
	// static final String evTypeFeatureFileName = "evTypeFeatureFile.log";
	// static final String evTypeModelFileName = "evTypeModel.log";

	static final String corefFeatureFileName = "corefFeatureFile.log";
	static final String corefModelFileName = "corefModel.log";

	static final String argFeatureFileName = "argFeatureFile.log";
	static final String argModelFileName = "argModel.log";
	static final String roleFeatureFileName = "roleFeatureFile.log";
	static final String roleModelFileName = "roleModel.log";

	static final String argFeatureHalfFileName = "argFeatureHalfFile.log";
	static final String argModelHalfFileName = "argModelHalf.log";
	static final String roleFeatureHalfFileName = "roleFeatureHalfFile.log";
	static final String roleModelHalfFileName = "roleModelHalf.log";

	static final String eventPatternFile = "eventPatterns.log";
	static final String eventReportFile = "eventPatternReport.log";
	static final String halfEventPatternFile = "eventPatternsHalf.log";
	
	static String fileListTrain;
	static String docDir;
	static String outputDir;

	static boolean processOnlyOddDocuments = false;
	static boolean processOnlyEvenDocuments = false;

	/**
	 *  train an ACE event tagger from a set of documents with APF files.
	 *  Takes the following arguments:
	 *  <ul>
	 *  <li> props:  Jet property file
	 *  <li> fileList:  list of files to be processed (one per line)
	 *  <li> docDir:    directory containing text and APF files
	 *  <li> outDir:    directory to receive all temporary and output files
	 *                  (event pattern file, event and argument models, etc.)
	 *  <li> glarfDir:  directory containing glarf triples (optional)
	 *  <li> glarfSuffix:  file extension for glarf files
	 *  </ul>
	 *  Training is done in four passes over the training corpus:
	 *  <ul>
	 *  <li> collect patterns (trigger words and arguments)
	 *  <li> count how often each pattern does/does not indicate an event
	 *  <li> build event model
	 *  <li> build event coreference model
	 *  </ul>
	 */

	public static void main (String[] args) throws IOException {
		System.out.println("Starting ACE event tagger training procedure.");
		if (args.length != 4 && args.length != 6) {
			System.out.println ("EventTagger must take 4 or 6 arguments:");
			System.out.println ("    properties filelist documentDir outputDir" +
			                    " [glarfDir glarfSuffix]");
			System.exit(1);
		}
		String propertyFile = args[0];
		fileListTrain = args[1];
		docDir = args[2];
		if (!docDir.endsWith("/")) docDir += "/";
		EventTagger.docDir = docDir;
		outputDir = args[3];
		if (!outputDir.endsWith("/")) outputDir += "/";
		EventTagger.glarfDir = null;
		if (args.length == 6) { 
			EventTagger.glarfDir = args[4];
			if (!EventTagger.glarfDir.endsWith("/")) EventTagger.glarfDir += "/";
			EventTagger.triplesSuffix = args[5];
			EventTagger.usePA = true;
		}

		// initialize Jet
		JetTest.initializeFromConfig (propertyFile);
		Pat.trace = false;
		Resolve.trace = false;
		AceDocument.ace2005 = true;
		// collect patterns and build argument models
		EventTagger et = new EventTagger();
		et.argFeatureWriter = new PrintStream (new FileOutputStream (outputDir + argFeatureFileName));
		et.roleFeatureWriter = new PrintStream (new FileOutputStream (outputDir + roleFeatureFileName));
		System.out.println ("\n===== Acquiring patterns and training argument model. =====\n");
		train (et, fileListTrain, 0);
		train (et, fileListTrain, 1);
		et.report (outputDir + eventReportFile);
		et.argFeatureWriter.close();
		et.roleFeatureWriter.close();
		buildClassifierModel(outputDir + argFeatureFileName, outputDir + argModelFileName);
		buildClassifierModel(outputDir + roleFeatureFileName, outputDir + roleModelFileName);
		et.save (outputDir + eventPatternFile);
		System.out.println ("\n===== Training event model. =====\n");
		trainEventModel (et);
		System.out.println ("\n===== Training coreference model. =====\n");
		trainCorefModel (et);
	}

	/**
	 *  train the event model -- the classifier which decides whether to actually report an event.
	 *  <p>
	 *  The event model is supposed to help in the case of gaps in the pattern sets, so the
	 *  training is a bit complicated.  We build a pattern set and arg and role models from
	 *  half the training corpus (the odd documents), and then apply these models to
	 *  the other half of the training corpus (the even documents), and then train the
	 *  event model using the results of these models combined with the true information
	 *  about the presence of events.
	 */

	private static void trainEventModel (EventTagger et) throws IOException {
		processOnlyOddDocuments = true;
		// set arg models
		et.argFeatureWriter = new PrintStream (new FileOutputStream (outputDir + argFeatureHalfFileName));
		et.roleFeatureWriter = new PrintStream (new FileOutputStream (outputDir + roleFeatureHalfFileName));
		// train patterns on odd documents
		train (et, fileListTrain, 0);
		train (et, fileListTrain, 1);
		et.save (outputDir + halfEventPatternFile);
		// build arg models (half) and load them
		et.argFeatureWriter.close();
		et.roleFeatureWriter.close();
		buildClassifierModel(outputDir + argFeatureHalfFileName, outputDir + argModelHalfFileName);
		buildClassifierModel(outputDir + roleFeatureHalfFileName, outputDir + roleModelHalfFileName);
		EventTagger.argModel = EventTagger.loadClassifierModel(outputDir + argModelFileName); // << 2013
		EventTagger.roleModel = EventTagger.loadClassifierModel(outputDir + roleModelFileName); // << 2013
		// set ev model
		et.eventFeatureWriter = new PrintStream (new FileOutputStream (outputDir + eventFeatureFileName));
		// et.evTypeFeatureWriter = new PrintStream (new FileOutputStream (evTypeFeatureFileName));
		// train event model on even documents
		processOnlyOddDocuments = false;
		// processOnlyEvenDocuments = true; << 2013
		train (et, fileListTrain, 2);
		// build ev model
		et.eventFeatureWriter.close();
		// et.evTypeFeatureWriter.close();
		buildClassifierModel(outputDir + eventFeatureFileName, outputDir + eventModelFileName);
		// buildClassifierModel(evTypeFeatureFileName, evTypeModelFileName);
	}

	private static void trainCorefModel (EventTagger et) throws IOException {
		EventTagger.argModel = EventTagger.loadClassifierModel(outputDir + argModelHalfFileName);
		EventTagger.roleModel = EventTagger.loadClassifierModel(outputDir + roleModelHalfFileName);
		EventTagger.eventModel = EventTagger.loadClassifierModel(outputDir + eventModelFileName);
		et.load (outputDir + halfEventPatternFile);
		// set coref model
		et.corefFeatureWriter = new PrintStream (new FileOutputStream (outputDir + corefFeatureFileName));
		// train coref model on even documents, using models from odd documents
		processOnlyEvenDocuments = true;
		train (et, fileListTrain, 3);
		// build coref model
		et.corefFeatureWriter.close();
		buildClassifierModel(outputDir + corefFeatureFileName, outputDir + corefModelFileName);
	}
	
	/**
	 *  trains an event tagger from a set of text and APF files.
	 *  @param fileList  a list of text file names, one per line.
	 *                   The APF file names are obtained by replacing
	 *                   'sgm' by 'apf.xml'.
	 */

	public static void train (EventTagger et, String fileList, int pass) throws IOException {
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		String currentDocPath;
		while ((currentDocPath = reader.readLine()) != null) {
			docCount++;
			// if (docCount > 10) break;
			if (processOnlyOddDocuments && docCount%2 == 0) continue;
			if (processOnlyEvenDocuments && docCount%2 == 1) continue;
			System.out.println ("\nProcessing file " + currentDocPath);
			String textFile = docDir + currentDocPath + ".sgm";
			String xmlFile = docDir + currentDocPath + ".apf.xml";
			ExternalDocument doc = new ExternalDocument("sgml", textFile);
			doc.setAllTags(true);
			doc.open();
			doc.stretchAll();
			Resolve.ACE = true;
			Ace.monocase = Ace.allLowerCase(doc);
			Control.processDocument (doc, null, false, 0);
			AceDocument aceDoc = new AceDocument(textFile, xmlFile);
			if (pass == 0)
				et.acquirePatterns (doc, aceDoc, currentDocPath);
			else if (pass == 1)
				et.evaluatePatterns (doc, aceDoc, currentDocPath);
			else if (pass == 2)
				et.trainEventModel (doc, aceDoc, currentDocPath);
			else /* pass == 3 */
				et.trainCorefModel (doc, aceDoc, currentDocPath);
		}
		reader.close();
	}

	static private void buildClassifierModel (String featureFileName, String modelFileName) {
		boolean USE_SMOOTHING = false;
		boolean PRINT_MESSAGES = true;
		double SMOOTHING_OBSERVATION = 0.1;
		try {
			FileReader datafr = new FileReader(new File(featureFileName));
			EventStream es = new BasicEventStream(new PlainTextByLineDataStream(datafr));
			GIS.SMOOTHING_OBSERVATION = SMOOTHING_OBSERVATION;
			GISModel model = GIS.trainModel(es, 100, 4, USE_SMOOTHING, PRINT_MESSAGES);

			File outputFile = new File(modelFileName);
			GISModelWriter writer = new SuffixSensitiveGISModelWriter(model, outputFile);
			writer.persist();
		} catch (Exception e) {
			System.err.print("Unable to create model due to exception: ");
			System.err.println(e);
		}
	}

}
