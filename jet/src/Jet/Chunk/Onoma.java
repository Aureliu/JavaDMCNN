package Jet.Chunk;

import java.io.*;
import java.util.*;

import AceJet.Gazetteer;
import Jet.Lex.Lexicon;
import Jet.Lisp.FeatureSet;
import Jet.Tipster.*;

/**
 *  Onoma provides a dictionary of proper names ('onomasticon').  This dictionary
 *  is used by the 'tagNamesFromOnoma' command.
 */

public class Onoma {

	public static boolean loaded = false;

	/**
	 *  Read the onomasticon from file 'fileName'.  Each line of the file
	 *  consists of two fields, separated by a tab: <br>
	 *  the name, consisting of one or more blank-separated tokens <br>
	 *  the type of the name
	 */

	public static void read (String fileName) throws IOException {
		loaded = true;
		int n = 0;
		BufferedReader reader = new BufferedReader (new FileReader (fileName));
		String line;
		while ((line = reader.readLine()) != null) {
			String[] fields = line.split("\t");
			if (fields.length < 2 || fields.length > 3) {
				System.out.println ("Invalid onoma line: " + line);
				continue;
			}
			String name = fields[0];
			String type = fields[1];
			String subtype = null;
			if (fields.length == 3)
				subtype = fields[2];
			Lexicon.clearEntry (Gazetteer.splitAtWS(name));
			Lexicon.addEntry (Gazetteer.splitAtWS(name),
                                          new FeatureSet ("TYPE", type, "SUBTYPE", subtype),
                                          "onoma");
			n++;
		}
		System.out.println ("Onoma:  read " + n + " names.");
	}

	/**
	 *  This is a stub which remains from code that was added at SRI's 
	 *  request for Dovetail in order to tag drug names..
	 */

	public static void tagDrugs (Document doc, Span span) {
	}

	/**
	 *  tag names which appear in the onomasticon, adding an ENAMEX annotation
	 *  with features TYPE and SUBTYPE.  If there are multiple
	 *  matches at a given position, the longest match is used.  
	 *  <p>
	 *  If there is an existing ENAMEX annotation which overlaps but is
	 *  not contained in the name in the onomasticon, the existing
	 *  annotation takes precedence and no new annotation is added.  If
	 *  there are existing ENAMEX annotations which are contained within
	 *  the name given in the onomasticon, these existing annotations
	 *  are deleted.
	 */

	public static void tagNames (Document doc, Span span) {
		int posn = span.start();
		Annotation token;
		while (posn < span.end()) {
			Vector<Annotation> onomas = doc.annotationsAt(posn, "onoma");
			if (onomas != null && onomas.size() > 0) {
				Annotation onoma = onomas.get(0);
				List<Annotation> anns = containedNames(doc, span, onoma.span());
				if (anns != null) {
					String type = (String) onoma.get("TYPE");
					String subtype = (String) onoma.get("SUBTYPE");
					doc.annotate ("ENAMEX", onoma.span(), 
						      new FeatureSet("TYPE", type, "SUBTYPE", subtype));
					for (Annotation ann : anns)
						doc.removeAnnotation(ann);
				}
				posn = onoma.end();
			} else if ((token = doc.tokenAt(posn)) != null) {
				posn = token.end();
			} else return;
		} 
	}

	/**
 	 *  determines whether Span 'span' of Document 'doc' contains any ENAMEX
 	 *  annotations which overlap 'onomaSpan'.  If there are annotations
 	 *  which overlap but are not contained within onomaSpan, return null;
 	 *  otherwise return a list of all annotations contained within onomaSpan.
 	 */

	private static List<Annotation> containedNames (Document doc, Span span, Span onomaSpan) {
		List<Annotation> anns = new ArrayList<Annotation>();
		int posn = span.start();
		Annotation token;
		while (posn < span.end()) {
			Vector<Annotation> enamexes = doc.annotationsAt(posn, "ENAMEX");
			if (enamexes != null && enamexes.size() > 0) {
				Annotation enamex = enamexes.get(0);
				Span enamexSpan = enamex.span();
				if (enamexSpan.end() <= onomaSpan.start()) ;
				else if (enamexSpan.start() >= onomaSpan.end()) ;
				else if (enamexSpan.within(onomaSpan))
					anns.add(enamex);
				else return null;
				posn = enamex.end();
			} else if ((token = doc.tokenAt(posn)) != null) {
				posn = token.end();
			} else return null;
		}
		return anns;
	}
		
}
