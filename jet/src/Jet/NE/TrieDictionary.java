// -*- tab-width: 4 -*-
package Jet.NE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import Jet.Lex.Tokenizer;
import Jet.Tipster.Document;
import Jet.Util.Cdb;
import Jet.Util.CdbBuilder;
import Jet.Util.DoubleArrayTrie;

/**
 *  An implementation of the Dictionary used by the Extended Named
 *  Entity annotator, based on an in-memory Trie plus an on-disk
 *  data base.
 *
 *  @author Akira ODA
 */

public class TrieDictionary extends Dictionary {

	private DoubleArrayTrie trie;

	private Cdb cdb;

	private StringBuilder text;

	private int[] indexes;
	
	public TrieDictionary(File trieFile, File cdbFile) throws IOException {
		trie = new DoubleArrayTrie();
		trie.load(trieFile);
		cdb = new Cdb(cdbFile);
	}

	public TrieDictionary(String trieFilename, String cdbFilename)
			throws IOException {
		this(new File(trieFilename), new File(cdbFilename));
	}

	/**
	 *  initializes the dictionary look-up process for the text consisting of
	 *  tokens <code>tokens</code>.
	 */

	@Override
	public void lookupStart(String[] tokens) {
		text = new StringBuilder();
		indexes = new int[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			indexes[i] = text.length();
			text.append(tokens[i]);
			text.append(' ');
		}
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

	@Override
	public Dictionary.Entry lookup(int pos) {
		DoubleArrayTrie.Result result = trie.getLongestCommonPrefix(text,
				indexes[pos]);
		if (result != null) {
			try {
				byte[] key = text.subSequence(indexes[pos],
						indexes[pos] + result.getLength() - 1).toString().getBytes("UTF-8");
				byte[] value;
				Set<String> values = new HashSet<String>();
				cdb.findstart();
				while ((value = cdb.findNext(key)) != null) {
					values.add(new String(value, "UTF-8"));
				}

				int length = 0;
				while (pos + length < indexes.length
						&& indexes[pos + length] - indexes[pos] < result
								.getLength()) {
					length++;
				}

				return new Dictionary.Entry(length, values);
			} catch (UnsupportedEncodingException ex) {
				throw new RuntimeException(ex);
			}
		} else {
			return null;
		}
	}

	public static void main(String[] args) throws IOException {
		// prepare("data/wsj.ned", "ISO-8859-1", "data/wsj.ned.da",
		// "data/wsj.ned.cdb");

		String text = readText("sample.txt", "ISO-8859-1");
		testDict(text, "data/wsj.ned.da", "data/wsj.ned.cdb");
	}

	private static SortedMap<String, Set<String>> loadDict(String filename,
			String encoding) throws IOException {
		SortedMap<String, Set<String>> dict = new TreeMap<String, Set<String>>();

		BufferedReader in = new BufferedReader(new InputStreamReader(
				new FileInputStream(filename), encoding));
		String line;
		Pattern delimiter = Pattern.compile("\\s+/\\s+");

		while ((line = in.readLine()) != null) {
			String[] tmp = delimiter.split(line, 2);
			String word = tmp[0].replaceAll("\\\\/", "/").replaceAll("\\s+",
					" ").trim()
					+ " ";
			String ne = tmp[1].trim();

			Set<String> value = dict.get(word);
			if (value == null) {
				value = new TreeSet<String>();
				dict.put(word, value);
			}

			value.add(ne);
		}

		return dict;
	}

	private static DoubleArrayTrie buildTrie(SortedMap<String, Set<String>> dict) {
		char[][] keys = new char[dict.size()][];
		int i = 0;
		for (String key : dict.keySet()) {
			keys[i] = key.toCharArray();
			i++;
		}

		DoubleArrayTrie trie = new DoubleArrayTrie();
		trie.build(keys, null);

		return trie;
	}

	private static void buildCdb(SortedMap<String, Set<String>> dict,
			String dbFilename) throws IOException {
		CdbBuilder builder = new CdbBuilder(dbFilename, dbFilename + ".tmp");
		for (Map.Entry<String, Set<String>> entry : dict.entrySet()) {
			byte[] key = entry.getKey().getBytes("UTF-8");
			for (String ne : entry.getValue()) {
				byte[] value = ne.getBytes("UTF-8");
				builder.add(key, value);
			}
		}
		builder.finish();
	}

	public static String readText(String filename, String encoding)
			throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(
				new FileInputStream(filename), encoding));
		String line;
		StringBuilder builder = new StringBuilder();
		while ((line = in.readLine()) != null) {
			builder.append(line);
			builder.append("\n");
		}
		in.close();
		return builder.toString();
	}

	public static void prepare(String dictFilename, String encoding,
			String trieFilename, String cdbFilename) throws IOException {
		SortedMap<String, Set<String>> dict = loadDict(dictFilename, encoding);

		DoubleArrayTrie trie = buildTrie(dict);
		trie.save(trieFilename);
		buildCdb(dict, cdbFilename);
	}

	public static void testDict(String text, String trieFilename,
			String cdbFilename) throws IOException {

		Document doc = new Document(text);
		Tokenizer.tokenize(doc, doc.fullSpan());
		Dictionary dict = new TrieDictionary(trieFilename, cdbFilename);
		DictionaryTagger tagger = new DictionaryTagger();
		tagger.setDictionary(dict);
		tagger.annotate(doc);
		NamedEntityUtil.packNamedEntity(doc, null);

		System.out.println(doc.writeSGML("ENAMEX"));
	}
}
