// -*- tab-width: 4 -*-
package Jet.Chunk;

import opennlp.maxent.*;
import opennlp.maxent.io.*;
import opennlp.model.*;
import java.io.*;
import java.util.*;
import Jet.Tipster.*;
import Jet.*;
import Jet.Lex.Tokenizer;
import Jet.Console;

/**
 *  a noun group chunker using a maximum entropy model.
 */

public class Chunker {

	public static GISModel model = null;
	static Annotation[] tokens;
	static int tokenCount, jetTokenCount;
	static String[] pos, word, tag;
	static int[] position;
	static boolean trace = false;

	/**
	 *  adds chunks (annotations of type <b>ng</b>) to Span 'span' of
	 *  Document 'doc'.
	 */

	public static void chunk (Document doc, Span span) {
		// 1. get tokens
		tokens = Tokenizer.gatherTokens (doc, span);
		tokenCount = tokens.length;
		if (tokenCount == 0) {
			return;
		}
		// 2. gather Penn POS and token texts
		pos = new String[tokenCount];
		word = new String[tokenCount];
		position = new int[tokenCount];
		int itoken = 0;
		for (int i=0; i<tokenCount; i++) {
			int posn = tokens[i].span().start();
			position[itoken] = posn;
			pos[itoken] = getPosAt (doc, posn);
			word[itoken] = doc.text(tokens[i]).trim();
			if (itoken >= 2 && doc.text(tokens[i-1]).equals("-")) {
				pos[itoken-2] = "JJ";
				word[itoken-2] = word[itoken-2] + word[itoken-1] + word[itoken];
				itoken = itoken-2;
			}
			itoken++;
		}
		jetTokenCount = itoken;
		// 3. compute features and predict chunk tag
		tag = new String[jetTokenCount];
		for (int i=0; i<jetTokenCount; i++) {
			String[] features = chunkFeatures(i);
			tag[i] = model.getBestOutcome(model.eval(features)).intern();
			if ((i == 0 || tag[i-1] == "O") && tag[i] == "B")
				tag[i] = "I";
			// System.err.print ("Word = " + word[i] + " pos = " + pos[i]);
			// System.err.println ("  tag = " + tag[i]);
		}
		// 4. generate chunk annotation (later, add HEAD feature)
		int chunkStart = -1;
		int nameEnd = -1;
		for (int i=0; i<jetTokenCount; i++) {
			int posn = position[i];
			if (nameEnd == posn)
				nameEnd = -1;
			// if in a chunk, next tag is O or B, and current and next tokens
			// are not part of same name, this is end of chunk
			if (chunkStart >= 0 && (tag[i] == "O" | tag[i] == "B")
			                    && nameEnd < 0) {
				// generate chunk from chunkStart to posn
				Annotation ann = new Annotation ("ng", new Span(chunkStart, posn), null);
				doc.addAnnotation(ann);
				if (trace)
					Console.println ("Annotate " + doc.text(ann) + " as " + ann);
				chunkStart = -1;
			}
			if (nameEnd < 0) {
				Vector nameAnns = doc.annotationsAt(posn, "ENAMEX");
				if (nameAnns != null && nameAnns.size() > 0) {
					Annotation nameAnn = (Annotation) nameAnns.get(0);
					nameEnd = nameAnn.span().end();
				}
			}
			// if not in a chunk and next token has tag B or I or is part of
			// a name, start a new chunk
			if (chunkStart < 0 && (tag[i] == "B" || tag[i] == "I" || nameEnd > 0)) {
				chunkStart = posn;
			}
		}
		// 4a. generate final chunk
		if (chunkStart >= 0) {
			int lastTokenEnd = tokens[tokenCount-1].span().end();
			// generate chunk from chunkStart to lastTokenEnd
			Annotation ann = new Annotation ("ng", new Span(chunkStart, lastTokenEnd), null);
			doc.addAnnotation(ann);
			if (trace)
				Console.println ("Annotate " + doc.text(ann) + " as " + ann);
		}
	}

	private static String getPosAt (Document doc, int posn) {
		Vector v = doc.annotationsAt (posn, "tagger");
		if (v == null || v.size() == 0) return "";
		Annotation taggerAnn = (Annotation) v.get(0);
		return (String) taggerAnn.get("cat");
	}

	private static String[] chunkFeatures (int i) {
		String[] features = new String[8];
		features[0] = "prevPOS=" + (i>0 ? pos[i-1] : "");
		features[1] = "currPOS=" + pos[i];
		features[2] = "nextPOS=" + (i < (jetTokenCount-1) ? pos[i+1] : "");
		if (i < (jetTokenCount-2))
			features[3] = "POS012=" + pos[i] + ":" + pos[i+1] + ":" + pos[i+2];
		else if (i < (jetTokenCount-2))
			features[3] = "POS012=" + pos[i] + ":" + pos[i+1] + ":";
		else
			features[3] = "POS012=" + pos[i] + "::";
		features[4] = "prevTag=" + (i>0 ? tag[i-1] : "");
		features[5] = "currWord=" + word[i];
		features[6] = "W-1W0=" + (i>0 ? word[i-1] : "") + ":" + word[i];
		features[7] = "W0W1=" + word[i] + ":" + (i < (jetTokenCount-1) ? word[i+1] : "");
		return features;
	}

	public static void loadModel () {
		String modelFileName = ChunkBuildTrain.chunkDir + "chunk model.txt";
		loadModel (modelFileName);
	}

	/**
	 *  load a maximum entropy chunker model from file 'modelFileName'.
	 */

	public static void loadModel (String modelFileName) {
		try {
		    model = (GISModel) new SuffixSensitiveGISModelReader(new File(modelFileName)).getModel();
		    System.err.println ("Chunker model loaded from " + modelFileName);
		} catch (Exception e) {
		    e.printStackTrace();
		    System.exit(0);
		}
	}

	public static void main (String[] args) {
		loadModel();
		JetTest.initializeFromConfig("ME Chunk.properties");
		// JetTest.dataPath = "C:\\My Documents\\Jet\\Data";
		new Jet.Console();
	}
}
