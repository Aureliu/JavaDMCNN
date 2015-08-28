// -*- tab-width: 4 -*-
package Jet.NE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *  An in-memory implementation of the Dictionary used by the Extended Named
 *  Entity annotator.
 *
 *  @author Akira ODA
 */

public class OnMemoryDictionary extends Dictionary {
	private Map<List<String>, Set<String>> dict = new HashMap<List<String>, Set<String>>();
	private String[] tokens;

	private int maxWordCount = 0;

	public OnMemoryDictionary() {
	}

	public void load(File file) throws IOException, DictionaryFormatException {
		Reader in = new FileReader(file);
		load(in);
	}

	/**
	 * Load Dictionary from <code>java.io.Reader</code> object
	 *
	 * @param in
	 *            resource for the dictionary
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws DictionaryFormatException
	 *             if illegal format occurs
	 */

	public void load(Reader in) throws IOException, DictionaryFormatException {
		BufferedReader bin;

		if (in instanceof BufferedReader) {
			bin = (BufferedReader) in;
		} else {
			bin = new BufferedReader(in);
		}

		String line = null;
		int lineno = 1;
		while ((line = bin.readLine()) != null) {
			String[] parts = line.split("\\s+/\\s+", 2);
			if (parts.length != 2) {
				throw new DictionaryFormatException("format error at " + lineno);
			}

			String key = parts[0].replaceAll("\\\\/", "/");
			List<String> words = Arrays.asList(key.split("\\s+"));
			String cls = parts[1].intern();

			for (int i = 0; i < words.size(); i++) {
				words.set(i, words.get(i).intern());
			}

			Set<String> set = dict.get(words);
			if (set == null) {
				set = new HashSet<String>();
				set.add(cls);
				dict.put(words, set);
			} else {
				set.add(cls);
			}

			if (words.size() > maxWordCount) {
				maxWordCount = words.size();
			}

			++lineno;
		}
	}

	/**
	 *  initializes the dictionary look-up process for the text consisting of
	 *  tokens <code>tokens</code>.
	 */

	public void lookupStart(String[] tokens) {
		this.tokens = tokens;
	}

	/**
	 *  look for a dictionary entry matching the token sequence beginning at
	 *  token <code>pos</code>.
	 *
	 *  @param  pos    the position within the token array passed to lookupStart
	 *
	 *  @return the longest dictionary entry matching the token sequence, or
	 *          <code>null</code> if no entry matches
	 */

	public Dictionary.Entry lookup(int pos) {
		int length = Math.min(maxWordCount, tokens.length - pos);
		List<String> key = new ArrayList();
		int keyLength = 0;
		Set<String> foundValue = null;
		Set<String> value;

		for (int i = 0; i < length; i++) {
			key.add(tokens[pos + i]);
			value = dict.get(key);
			if (value != null) {
				foundValue = value;
				keyLength = i + 1;
			}
		}

		if (foundValue != null) {
			for (int i = key.size() - 1; i >= keyLength; i--) {
				key.remove(i);
			}
			return new Dictionary.Entry(key.size(), foundValue);
		} else {
			return null;
		}
	}
}
