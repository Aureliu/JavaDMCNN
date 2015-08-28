// -*- tab-width: 4 -*-
package Jet.HMM;

import java.util.*;
import java.io.*;

import Jet.JetTest;
import Jet.Control;
import Jet.Pat.Pat;
import Jet.Lisp.*;
import Jet.Tipster.*;
import Jet.Refres.Resolve;
import Jet.Chunk.Chunker;

import AceJet.Gazetteer;

class NameCoref {

	static final String home = "C:/Documents and Settings/Ralph Grishman/My Documents/";
	static final String ACEdir = home + "ACE/";
	// static final String collection = ACEdir + "training nwire sgm 10.txt";
	static final String collection = ACEdir + "sep02 nwire sgm.txt";
	// static final String keyCollection = ACEdir + "training nwire ne 10.txt";
	static final String keyCollection = ACEdir + "sep02 nwire ne.txt";
	static final String svmInputFile = home + "svmlight/training.txt";
	static final String dictFile = ACEdir + "EDT type dict.txt";

	static PrintStream writer;

	public static void main (String[] args) throws IOException {
		// initialize Jet
		System.out.println("Starting ACE Jet...");
		JetTest.initializeFromConfig("ME ace.properties");
		// JetTest.initializeFromConfig("ace parser.properties");
		Chunker.loadModel();
		// load ACE type dictionary and gazetteer
		AceJet.EDTtype.readTypeDict(dictFile);
		AceJet.Ace.gazetteer = new Gazetteer();
		AceJet.Ace.gazetteer.load(ACEdir + "loc.dict");
		// turn off traces
		Pat.trace = false;
		Resolve.trace = false;
		writer = new PrintStream (new FileOutputStream (svmInputFile));
		processCollection (collection, keyCollection);
		writer.close();
	}

	private static void processCollection (String testCollection, String keyCollection)
			throws IOException {
		DocumentCollection testCol = new DocumentCollection(testCollection);
		testCol.open();
		DocumentCollection keyCol = new DocumentCollection(keyCollection);
		keyCol.open();
		if (testCol.size() != keyCol.size()) {
			System.out.println (" ** Test and key collections have different sizes, cannot evaluate.");
			return;
		}
		for (int docCount=0; docCount<testCol.size(); docCount++) {
		// open test document
			ExternalDocument testDoc = testCol.get(docCount);
			testDoc.setAllTags(true);
			testDoc.open();
			// process document
			System.out.println ("Processing document " + docCount + " " +
			                    testDoc.fileName());
			AceJet.Ace.monocase = AceJet.Ace.allLowerCase(testDoc);
			Control.processDocument (testDoc, null, docCount == -1, docCount);
			// open key document
			ExternalDocument keyDoc = keyCol.get(docCount);
			keyDoc.setAllTags(true);
			keyDoc.open();
			new View (testDoc, docCount);
			new View (keyDoc, docCount + 100);
			writeNamedMentions (testDoc, keyDoc);
			// break;
		}
	}

	private static void writeNamedMentions (Document doc, Document keyDoc) {
		// for every entity ...
		Vector entities = doc.annotationsOfType("entity");
		for (int ientity=0; ientity<entities.size(); ientity++) {
			Annotation entity = (Annotation) entities.get(ientity);
			Vector mentions = (Vector) entity.get("mentions");
		//    get mention count etc.
			int mentionCount = mentions.size();
		//    for every mention of that entity ...
			for (int imention=0; imention<mentions.size(); imention++) {
				Annotation mention = (Annotation) mentions.get(imention);
		//       if that mention is a name ...
				Annotation head = Resolve.getHeadC(mention);
				String[] mentionName = Resolve.getNameTokens (doc, mention);
				boolean isNameMention = mentionName != null;
				if (isNameMention) {
					// get corresponding ENAMEX annotation
					Vector enamexes = doc.annotationsAt(head.start(), "ENAMEX");
					if (enamexes == null || enamexes.size() == 0) {
						System.out.println ("Name mention with no enamex.");
						return;
					}
					Annotation enamex = (Annotation) enamexes.get(0);
					int nameStart = enamex.start();
					String nameType = (String) enamex.get("type");
					Integer margin = (Integer) enamex.get("margin");
					Span span = enamex.span();
		//          is there a corresponding name in the key file?
		//          write a line with this info
					Vector keyEnamexes = keyDoc.annotationsAt(nameStart, "ENAMEX");
					boolean match = false;
					if (keyEnamexes != null && keyEnamexes.size() > 0) {
						Annotation keyEnamex = (Annotation) keyEnamexes.get(0);
						String keyNameType = (String) keyEnamex.get("TYPE");
						if (nameType.equalsIgnoreCase(keyNameType) &&
						    span.equals(keyEnamex.span())) {
							match = true;
						}
					}
					System.out.println ("\"" + Resolve.concat(mentionName) + "\" " + span +
					                    " type:" + nameType +
					                    " margin:" + margin +
					                    " firstMention:" + (imention==0) +
					                    " mentionCount:" + mentionCount + " " + match);
					writer.println ((match?1:-1) +
					                " 1:" + margin +
					                " 2:" + ((imention==0)?1:0) +
					                " 3:" + mentionCount);
				}
			}
		}
	}
}
