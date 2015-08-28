// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.60
//Copyright(c): 2011
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Chunk;

import java.util.*;
import java.io.*;
import Jet.MaxEntModel;
import Jet.Tipster.*;
import AceJet.Ace;	// for monocase flags
import AceJet.Datum;

/**
 *  token-level maximum-entropy based name tagger.  Invoked by
 *  <code>MENameTagger</code> to do token-level training and decoding.
 *  <p>
 *  Dictionary-based strategy:  operating without a cut-off, so that every
 *  singleton word (and its context) becomes a separate feature, leads to
 *  a very large number of features, which makes training slow and, without
 *  smoothing, can produce poor performance.  Instead, this tagger uses a
 *  cut-off of 4, so only words appearing 4 or more times in the training
 *  become separate features.  In order that information from words
 *  appearing less than 4 times is used, it builds 3 dictionaries during
 *  training (WordType, WordTypeEvens, WordTypeOdds);  one from all
 *  documents, one from even-numbered documents, and one from odd-numbered
 *  documents.  This is done during one pass over the documents.  In a
 *  second pass, we train the MaxEnt model, using the information from
 *  WordTypeEvens as a feature for odd documents, and the information from
 *  WordTypeOdds as a feature for even documents.
 *
 *  @author  Ralph Grishman
 */

public class MaxEntNE extends TokenClassifier {

	MaxEntModel model;
	String[] state;
	Map<String, String> cache = new HashMap<String, String>();

	/**
	 *  create a new maximum entropy tagger.
	 */

	public MaxEntNE () {
		model = new MaxEntModel();
		model.setIterations(80);
	}

	/**
	 *  initializes the training process for the tagger.
	 *
	 *  @param  featureFile  the file into which the features will be written
	 */

	public void resetForTraining (String featureFile) {
		model.initializeForTraining (featureFile);
		trainingDocCount = 0;
	}

	public void newDocument () {
		cache.clear();
		trainingDocCount++;
	}

	/**
	 *  pass number during training.  In pass 1, we build the dictionaries
	 *  (wordType, wordTypeEvens, wordTypeOdds).  In pass 2, we train the
	 *  MaxEnt model.
	 */
	public static int pass = 0;
	Map<String, String> wordType = new HashMap<String, String>();
	Map<String, String> wordTypeEvens = new HashMap<String, String>();
	Map<String, String> wordTypeOdds = new HashMap<String, String>();
	static int trainingDocCount = 0;

	/**
	 *  train the model on a sequence of words from Document doc.
	 *
	 *  @param doc      the document containing the word sequence
	 *  @param tokens   the token annotations for these words
	 *  @param tags     the token-level name tags for these words
	 */

	public void train (Document doc, Annotation[] tokens, String[] tags) {
		int nTokens = tokens.length;
		String[] words = new String[nTokens];
		if (useOnoma)  onomaType = new String[nTokens];
		for (int iToken = 0; iToken < nTokens; iToken++) {
			words[iToken] = doc.text(tokens[iToken]).trim();
			if (useOnoma)  onomaType[iToken] = onomaFeature(doc, tokens[iToken]);
		}
		String priorTag = "other";
		for (int iToken = 0; iToken < nTokens; iToken++) {
			if (pass == 1) {
				recordWord (words[iToken], tags[iToken]);
			} else {
				Datum d = NEfeatures (iToken, words, tokens, priorTag, doc);
				d.setOutcome (tags[iToken]);
				model.addEvent (d);
				priorTag = tags[iToken];
				addToCache(words[iToken], tags[iToken]);
			}
		}
	}

	/**
	 *  during pass 1, record a word's tag in the dictionaries (wordType and
	 *  either wordTypeOdds or wordTypeEvens).
	 */

	private void recordWord (String word, String tag) {
		String token = word.toLowerCase();
		if (tag.charAt(1) == '-') tag=tag.substring(2);
		char tagChar = tag.charAt(0);
		if (tag.equals("other")) tagChar = 'x';
		recordWord (token, tagChar, wordType);
		if (trainingDocCount % 2 == 1) {
			recordWord (token, tagChar, wordTypeOdds);
		} else {
			recordWord (token, tagChar, wordTypeEvens);
		}
		if (!Ace.monocase && tagChar == 'p' && Character.isLowerCase(word.charAt(0)))
			System.out.println ("Lower case person token " + token);

	}

	private void recordWord (String token, char tagChar, Map<String, String> typeTable) {
		String type = typeTable.get(token);
		if (type == null) {
			typeTable.put(token, "" + tagChar);
		} else if (type.indexOf(tagChar) < 0) {
			type = type + tagChar;
			typeTable.put(token, type);
		}
	}

	/**
	 *  returns a Datum encoding the features associated with word[i].
	 */

	private Datum NEfeatures (int i, String[] words, Annotation[] tokens, String priorTag,
	                          Document doc) {
		Datum d = new Datum();
		String prior1 = (i > 0) ? words[i-1].toLowerCase() : "^";
		String prior2 = (i > 1) ? words[i-2].toLowerCase() : "^";
		String current = words[i].toLowerCase();
		String next = (i >= words.length -1) ? "$" : words[i+1].toLowerCase();

		String next2 = (i >= words.length - 2) ? "$" : words[i+2].toLowerCase();

		d.addFV ("p", prior1 + ":" + priorTag);
		d.addFV ("c", current + ":" + priorTag);
		d.addFV ("n", next + ":" + priorTag);

		d.addFV ("n2", next2 + ":" + priorTag);
		
		String cf = wordFeature(words[i], tokens[i].get("case")=="forcedCap");
		String pf = "^";
		String nf = "$";
		if (i > 0)
			pf = wordFeature(words[i-1], tokens[i-1].get("case")=="forcedCap");
		if (i < words.length -1)
			nf = wordFeature(words[i+1], tokens[i+1].get("case")=="forcedCap");
		
		d.addFV("pcnf", pf + cf + nf);
		d.addFV (Ace.monocase ? "cfmono" : "cf",
		         wordFeature(words[i], tokens[i].get("case")=="forcedCap") + ":" + priorTag);
		d.addFV ("pt", priorTag);
		String cacheValue = (String) cache.get(words[i]);
		if (cacheValue == null) cacheValue = "";
		d.addFV ("ca", cacheValue + ":" + priorTag);
		d.addFV ("pc", prior1 + ":" + words[i] + priorTag);
		d.addFV ("p2", prior2 + ":" + prior1 + priorTag);
		if (i > 0)
			d.addFV (Ace.monocase ? "pfmono" : "pf",
			         wordFeature(words[i-1], tokens[i-1].get("case")=="forcedCap") + ":" + priorTag);
		else
			d.addFV ("pf", "^:" + priorTag);
		if (i < words.length -1)
			d.addFV (Ace.monocase ? "nfmono" : "nf",
			         wordFeature(words[i+1], tokens[i+1].get("case")=="forcedCap") + ":" + priorTag);
		else
			d.addFV ("nf", "$:" + priorTag);
		d.addFV ("tt", typeFeature(words[i]) + ":" + priorTag);
		d.addFV ("w", words[i] +  ":" + priorTag);

		if (useOnoma) {
			if (onomaType[i] != null)
				d.addFV ("onoma", onomaType[i]);
			// special features for <city>, <country> and <city> - based
			// (modified 10 Aug 2013 at request of SRI to not crash on  0-length words)
			if (words[i].length() > 0 && 
			    Character.isUpperCase(words[i].charAt(0)) && i < words.length - 2) {
				int j = i + 1;
				while (words[j].length() > 0 && 
				       Character.isUpperCase(words[j].charAt(0)) && j < words.length - 2) 
					j = j + 1;
				String onomaType2 = onomaType[j + 1];
				if (words[j].equals(",") && (onomaType2 == "country" || onomaType2 == "usstate"))
					d.addF ("cityContext");
				else if (words[j].equals("-") && words[j+1].equals("based"))
					d.addF ("-basedContext");
			}
		}

		// add word cluster features according to (Miller et al., 2004)
		if (useWordClusters) {
			prior1 = (i > 0) ? words[i-1] : "^";
			current = words[i];
			next = (i >= words.length -1) ? "$" : words[i+1];
			d.addFV("p1px4", getWordClusterPrefix(prior1, 4));
			d.addFV("p1px6", getWordClusterPrefix(prior1, 6));
			d.addFV("p1px10", getWordClusterPrefix(prior1, 10));
			d.addFV("p1px20", getWordClusterPrefix(prior1, 20));
			
			d.addFV("cpx4", getWordClusterPrefix(current, 4));
			d.addFV("cpx6", getWordClusterPrefix(current, 6));
			d.addFV("cpx10", getWordClusterPrefix(current, 10));
			d.addFV("cpx20", getWordClusterPrefix(current, 20));
			
			d.addFV("npx4", getWordClusterPrefix(next, 4));
			d.addFV("npx6", getWordClusterPrefix(next, 6));
			d.addFV("npx10", getWordClusterPrefix(next, 10));
			d.addFV("npx20", getWordClusterPrefix(next, 20));
		}
		
		return d;
	}

	/**
	 *  return the type of <code>word</code> based on the type dictionary
	 *  currently in use:  wordType when decoding, wordTypeOdds when
	 *  training on even numbered documents, wordTypeEvens when training on
	 *  odd numbered documents.  If the word does not appear in the
	 *  type dictionary, returns "OOV".
	 */

	private String typeFeature (String word) {
		String wordL = word.toLowerCase();
		Map<String, String> typeTable;
		if (trainingDocCount < 0) {
			// tagging new data:  use combined type information
			typeTable = wordType;
		} else if (trainingDocCount % 2 == 0) {
			// even document:  use types from odds
			typeTable = wordTypeOdds;
		} else {
			// odd document:  use types from evens
			typeTable = wordTypeEvens;
		}
		if (typeTable.get(wordL) == null)
			return "OOV";
		else
			return typeTable.get(wordL);
	}

	boolean useOnoma = true;

	/**
	 *  if <code>token</code> is the start of a name from a dictionary
	 *  (recorded as an 'onoma' annotation), return the type of the name,
	 *  else return <code>null</code>.
	 */

	private String onomaFeature (Document doc, Annotation token) {
		int posn = token.start();
		Vector<Annotation> v = doc.annotationsAt(posn, "onoma");
		if (v == null || v.size() == 0)
			return null;
		Annotation a = v.get(0);
		return (String) a.get("type");
	}

	/**
	 *  record in the name cache that <code>word</code> has appeared with
	 *  name type <code>tag</code>.
	 */

	private void addToCache (String word, String tag) {
		if (tag.equals("other")) return; /*
										   if (word.equals("-") || word.equalsIgnoreCase("of") ||
										   word.equalsIgnoreCase("the") || word.equalsIgnoreCase("and"))
										   return; */
		if (tag.charAt(1) == '-') tag=tag.substring(2);
		char tagChar = tag.charAt(0);
		String cacheValue = (String) cache.get(word);
		if (cacheValue == null) {
			cacheValue = "" + tagChar;
			cache.put(word, cacheValue);
		} else if (cacheValue.indexOf(tagChar) < 0) {
			cacheValue = cacheValue + tagChar;
			cache.put(word, cacheValue);
		}
	}

	/**
	 *  create a max ent model (at the end of training).
	 */

	public void createModel () {
		model.buildModel();
	}

	/**
	 *  store the information required for the MaxEntNE tagger to file
	 *  <CODE>fileName</CODE>.  This information is the table of
	 *  types for each word, and the parameters of the maximum entropy model.
	 */

	public void store (String fileName) {
		try {
			store (new BufferedWriter (new FileWriter (fileName)));
		} catch (IOException e) {
			System.err.println ("Error in MaxEntNE.store: " + e);
			System.exit(1);
		}
	}

	/**
	 *  write the information required for the MaxEntNE tagger to BufferedWriter
	 *  <CODE>writer</CODE>.  This information is the table of
	 *  types for each word, and the parameters of the maximum entropy model.
	 */

	public void store (BufferedWriter writer) {
		try {
			for (String word : wordType.keySet()) {
				writer.write (word + " " + wordType.get(word));
				writer.newLine ();
			}
			writer.write ("endWordType");
			writer.newLine ();
		} catch (IOException e) {
			System.err.println ("Error in MaxEntNE.store: " + e);
			System.exit (1);
		}
		model.saveModel (writer);
	}

	/**
	 *  load the information required for the MaxEntNE tagger from file
	 *  <CODE>fileName</CODE>.  This information is the table of
	 *  types for each word, and the parameters of the maximum entropy model.
	 */

	public void load (String fileName) {
		try {
			load (new BufferedReader (new FileReader (fileName)));
		} catch (IOException e) {
			System.err.println ("Error in MaxEntNE.load: " + e);
			System.exit(1);
		}
	}

	/**
	 *  load the information required for the MaxEntNE tagger from
	 *  <CODE>reader</CODE>.  This information is the table of
	 *  types for each word, and the parameters of the maximum entropy model.
	 */

	public void load (BufferedReader reader) {
		//- read type table
		String line;
		try {
			while (!(line = reader.readLine()).equals("endWordType")) {
				String[] tags = line.split("\\s+");
				if (tags.length != 2) {
					System.err.println ("MaxEntNE.load:  invalid line " + line);
					continue;
				}
				wordType.put(tags[0], tags[1]);
			}
		} catch (IOException e) {
			System.err.println ("Error in MaxEntNE.load: " + e);
			System.exit(1);
		}
		model.loadModel (reader);
	}

	/**
	 *  assign the best tag for each token using a simple deterministic
	 *  left-to-right tagger (which may not find the most probable path).
	 */

	public String[] simpleDecoder (Document doc, Annotation[] tokens) {
		int nTokens = tokens.length;
		String[] words = new String[nTokens];
		String[] tags = new String[nTokens];
		for (int iToken = 0; iToken < nTokens; iToken++) {
			words[iToken] = doc.text(tokens[iToken]).trim();
		}
		String priorTag = "other";
		for (int iToken = 0; iToken < nTokens; iToken++) {
			Datum d = NEfeatures (iToken, words, tokens, priorTag, doc);
			tags[iToken] = model.bestOutcome(d);
			addToCache(words[iToken], tags[iToken]);
			priorTag = tags[iToken];
		}
		return tags;
	}

	public static double otherOffset = 0.;

	String[] onomaType;

	/**
	 *  assign the best tag for each token using a Viterbi decoder.
	 *
	 *  @return  an array whose i-th element is the tag of the i-th token
	 */

	public String[] viterbi (Document doc, Annotation[] tokens) {
		trainingDocCount = -1;
		int nTokens = tokens.length;
		if (nTokens == 0)
			return new String[0];
		String[] words = new String[nTokens];
		if (useOnoma)  onomaType = new String[nTokens];
		for (int iToken = 0; iToken < nTokens; iToken++) {
			words[iToken] = doc.text(tokens[iToken]).trim();
			if (useOnoma)  onomaType[iToken] = onomaFeature(doc, tokens[iToken]);
		}
		int nStates = model.getNumOutcomes();
		state = new String[nStates];
		for (int i=0; i<nStates; i++) {
			state[i] = model.getOutcome(i);
		}
		double[][] prob = new double[nTokens][nStates];
		int[][] prior = new int [nTokens][nStates];
		NameConstraints constraints = new NameConstraints (doc, tokens, state);
		int[] path = new int[nTokens];
		double IMPOSSIBLE = -1000000.;

		// compute probabilities for first token (iToken == 0)
		for (int iState = 0; iState < nStates; iState++) {
			if (state[iState].charAt(0) == 'I' ||
			    !constraints.allowedState(0, iState)) {
				prob[0][iState] = IMPOSSIBLE;
			} else {
				Datum d = NEfeatures (0, words, tokens, "other", doc);
				double[] outcome = model.getOutcomeProbabilities(d);
				double p = Math.log(outcome[iState]);
				if (state[iState].equals("other")) p += otherOffset;
				prob[0][iState] = p;
			}
		}
		// compute probabilities for subsequent tokens
		for (int iToken = 1; iToken < nTokens; iToken++) {
			for (int iState = 0; iState < nStates; iState++) {
				prob[iToken][iState] = IMPOSSIBLE;
				prior[iToken][iState] = -1;
			}
			for (int iPrior = 0; iPrior < nStates; iPrior++) {
				Datum d = NEfeatures (iToken, words, tokens, state[iPrior], doc);
				double[] outcome = model.getOutcomeProbabilities(d);
				for (int iState = 0; iState < nStates; iState++) {
					double p = Math.log(outcome[iState]);
					if (state[iState].equals("other")) p += otherOffset;
					if (allowedTransition(iPrior, iState) && 
					    constraints.allowedState(iToken, iState) &&
					    prob[iToken-1][iPrior] + p > prob[iToken][iState]) {
						prob[iToken][iState] = prob[iToken-1][iPrior] + p;
						prior[iToken][iState] = iPrior;
					}
				}
			}
		}
		// find final token with highest probability
		double bestProb = IMPOSSIBLE;
		int bestState = -1;
		for (int iState = 0; iState < nStates; iState++) {
			if (prob[nTokens - 1][iState] > bestProb) {
				bestProb = prob[nTokens - 1][iState];
				bestState = iState;
			}
		}
		if (bestState < 0) {
			System.err.println ("No valid path.");
			return null;
		}
		path[nTokens - 1] = bestState;
		// trace path back to first token
		for (int iToken = nTokens - 2; iToken >= 0; iToken--) {
			int iPrior = prior[iToken+1][path[iToken+1]];
			if (iPrior < 0) {
				System.err.println ("No valid path.");
				return null;
			}
			path[iToken] = iPrior;
		}
		String[] tags = new String[nTokens];
		for (int iToken = 0; iToken < nTokens; iToken++) {
			tags[iToken] = state[path[iToken]];
			addToCache(words[iToken], tags[iToken]);
		}
		return tags;
	}

	private boolean allowedTransition (int iPrior, int iState) {
		String priorState = state[iPrior];
		String currentState = state[iState];
		if (currentState.substring(0,2).equals("I-"))
			return priorState.substring(1).equals(currentState.substring(1));
		else
			return true;
	}

	/**
	 *  returns a word feature based on the 'shape' of a word (all caps,
	 *  all lower case, hyphenated, etc.).  Based on the feature set from
	 *  NYMBLE.
	 */

	static String wordFeature (String word, boolean forcedCap) {
		int len = word.length();
		boolean allDigits = true;
		boolean allCaps = true;
		boolean initCap = true;
		boolean allLower = true;
		boolean hyphenated = true;
		boolean abbrev = true;
		for (int i=0; i<len; i++) {
			char c = word.charAt(i);
			if (!Character.isDigit(c)) allDigits = false;
			if (!Character.isUpperCase(c)) allCaps = false;
			if (!Character.isLowerCase(c)) allLower = false;
			if (!(Character.isLetter(c) || c == '-')) hyphenated = false;
			if (!(Character.isLetter(c) || c == '.')) abbrev = false;
			if ((i == 0 && !Character.isUpperCase(c)) ||
			    (i > 0  && !Character.isLowerCase(c))) initCap = false;
		}
		if (allDigits) {
			if (len == 2) {
				return "twoDigitNum";
			} else if (len == 4) {
				return "fourDigitNum";
			} else {
				return "otherNum";
			}
		} else if (allCaps) {
			return "allCaps";
		} else if (forcedCap) {
			return "forcedCap";
		} else if (initCap) {
			return "initCap";
		} else if (allLower) {
			return "lowerCase";
			// any mix of letters and periods counts as an abbrev
		} else if (abbrev) {
			return "abbrev";
			// for POS
		} else if (hyphenated) {
			return "hyphenated";
		} else return "other";
	}

	static Map<String, String> word2cluster = new HashMap<String, String>();

	static boolean useWordClusters = false;

	/**
	 * loads word clusters and builds map from word to cluster
	 * @throws IOException
	 */

	public static void loadWordClusters (String wordClusterFile) throws IOException {
		System.out.println("Loading word clusters from " + wordClusterFile);
		File paths = new File(wordClusterFile);
		BufferedReader rdr = new BufferedReader(new FileReader(paths));
		String sline;
		while ( (sline = rdr.readLine()) != null) {
			String[] tokens = sline.split("\t");
			if (tokens.length >= 2)
				word2cluster.put(tokens[1], tokens[0]);
		}
		useWordClusters = true;
	}
	
	private static String getWordClusterPrefix(String word, int bits) {
		String wc = new String();
		if (word2cluster.containsKey(word)) {
			wc = word2cluster.get(word);
			if (wc.length() >= bits) {
				wc = wc.substring(0, bits);
			}
		} else {
			wc = "nil";
		}
		return wc;
	}

}
