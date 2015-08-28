// -*- tab-width: 4 -*-
package AceJet;

//Author:       Ralph Grishman
//Date:         July 10, 2004

import java.util.*;
import java.io.*;
import opennlp.maxent.*;
import opennlp.maxent.io.*;
import opennlp.model.*;

import Jet.JetTest;
import Jet.Tipster.*;

/**
 *  methods to determine the ACE subclass of a name.  Uses a maximum entropy
 *  model where the features are the tokens of the name.
 */

public class NameSubtyper {

	static PrintStream writer;
	static String featureFile;
	static String modelFile;
	static GISModel model;

	/**
	 *  train a subclass model from the ACE training data.  Takes 2n+3
	 *  arguments:
	 *  <ul>
	 *  <li>  year:  2004 or 2005 -- determines format of APF file
	 *  <li>  feature-file:  intermediate file for MAXENT features
	 *  <li>  model-file:    MAXENT model file
	 *  <li>  apf-Directory-1:  directory containing APF files
	 *  <li>  fileList-1:    file with list of document names
	 *  <li>  apf-Directory-2:
	 *  <li>  fileList-2:
	 *  <li>  ...
	 *  <ul>
	 */

	public static void main (String[] args) throws IOException {
		if (args.length < 5 || (args.length % 2) == 0) {
			System.err.println ("NameSubtyper requires 3+2n arguments");
			System.err.println ("  year feature-file model-file (apfDirectory apfFileList)+");
			System.exit (1);
		}
		String year = args[0];
		if (year.equals("2004")) {
			AceDocument.ace2004 = true;
			AceDocument.ace2005 = false;
		} else if (year.equals("2005")) {
			AceDocument.ace2004 = true;
			AceDocument.ace2005 = true;
		} else {
			System.err.println ("Invalid year (must be 2004 or 2005)");
			System.exit (1);
		}
		featureFile = args[1];
		modelFile = args[2];
		writer = new PrintStream (new FileOutputStream (featureFile));
		for (int iarg=3; iarg < args.length; iarg+=2)
			processFileList (args[iarg], args[iarg+1]);
		createModel ();
		store (modelFile);
		System.out.println ("Finished.");
	}

	private static void processFileList (String directory, String fileList) throws IOException {
		if (!directory.endsWith("/")) directory += "/";
		// open list of files
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		String currentDoc;
		while ((currentDoc = reader.readLine()) != null) {
			// process file 'currentDoc'
			docCount++;
			System.out.println ("\nProcessing document " + docCount + ": " + currentDoc);
			String textFileName = directory + currentDoc + ".sgm";
			String APFfileName = directory + currentDoc + ".apf.xml";
			analyzeDocument (textFileName, APFfileName);
		}
	}

	private static void analyzeDocument (String textFileName, String APFfileName) {
		AceDocument aceDoc = new AceDocument (textFileName, APFfileName);

		ArrayList entities = aceDoc.entities;
		for (int ientity=0; ientity<entities.size(); ientity++) {
			AceEntity entity = (AceEntity) entities.get(ientity);
			String type = entity.type;
			String subtype = entity.subtype;
			ArrayList names = entity.names;
			for (int iname=0; iname<names.size(); iname++) {
				AceEntityName name = (AceEntityName) names.get(iname);
				String text = name.text;
				String[] tokens = text.split("\\s");
				String[] features = NEfeatures (tokens, type);
				for (int ifeat=0; ifeat<features.length; ifeat++) {
					writer.print (features[ifeat] + " ");
				}
				writer.println (subtype);
			}
		}
	}

	private static String[] NEfeatures (String[] tokens, String type) {
		String[] features = new String[tokens.length+1];
		type = type.substring(0,3);
		features[0] = type;
		for (int i=0; i<tokens.length; i++) {
			features[i+1] = type + "=" + tokens[i].toLowerCase();
		}
		return features;
	}

	public static void createModel () {
		boolean USE_SMOOTHING = false;
		boolean PRINT_MESSAGES = true;
		try {
	    FileReader datafr = new FileReader(new File(featureFile));
	    EventStream es =
				new BasicEventStream(new PlainTextByLineDataStream(datafr));
	    GIS.SMOOTHING_OBSERVATION = 0.1;
	    model = GIS.trainModel(es, 100, 2, USE_SMOOTHING, PRINT_MESSAGES);
		} catch (Exception e) {
		    System.err.print("Unable to create model due to exception: ");
		    System.err.println(e);
		}
	}

	public static void store (String modelFileName) {
		try {
			File outputFile = new File(modelFileName);
    	GISModelWriter writer = new SuffixSensitiveGISModelWriter(model, outputFile);
    	writer.persist();
    } catch (IOException e) {
    	System.err.println ("MaxEntNE.saveModel: unable to save model");
    	System.err.println (e);
    }
	}

	/**
	 *  load the maxent model from the file specified as Jet paramter
	 *  <CODE>Ace.NameSubtypeModel.fileName</CODE>.
	 */

	public static void load () {
		String fileName = JetTest.getConfigFile("Ace.NameSubtypeModel.fileName");
		if (fileName != null) {
			load (fileName);
		} else {
			System.err.println ("NameSubtyper.load:  no file name specified in config file");
		}
	}
	
	/**
	 *  load the maxent model from file <CODE>modelFileName</CODE>.
	 */

	public static void load (String modelFileName) {
		try {
		    model = (GISModel) new SuffixSensitiveGISModelReader(new File(modelFileName)).getModel();
		    System.err.println ("GIS model loaded.");
		} catch (Exception e) {
		    e.printStackTrace();
		    System.exit(0);
		}
	}

	/**
	 *  return the most likely subclass of name <CODE>name</CODE>, of EDT type
	 *  <CODE>type</CODE>.
	 */

	public static String classify (String name, String type) {
		if (model == null)
			load ();
		String[] tokens = name.split("\\s");
		String[] features = NEfeatures (tokens, type);
		String subtype = model.getBestOutcome(model.eval(features));
		return subtype;
	}
}
