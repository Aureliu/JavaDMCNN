// -*- tab-width: 4 -*-
package AceJet;

import java.util.*;
import java.io.*;

import Jet.Tipster.*;
import Jet.Lisp.*;

/**
 *  convert a set of ACE APF files to XML files containing mentions
 *  specified by the APF file, and the entity number for each mention.
 *  Each name is marked with "mention entity=n".  Only the head of the
 *  mention is tagged.
 */

class APFtoCorefXML {

	static String ACEdir;
	static String outputDir;
	static String apfExtension;
	static boolean showTypes;

	/**
   *  convert a list of APF files to XML files containing in-line markup for
   *  coreference.  Takes the following arguments:
   *  <ul>
   *  <li> year:  the year (2002, 2003, 2004, 2005) the APF file was created,
   *       which determines its format and file extension
   *  <li> ACEdir:    the directory containing the text and APF files
   *  <li> corefDir:  the directory containing the in-line coreference files
   *  <li> filelist:  a file containing a list of document names
   *  <li> showTypes: (optional) if this argument is present, entity type
   *                  and subtype information is included with each mention
   *  </ul>
   */
   
	public static void main (String [] args) throws IOException  {
		if (!(args.length == 4 || args.length ==5)) {
			System.err.println ("APFtoCorefXML requires 4 or 5 arguments:");
			System.err.println ("  year apf-directory  output-directory  filelist [showTypes]");
			System.exit (1);
		}
		String year = args[0];
		apfExtension = ".apf.xml";
		AceDocument.ace2004 = false;
		AceDocument.ace2005 = false;
		if (year.equals("2002")) {
			apfExtension = ".sgm.tmx.rdc.xml";
		} else if (year.equals("2003")) {
		} else if (year.equals("2004")) {
			AceDocument.ace2004 = true;
		} else if (year.equals("2005")) {
			AceDocument.ace2004 = true;
			AceDocument.ace2005 = true;
		} else {
			System.err.println ("Invalid year:  must be 2002-2005");
			System.exit (1);
		}
		ACEdir = args[1];
		if (!ACEdir.endsWith("/")) ACEdir += "/";
		outputDir = args[2];
		if (!outputDir.endsWith("/")) outputDir += "/";
		String fileList = args[3];
		showTypes = args.length == 5;
		processFileList (fileList);
	}
	

	private static void processFileList (String fileList) throws IOException {
		// open list of files
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		String currentDoc;
		while ((currentDoc = reader.readLine()) != null) {
			// process file 'currentDoc'
			docCount++;
			System.out.println ("\nProcessing document " + docCount + ": " + currentDoc);
			String textFileName = ACEdir + currentDoc + ".sgm";
			ExternalDocument doc = new ExternalDocument("sgml", textFileName);
			doc.setAllTags(true);
			doc.open();
			String APFfileName = ACEdir + currentDoc + apfExtension;
			AceDocument aceDoc = new AceDocument(textFileName, APFfileName);
			addMentionTags (doc, aceDoc);
			doc.setSGMLwrapMargin(0);
			doc.saveAs(outputDir, currentDoc + ".co.txt");
		}
	}
	
	/**
	 *  generate mention annotations (with entity numbers) based on the ACE
	 *  entities and mentions.
	 */

	static void addMentionTags (Document doc, AceDocument aceDoc) {
		ArrayList entities = aceDoc.entities;
		for (int i=0; i<entities.size(); i++) {
			AceEntity entity = (AceEntity) entities.get(i);
			ArrayList mentions = entity.mentions;
			for (int j=0; j<mentions.size(); j++) {
				AceEntityMention mention = (AceEntityMention) mentions.get(j);
				// we compute a jetSpan not including trailing whitespace
				Span aceSpan = mention.head;
				Span jetSpan = new Span (aceSpan.start(), aceSpan.end()+1);
				FeatureSet features = new FeatureSet("entity", new Integer(i));
				if (showTypes) {
					features.put("type", entity.type.substring(0,3));
					if (entity.subtype != null)
						features.put("subtype", entity.subtype);
				}					
				doc.annotate ("mention", jetSpan, features);
			}
		}
	}

}
