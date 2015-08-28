package Jet.Util;

import java.io.File;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;

import junit.framework.TestCase;

/**
 * Unit test for DoubleArrayTrie
 * 
 * @author Akira ODA
 */
public class DoubleArrayTrieTest extends TestCase {
	@Override
	public void tearDown() throws Exception {
		System.gc();
		File file = new File("test.da");
		if (file.exists()) {
			file.deleteOnExit();
		}
	}

	/**
	 * Tests common prefix search without value array.
	 */
	public void testCommonPrefixSearchWithoutValues() throws Exception {
		final int n = 1000;
		Random r = new Random();
		SortedSet<String> stringKeys = makeRandomKeys(r, n);
		char[][] keys = stringSetToCharArray(stringKeys);

		DoubleArrayTrie trie = new DoubleArrayTrie();
		boolean result = trie.build(keys, null);
		assertEquals(true, result);

		int i = 0;
		while (i < n) {
			String key = makeRandomString(r, 100);
			List<DoubleArrayTrie.Result> expected = simpleCommonPrefixSearch(
					stringKeys, key, null);
			if (expected.size() == 0) {
				continue;
			}
			List<DoubleArrayTrie.Result> actual = trie.commonPrefixSearch(key);

			assertEquals(expected, actual);
			i++;
		}
	}

	/**
	 * Tests common prefix search with value array.
	 */
	public void testCommonPrefixSearchWithValues() throws Exception {
		final int n = 1000;
		Random r = new Random();
		SortedSet<String> strKeys = makeRandomKeys(r, n);
		char[][] keys = stringSetToCharArray(strKeys);
		int[] values = new int[n];
		for (int i = 0; i < n; i++) {
			values[i] = r.nextInt(65536);
		}

		DoubleArrayTrie trie = new DoubleArrayTrie();
		trie.build(keys, values);

		int i = 0;
		while (i < n) {
			String key = makeRandomString(r, 100);
			List<DoubleArrayTrie.Result> expected = simpleCommonPrefixSearch(
					strKeys, key, values);
			if (expected.size() == 0) {
				continue;
			}

			List<DoubleArrayTrie.Result> actual = trie.commonPrefixSearch(key);
			assertEquals(expected, actual);
			i++;
		}
	}

	public void testCommonPrefixWithOffset() throws Exception {
		final int n = 1000;
		Random r = new Random();
		SortedSet<String> stringKeys = makeRandomKeys(r, n);
		char[][] keys = stringSetToCharArray(stringKeys);

		DoubleArrayTrie trie = new DoubleArrayTrie();
		boolean result = trie.build(keys, null);
		assertEquals(true, result);

		int i = 0;
		while (i < n) {
			String key = makeRandomString(r, 100);
			List<DoubleArrayTrie.Result> expected = simpleCommonPrefixSearch(
					stringKeys, key, null);
			if (expected.size() == 0) {
				continue;
			}

			String prefix = makeRandomString(r, 10);
			int offset = prefix.length();
			String key2 = prefix + key;
			List<DoubleArrayTrie.Result> actual = trie.commonPrefixSearch(key2,
					offset, key2.length() - offset, 0);

			assertEquals(expected, actual);
			i++;
		}
	}

	/**
	 * Tests save and load method.
	 */
	public void testSaveAndLoad() throws Exception {
		final int n = 1000;
		Random r = new Random();
		SortedSet<String> stringKeys = makeRandomKeys(r, n);
		char[][] keys = stringSetToCharArray(stringKeys);
		DoubleArrayTrie trie = new DoubleArrayTrie();
		trie.build(keys, null);
		trie.save("test.da");

		assertTrue(new File("test.da").exists());

		trie = new DoubleArrayTrie();
		trie.load("test.da");
		int i = 0;
		while (i < n) {
			String key = makeRandomString(r, 100);
			List<DoubleArrayTrie.Result> expected = simpleCommonPrefixSearch(
					stringKeys, key, null);
			if (expected.size() == 0) {
				continue;
			}
			List<DoubleArrayTrie.Result> actual = trie.commonPrefixSearch(key);
			assertEquals(expected, actual);
			i++;
		}
	}

	/**
	 * Tests exact match search.
	 */
	public void testExactMatchSearch() throws Exception {
		final int n = 1000;
		Random r = new Random();
		SortedSet<String> stringKeys = makeRandomKeys(r, n);
		char[][] keys = stringSetToCharArray(stringKeys);
		DoubleArrayTrie trie = new DoubleArrayTrie();
		trie.build(keys, null);

		for (int i = 0; i < n; i++) {
			int actual = trie.exactMatchSearch(CharBuffer.wrap(keys[i]));
			assertEquals(i, actual);
		}
	}

	/**
	 * Tests getLongestCommonPrefix.
	 */
	public void testGetLongestCommonPrefix() throws Exception {
		final int n = 1000;
		Random r = new Random();
		SortedSet<String> strKeys = makeRandomKeys(r, n);
		char[][] keys = stringSetToCharArray(strKeys);
		DoubleArrayTrie trie = new DoubleArrayTrie();
		trie.build(keys, null);

		for (int i = 0; i < n; i++) {
			String key = makeRandomString(r, 100);
			DoubleArrayTrie.Result expected = getLongestCommonPrefix(strKeys,
					key);
			DoubleArrayTrie.Result actual = trie.getLongestCommonPrefix(key, 0,
					key.length(), 0);
			assertEquals(expected, actual);
		}
	}

	private String makeRandomString(Random r, int l) {
		int len = 1 + r.nextInt(l);
		char[] str = new char[len];
		for (int i = 0; i < len; i++) {
			str[i] = (char) (32 + r.nextInt(128 - 32));
		}
		return new String(str);
	}

	private List<DoubleArrayTrie.Result> simpleCommonPrefixSearch(
			SortedSet<String> keys, String str, int[] values) {
		ArrayList<DoubleArrayTrie.Result> result = new ArrayList<DoubleArrayTrie.Result>();
		int i = 0;
		for (String key : keys) {
			if (str.startsWith(key)) {
				if (values == null) {
					result.add(new DoubleArrayTrie.Result(i, key.length()));
				} else {
					result.add(new DoubleArrayTrie.Result(values[i], key
							.length()));
				}
			}
			i++;
		}
		return result;
	}

	private DoubleArrayTrie.Result getLongestCommonPrefix(
			SortedSet<String> keys, String str) {
		int i = 0;
		int length = -1;
		int index = -1;
		for (String key : keys) {
			if (str.startsWith(key) && key.length() > length) {
				index = i;
				length = key.length();
			}
			i++;
		}

		if (length > 0) {
			return new DoubleArrayTrie.Result(index, length);
		} else {
			return null;
		}
	}

	private SortedSet<String> makeRandomKeys(Random r, int n) {
		SortedSet<String> keys = new TreeSet<String>();
		while (keys.size() < n) {
			keys.add(makeRandomString(r, 5));
		}

		return keys;
	}

	private char[][] stringSetToCharArray(SortedSet<String> strings) {
		char[][] result = new char[strings.size()][];
		int i = 0;
		for (String str : strings) {
			result[i++] = str.toCharArray();
		}
		return result;
	}
}
