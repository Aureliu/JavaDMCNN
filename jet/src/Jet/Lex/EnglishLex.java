// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Lex;

import java.io.*;
import java.util.*;
import Jet.Pat.PatternSyntaxError;
import Jet.Lisp.*;

/**
 *  EnglishLex contains all the methods for reading and interpreting
 *  a file of <a href="doc-files/lexicon.html">English lexical entries</a>.
 */

public class EnglishLex {

	/**
	 *  reads the English lexicon entries from file <CODE>fileName</CODE>,
	 *  expands them (generating inflectional variants), and adds 
	 *  them to the dictionary.
	 */

	public static void readLexicon (String fileName) throws IOException {
		System.err.println ("Reading lexicon " + fileName);
		Reader reader = new BufferedReader ( new FileReader (fileName));
		LineNumberReader lnreader = new LineNumberReader (reader);
		StreamTokenizer tok = new StreamTokenizer(lnreader);
		int definitionCounter = 0;
		while (tok.nextToken() != StreamTokenizer.TT_EOF) {
			try {
				readLexiconDefinition (tok);
				definitionCounter++;
			}
			catch (PatternSyntaxError pse) {
				int ln = lnreader.getLineNumber();
				System.err.println ("*** syntax error in lexicon, line " + ln);
				System.err.println (pse.toString());
				if (tok.ttype == StreamTokenizer.TT_WORD)
					System.err.println ("Current token = " + tok.sval);
				else
					System.err.println ("Current token = " + (char) tok.ttype);
				while (tok.nextToken() != ';') {
					if (tok.ttype == StreamTokenizer.TT_EOF) return;
				}
			}
		}
		System.err.println (definitionCounter + " lexical entries read");
	}

	private static void readLexiconDefinition (StreamTokenizer tok)
		throws IOException, PatternSyntaxError {
		Vector lexItem = new Vector();
		String words[];
		String entryType;
		FeatureSet fs;
		if (tok.ttype == StreamTokenizer.TT_WORD) {
			do {lexItem.addElement(tok.sval);}
			while (tok.nextToken() == StreamTokenizer.TT_WORD);
			words = (String []) lexItem.toArray(new String[0]);
		} else if (tok.ttype == '"') {
			words = Tokenizer.tokenize(tok.sval);
			tok.nextToken();
		} else throw new PatternSyntaxError ();
		// skip empty definitions (for now)
		if (tok.ttype == ';') return;
		if (tok.ttype != ',') throw new PatternSyntaxError (", expected");
		if (tok.nextToken() == StreamTokenizer.TT_WORD) {
			entryType = tok.sval;
			if (tok.nextToken() == ',') {
				fs = new FeatureSet (tok, false, ';');
				defineEnglishEntry (words, entryType, fs);
			} else if (tok.ttype == ';') {
				defineEnglishEntry (words, entryType, new FeatureSet());
			} else {
				System.err.println ("In defn of " + words[0].toString());
				throw new PatternSyntaxError (", or ; expected");
			}
		} else if (tok.ttype == ',') {
			fs = new FeatureSet (tok, false, ';');
			Lexicon.addEntry (words, fs);
		} else if (tok.ttype == ';') {
			// skip empty definitions
			return;
		} else {
			throw new PatternSyntaxError ("entry type expected");
		}
	}

	private static void defineEnglishEntry (String[] words, String entryType,
											FeatureSet fs) throws PatternSyntaxError {
		if (entryType.equals("noun"))
			defineEnglishNoun (words, fs);
		else if (entryType.equals("verb"))
			defineEnglishVerb (words, fs);
		else if (entryType.equals("adj"))
			defineEnglishAdjective (words, fs);
		else if (entryType.equals("adv"))
			defineEnglishAdverb (words, fs);
		else if (entryType.equals("name"))
			defineEnglishName (words, fs);
		else
			throw new PatternSyntaxError ("unknown entry type");
	}

	private static void defineEnglishNoun (String [] words, FeatureSet fs) {
		// check fs:  plural:string, attributes:fs, xn:string
		String plural = makeString(fs.get("plural"));
		String[] pluralWords; // have to split at blanks
		FeatureSet attributes = (FeatureSet) fs.get("attributes");
		String xn = makeString(fs.get("xn"));
		String pred = (xn == null) ? fuseWithUnderscores(words) : xn;

		//  singular --------------------------
		FeatureSet singularDefn =
			new FeatureSet ("cat",    "n",
							"number", "singular",
							"pa",     new FeatureSet("head",   pred,
													 "number", "singular"));
		singularDefn.putAll(attributes);
		Lexicon.addEntry (words, singularDefn);

		//  plural ----------------------------
		if (plural == null) {
			pluralWords = nounPlural (words);
		} else if (plural.equals("none")) {
			return;
		} else {
			pluralWords = Tokenizer.tokenize (plural);
		}
		FeatureSet pluralDefn =
			new FeatureSet ("cat",    "n",
							"number", "plural",
							"pa",     new FeatureSet("head",   pred,
													 "number", "plural"));
		pluralDefn.putAll(attributes);
		Lexicon.addEntry (pluralWords,pluralDefn);
	}

	private static String makeString (Object ob) {
		if (ob == null)
			return null;
		else return ob.toString();
    }

	private static void defineEnglishVerb (String [] words, FeatureSet fs) {
		// need to validate
		String thirdSing = makeString(fs.get("thirdSing"));
		String plural = makeString(fs.get("plural"));
		String past = makeString(fs.get("past"));
		String pastPart = makeString(fs.get("pastPart"));
		String presPart = makeString(fs.get("presPart"));
		FeatureSet attributes = (FeatureSet) fs.get("attributes");
		String xn = (String) fs.get("xn");
		String pred = (xn == null) ? fuseWithUnderscores(words) : xn;

		//  infinitive --------------------------------
		FeatureSet infinitiveDefn =
			new FeatureSet ("cat", "v",
							"pa",  new FeatureSet ("head", pred));
		infinitiveDefn.putAll(attributes);
		Lexicon.addEntry (words, infinitiveDefn);

		//  present tense, third person singular -----
		FeatureSet thirdSingDefn =
			new FeatureSet ("cat",    "tv",
							"number", "singular",
							"pa",     new FeatureSet ("head",  pred,
													  "tense", "present"));
		thirdSingDefn.putAll(attributes);
		if (thirdSing == null)
			Lexicon.addEntry (nounPlural(words), thirdSingDefn);
		else
			Lexicon.addEntry (Tokenizer.tokenize (thirdSing), thirdSingDefn);

		// present tense, plural ----------------------
		FeatureSet pluralDefn =
			new FeatureSet ("cat",    "tv",
							"number", "plural",
							"pa",     new FeatureSet ("head",  pred,
													  "tense", "present"));
		pluralDefn.putAll(attributes);
		if (plural == null)
			Lexicon.addEntry (words, pluralDefn);
		else
			Lexicon.addEntry (Tokenizer.tokenize(plural), pluralDefn);

		// past tense ----------------------------------
		FeatureSet pastDefn =
			new FeatureSet ("cat", "tv",
							"pa",  new FeatureSet ("head",  pred,
												   "tense", "past"));
		pastDefn.putAll(attributes);
		if (past == null)
			Lexicon.addEntry (verbPast(words), pastDefn);
		else
			Lexicon.addEntry (Tokenizer.tokenize(past), pastDefn);

		// present participle --------------------------
		FeatureSet presPartDefn =
			new FeatureSet ("cat", "ving",
							"pa",  new FeatureSet ("head",  pred));
		presPartDefn.putAll(attributes);
		if (presPart == null)
			Lexicon.addEntry (verbPresPart(words), presPartDefn);
		else
			Lexicon.addEntry (Tokenizer.tokenize(presPart), presPartDefn);

		// past participle ------------------------------
		FeatureSet pastPartDefn =
			new FeatureSet ("cat", "ven",
							"pa",  new FeatureSet ("head",  pred));
		pastPartDefn.putAll(attributes);
		if (pastPart == null)
			Lexicon.addEntry (verbPast(words), pastPartDefn);
		else
			Lexicon.addEntry (Tokenizer.tokenize(pastPart), pastPartDefn);
    }

	private static void defineEnglishAdjective (String[] words, FeatureSet fs) {
		FeatureSet attributes = (FeatureSet) fs.get("attributes");
		String pred = fuseWithUnderscores(words);
		FeatureSet adjectiveDefn =
			new FeatureSet ("cat",  "adj",
							"pa",   new FeatureSet ("head", pred));
		adjectiveDefn.putAll(attributes);
		Lexicon.addEntry (words, adjectiveDefn);
	}

	private static void defineEnglishAdverb (String[] words, FeatureSet fs) {
		FeatureSet attributes = (FeatureSet) fs.get("attributes");
		String pred = fuseWithUnderscores(words);
		FeatureSet adverbDefn =
			new FeatureSet ("cat",  "adv",
							"pa",   new FeatureSet ("head", pred));
		adverbDefn.putAll(attributes);
		Lexicon.addEntry (words, adverbDefn);
	}

	private static void defineEnglishName (String[] words, FeatureSet fs) {
		String nameClass = (String) fs.get("class");
		FeatureSet nameDefn =
			new FeatureSet ("cat", "name");
		if (nameClass != null)
			nameDefn.put("class", nameClass);
		Lexicon.addEntry (words, nameDefn);
	}

	private static String fuseWithUnderscores (String[] words) {
		String result = words[0];
		for (int i=1; i < words.length; i++) {
			result += "_" + words[i];
		}
		return result;
	}

	// M O R P H O L O G Y

	public static String[] nounPlural (String[] words) {
		String pluralForm;
		String[] result =  new String[words.length];
		for (int i=0; i < words.length-1; i++) {
			result[i] = words[i];
		}
		String lastword = words[words.length-1];
		int len = lastword.length();
		char lastChar = lastword.charAt(len-1);
		char nextToLast = (lastword.length()>1) ? lastword.charAt(len-2) :  ' ';
		if (lastChar == 's' || lastChar == 'z' || lastChar == 'x' ||
			((nextToLast == 'c' || nextToLast == 's') && lastChar == 'h')) {
			pluralForm = lastword + "es";
		} else if (lastChar == 'y') {
			if (nextToLast == 'a' || nextToLast == 'e' ||
				nextToLast == 'i' || nextToLast == 'o' || nextToLast == 'y') {
				pluralForm = lastword + "s";
			} else {
				pluralForm = lastword.substring(0,len-1) + "ies";
			}
		} else {
			pluralForm = lastword + "s";
		}
		result[words.length-1] = pluralForm;
		// System.err.println ("plural = " + pluralForm);
		return result;
	}

	private static String[] verbPast (String[] words) {
		String pastForm;
		String[] result =  new String[words.length];
		for (int i=0; i < words.length-1; i++) {
			result[i] = words[i];
		}
		String lastword = words[words.length-1];
		int len = lastword.length();
		char lastChar = lastword.charAt(len-1);
		char nextToLast = (lastword.length()>1) ? lastword.charAt(len-2) :  ' ';
		if (lastChar == 'e') {
			pastForm = lastword + "d";
		} else if (lastChar == 'y') {
			if (nextToLast == 'a' || nextToLast == 'e' ||
				nextToLast == 'i' || nextToLast == 'o' || nextToLast == 'y') {
				pastForm = lastword + "ed";
			} else {
				pastForm = lastword.substring(0,len-1) + "ied";
			}
		} else {
			pastForm = lastword + "ed";
		}
		result[words.length-1] = pastForm;
		// System.err.println ("past = " + pastForm);
		return result;
	}

	private static String[] verbPresPart (String[] words) {
		String presPartForm;
		String[] result =  new String[words.length];
		for (int i=0; i < words.length-1; i++) {
			result[i] = words[i];
		}
		String lastword = words[words.length-1];
		int len = lastword.length();
		char lastChar = lastword.charAt(len-1);
		if (lastChar == 'e') {
			presPartForm = lastword.substring(0,len-1) + "ing";
		} else {
			presPartForm = lastword + "ing";
		}
		result[words.length-1] = presPartForm;
		// System.err.println ("presPart = " + presPartForm);
		return result;
	}

}
