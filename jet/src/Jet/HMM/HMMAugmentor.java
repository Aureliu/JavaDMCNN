// -*- tab-width: 4 -*-
package Jet.HMM;

import java.io.*;
import java.util.*;

/**
 *  augments an HMM using a file with a list of names and name types.  Each
 *  name is added to the HMM model as if it had appeared once in the corpus.
 *  <p>
 *  The file with the name list contains lines of the form <br>
 *    name | type                                          <br>
 *  where name can have one or more tokens, and type is PERSON,
 *  ORGANIZATION, or GPE.
 */

class HMMAugmentor {

	// static final String home = "C:/Documents and Settings/Ralph Grishman/My Documents/";
	// static final String ACEdir = home + "ACE/";
	static String originalHMMfile; // = home + "HMM/NE/ACEname04bigramHMM.txt";
	static String newHMMfile;      // = home + "HMM/NE/ACEname04bigramxHMM.txt";
	static String nameListFile;    // = ACEdir + "qq.txt";
	static HashMap<String,TreeSet<String>> stateUnigramTables 
		= new HashMap<String,TreeSet<String>>();
	static HashMap<String,HashSet<String[]>> stateBigramTables 
		= new HashMap<String,HashSet<String[]>> ();
	static HMMNameTagger tagger;
	static HMM hmm;

	/**
	 *  augment an HMM.  Takes 3 or 4 arguments:         <br>
	 *  base-hmm augmented-hmm name-list [bigram-flag]   <br>
	 *  where                                            <br>
	 *  base-hmm is the original HMM [input]             <br>
	 *  augmented-hmm is the enlarged HMM [output]       <br>
	 *  name-list is the file of name | type info        <br>
	 *  bigram-flag, if present, indicates that a bigram HMM is being updated
	 */
	 
	public static void main (String[] args) throws IOException {
		if (args.length < 3 || args.length > 4) {
			System.err.println ("HMMAugmentor requires 3 or 4 arguments:");
			System.err.println ("    base-hmm augmented-hmm name-list [bigram-flag]");
			System.exit (1);
		}
		originalHMMfile = args[0];
		newHMMfile = args[1];
		nameListFile = args[2];
		boolean bigramFlag = args.length > 3;
		// read augmentation file, collect sets
		BufferedReader reader = new BufferedReader (new FileReader(nameListFile));
		String line;
		while ((line = reader.readLine()) != null) {
			String[] ray = line.split(" \\| ");
			if (ray.length < 2) continue;
			String name = ray[0];
			String type = ray[1];
			String[] tokens = name.split(" ");
			if (type.equals("PERSON") || type.equals("LASTNAME")) {
				if (tokens.length == 1) {
					addTokenToState (tokens[0], "i-person");
				} else {
					addTokenToState (tokens[0], "b-person");
					for (int i=1; i<tokens.length-1; i++)
						addBigramToState (tokens[i-1], tokens[i], "m-person");
					addBigramToState (tokens[tokens.length-2], tokens[tokens.length-1], "e-person");
				}
			} else if (type.equals("COMPANY") || type.equals("ORGANIZATION")) {
				if (tokens.length == 1) {
					addTokenToState (tokens[0], "i-organization");
				} else {
					addTokenToState (tokens[0], "b-organization");
					for (int i=1; i<tokens.length-1; i++)
						addBigramToState (tokens[i-1], tokens[i], "m-organization");
					addBigramToState (tokens[tokens.length-2], tokens[tokens.length-1], "e-organization");
				}
			} else if (type.equals("GPE") || type.equals("CITY")) {
				if (tokens.length == 1) {
					addTokenToState (tokens[0], "i-gpe");
				} else {
					addTokenToState (tokens[0], "b-gpe");
					for (int i=1; i<tokens.length-1; i++)
						addBigramToState (tokens[i-1], tokens[i], "m-gpe");
					addBigramToState (tokens[tokens.length-2], tokens[tokens.length-1], "e-gpe");
				}
			} else {
				System.out.println ("Unexpected type:  " + type);
			}
		}
		// load HMM
		tagger = new HMMNameTagger(bigramFlag ? BigramHMMemitter.class : WordFeatureHMMemitter.class);
		tagger.load(originalHMMfile);
		hmm = tagger.nameHMM;
		/*
		// save state counts
		for (int i=0; i<stateSet.length; i++) {
			HMMstate state = hmm.getState(stateSet[i]);
			WordFeatureHMMemitter emitter = (WordFeatureHMMemitter) state.emitter;
			initialCount[i] = emitter.count;
		}
		*/
		// augment HMM

		Iterator it = stateUnigramTables.keySet().iterator();
		while (it.hasNext()) {
			String state = (String) it.next();
			TreeSet tokenSet = (TreeSet) stateUnigramTables.get(state);
			System.out.println ("For state " + state + " no of tokens = " + tokenSet.size());
			addUnigramEmitters (state, tokenSet);
		}
		/*
		Iterator bit = stateBigramTables.keySet().iterator();
		while (bit.hasNext()) {
			String state = (String) bit.next();
			HashSet bigramSet = (HashSet) stateBigramTables.get(state);
			System.out.println ("For state " + state + " no of bigrams = " + bigramSet.size());
			addBigramEmitters (state, bigramSet);
		}
		*/
		/*
		// adjust cache counts
		for (int i=0; i<stateSet.length; i++) {
			HMMstate state = hmm.getState(stateSet[i]);
			WordFeatureHMMemitter emitter = (WordFeatureHMMemitter) state.emitter;
			HashMap cacheCount = emitter.cacheCount;
			int newCount = emitter.count;
			Iterator cacheIterator = cacheCount.entrySet().iterator();
			while (cacheIterator.hasNext()) {
				Map.Entry entry = (Map.Entry) cacheIterator.next();
				String type = (String) entry.getKey();
				int oldCacheCount = ((Integer) entry.getValue()).intValue();
				int newCacheCount = (oldCacheCount * newCount) / initialCount[i];
				entry.setValue(new Integer(newCacheCount));
			}
		}
		*/
		// save HMM
		tagger.store(newHMMfile);
	}

	static void addTokenToState (String token, String state) {
		addBigramToState ("", token, state);
	}

	static void addBigramToState (String priorToken, String token, String state) {
		TreeSet<String> tokenSet = stateUnigramTables.get(state);
		if (tokenSet == null) {
			tokenSet = new TreeSet<String>();
			stateUnigramTables.put(state, tokenSet);
		}
		tokenSet.add(token);

		HashSet<String[]> bigramSet = stateBigramTables.get(state);
		if (bigramSet == null) {
			bigramSet = new HashSet<String[]>();
			stateBigramTables.put(state, bigramSet);
		}
		String[] pair = new String[] {priorToken, token};
		bigramSet.add(pair);
	}


	static void addUnigramEmitters (String state, TreeSet tokenSet) {
		HMMstate currentState = hmm.getState(state);
		if (currentState == null) {
			System.out.println ("Undefined state " + state);
			return;
		}
		Iterator it = tokenSet.iterator();
		while (it.hasNext()) {
			String token = (String) it.next();
			currentState.incrementEmitCount(token, "", 1);
		}
	}

	static void addBigramEmitters (String state, HashSet bigramSet) {
		HMMstate currentState = hmm.getState(state);
		Iterator it = bigramSet.iterator();
		while (it.hasNext()) {
			String[] pair = (String[]) it.next();
			currentState.incrementEmitCount(pair[1], pair[0], 1);
		}
	}


}
