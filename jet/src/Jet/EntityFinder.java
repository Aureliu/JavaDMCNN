// -*- tab-width: 4 -*-
//
//  EntityFinder.java
//  (Yusuke Shinyama)
//
//  This is a simple wrapper for NE tagging and coref. resolution
//  modules. It's to be called from other processes in a unixy environment.
//
//  Invocation:
//    $ java Jet.EntityFinder /path/to/propfile /path/to/datadir input > output
//
//  Input format:
//    # SENT1
//    Fred has a cat in the city , New York .
//
//  Output format:
//    # SENT1
//    <tagger cat="NNP"><ENAMEX TYPE="PERSON"><refobj objid="0" netype="PERSON">Fred </refobj></ENAMEX></tagger><tagger cat="VBZ">has </tagger><refobj objid="1"><tagger cat="DT">a </tagger><tagger cat="NN">cat </tagger></refobj><tagger cat="IN">in </tagger><refobj objid="2" netype="GPE"><tagger cat="DT">the </tagger><tagger cat="NN">city </tagger><tagger cat=",">, </tagger><ENAMEX TYPE="GPE"><refobj objid="2" netype="GPE"><tagger cat="NNP">New </tagger><tagger cat="NNP">York </tagger></refobj></ENAMEX><tagger cat=".">. </tagger></refobj>
//
//  Each input line consists of one sentence. A sentence must be tokenized in advance
//  and all the words must be delimited with a blank character. A line that begins
//  with '#' is a comment. Output is in SGML format where POS entity and name tags
//  are augmented. The processing is done in line-by-line basis and a blank line
//  indicates an end of a document.
//


package Jet;

import java.util.*;
import java.io.*;

import Jet.Tipster.*;
import Jet.Lisp.*;
import Jet.Pat.Pat;
import Jet.Refres.*;
import Jet.Chunk.Chunker;
import Jet.Parser.*;
import Jet.Zoner.*;
import Jet.HMM.*;
import Jet.Scorer.SGMLProcessor;
import AceJet.Gazetteer;
import AceJet.Ace;


public class EntityFinder {
	
	static final int MaxProcessSentences = 100;

    static void writeDocRaw(Document doc, PrintStream out) throws IOException {
	    out.println(doc.writeSGML(null).toString());
		out.flush();
		return;
	}
	
    static void writeDoc1(Document doc, PrintStream out) throws IOException {
	    Vector entities = doc.annotationsOfType("entity");
		if (entities == null) {
			System.err.println("No Entity: "+doc);
			return;
		}
	    Iterator entityIt = entities.iterator();
		int i = 0;
	    while (entityIt.hasNext()) {
	        Annotation entity = (Annotation) entityIt.next();
			Vector mentions = (Vector) entity.get("mentions");
			Iterator mentionIt = mentions.iterator();
			String nameType = (String)entity.get("nameType");
			while (mentionIt.hasNext()) {
				Annotation mention1 = (Annotation) mentionIt.next();
				Annotation mention2 = new Annotation("refobj", mention1.span(), new FeatureSet());
				mention2.put("objid", Integer.toString(i));
				if (nameType != null) {
					mention2.put("netype", nameType);
				}
				doc.addAnnotation(mention2);
			}
			i++;
	    }
		// remove other annotations.
		String[] annotypes = doc.getAnnotationTypes();
		for (i = 0; i < annotypes.length; i++) {
			String t = annotypes[i];
			if (! (t.equals("tagger") || t.equals("refobj") || t.equals("ENAMEX"))) {
				doc.removeAnnotationsOfType(t);
			}
		}
 		writeDocRaw(doc, out);
		return;
	}
    
	static void processDoc1(Document doc, int docno) throws IOException {
		// process document
		//System.err.println ("Parsing: "+docno+"/"+doc);
		String script = JetTest.config.getProperty("processDocument");
		// if there is a name tagger, clear its cache
		if (JetTest.nameTagger != null) JetTest.nameTagger.newDocument();
		Span all = new Span(0, doc.length());
		Control.applyScript (doc, all, script);
	}

	static void processFile(String fname, PrintStream out) throws IOException {
		System.err.println ("Processing: "+fname);

		FileInputStream fio = new FileInputStream(new File(fname));
		InputStreamReader fread = new InputStreamReader(fio, JetTest.encoding);
		BufferedReader fp = new BufferedReader(fread);
		StringBuffer buf = new StringBuffer();

		int docno = 0, allsents = 0, processedsents = 0;
		while (true) {
			String line = fp.readLine();

			// EOF or an empty line: the end of a Document.
			if (line == null || line.equals("")) {
				if (0 < buf.length()) {
					SGMLProcessor.allTags = true;
					Document doc = SGMLProcessor.sgmlToDoc(buf.toString(), (String[])null);
					doc.setSGMLwrapMargin(0);
					System.err.println ("Doc-"+docno+": sents="+allsents+", processed="+processedsents);
					processDoc1(doc, docno);
					writeDoc1(doc, out);
					out.flush();
					buf = new StringBuffer();
					docno++;
					allsents = 0;
					processedsents = 0;
				}
				if (line == null) {
					break;
				} else {
					continue;
				}
			}

			if (line.startsWith("#")) {
				// "#" indicates a comment line.
				buf.append(line+"\n");
			} else {
				allsents++;
				if (processedsents < MaxProcessSentences) {
					buf.append("<sentence>");
					String[] words = line.split(" ");
					for (int i = 0; i < words.length; i++) {
						if (0 != words[i].length()) {
							buf.append("<token>"+words[i]+" </token>");
						}
					}
					buf.append("</sentence>\n");
					processedsents++;
				}
			}
		}

		fp.close();
		fread.close();
		fio.close();
		return;
	}
	
	public static void main (String[] args) throws IOException, ClassNotFoundException {
		// initialize Jet
		if (args.length < 2) {
			System.err.println("usage: java EntityFinder propfile datapath files ...");
			System.exit(2);
		}
		JetTest.initializeFromConfig(args[0], args[1]);
		Pat.trace = false;
		Resolve.trace = false;

		String script = JetTest.config.getProperty("processDocument");
		if (script == null || script.length() == 0) {
			Console.println ("*** System error: no processDocument script.");
			return;
		}

		for (int i = 2; i < args.length; i++) {
			processFile(args[i], System.out);
		}
	}
}
