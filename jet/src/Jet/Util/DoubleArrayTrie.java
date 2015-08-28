// -*- tab-width: 4 -*-
package Jet.Util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.List;

/**
 * DoubleArrayTrie provides faster trie implementation which is based
 * <i>Double-Array Structure</i>.
 * 
 * <ul>
 * <li> Aoe, J. <cite>An Efficient Digital Search Algorithm by Using a
 * Double-Array Structure.</cite> <strong>IEEE Transactions on Software
 * Engineering.</strong> Vol. 15, 9 (Sep 1989). pp. 1066-1077. </li>
 * </ul>
 * 
 * @author Akira ODA
 */
public class DoubleArrayTrie {
	private static class Node {
		int code;

		int depth;

		int left;

		int right;
	}

	public static final class Result {
		private int value;

		private int length;

		Result(int value, int length) {
			this.value = value;
			this.length = length;
		}

		public int getLength() {
			return length;
		}

		public int getValue() {
			return value;
		}

		@Override
		public int hashCode() {
			final int PRIME = 31;
			int result = 1;
			result = PRIME * result + length;
			result = PRIME * result + value;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Result) {
				Result other = (Result) obj;
				return value == other.value && length == other.length;
			} else {
				return false;
			}
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("[value=");
			builder.append(value);
			builder.append(", length=");
			builder.append(length);
			builder.append("]");
			return builder.toString();
		}
	}

	int[] base = new int[0];

	int[] check = new int[0];

	MappedByteBuffer map = null;

	boolean[] used;

	int size;

	int alloc_size = 0;

	char[][] keys;

	int[] length;

	int[] val;

	int next_check_pos;

	int progress;

	int error;

	private void resize(int newSize) {
		base = resize(base, alloc_size, newSize);
		check = resize(check, alloc_size, newSize);
		used = resize(used, alloc_size, newSize);
		alloc_size = newSize;
	}

	private <T> T resize(T array, int oldSize, int newSize) {
		T newArray = (T) Array.newInstance(array.getClass().getComponentType(), newSize);
		System.arraycopy(array, 0, newArray, 0, Math.min(oldSize, newSize));
		return newArray;
	}

	private int fetch(Node parent, List<Node> siblings) {
		if (error < 0) {
			return 0;
		}

		int prev = 0;
		for (int i = parent.left; i < parent.right; i++) {
			if (keys[i].length < parent.depth) {
				continue;
			}

			char[] tmp = keys[i];
			int cur = 0;

			if (keys[i].length != parent.depth) {
				cur = tmp[parent.depth] + 1;
			}

			if (prev > cur) {
				error = -3;
				return 0;
			}

			if (cur != prev || siblings.isEmpty()) {
				Node tmpNode = new Node();
				tmpNode.depth = parent.depth + 1;
				tmpNode.code = cur;
				tmpNode.left = i;
				if (!siblings.isEmpty()) {
					siblings.get(siblings.size() - 1).right = i;
				}

				siblings.add(tmpNode);
			}

			prev = cur;
		}

		if (!siblings.isEmpty()) {
			siblings.get(siblings.size() - 1).right = parent.right;
		}

		return siblings.size();
	}

	private int insert(List<Node> siblings) {
		if (error < 0) {
			return 0;
		}

		int begin = 0;
		int pos = Math.max(siblings.get(0).code + 1, next_check_pos) - 1;
		int nonzero_num = 0;
		int first = 0;

		if (alloc_size <= pos) {
			resize(pos + 1);
		}

		NEXT: while (true) {
			++pos;

			if (alloc_size <= pos) {
				resize(pos + 1);
			}

			if (getCheck(pos) != 0) {
				++nonzero_num;
				continue;
			} else if (first == 0) {
				next_check_pos = pos;
				first = 1;
			}

			begin = pos - siblings.get(0).code;
			if (alloc_size <= (begin + siblings.get(siblings.size() - 1).code)) {
				resize((int) (alloc_size * Math.max(1.05, 1.0 * keys.length / progress)));
			}

			if (used[begin]) {
				continue;
			}

			for (int i = 1; i < siblings.size(); i++) {
				if (getCheck(begin + siblings.get(i).code) != 0) {
					continue NEXT;
				}
			}

			break;
		}

		if (1.0 * nonzero_num / (pos - next_check_pos + 1) >= 0.95) {
			next_check_pos = pos;
		}

		used[begin] = true;
		size = Math.max(size, begin + (int) siblings.get(siblings.size() - 1).code + 1);
		for (int i = 0; i < siblings.size(); i++) {
			setCheck(begin + siblings.get(i).code, begin);
		}

		for (int i = 0; i < siblings.size(); i++) {
			List<Node> new_siblings = new ArrayList<Node>();
			if (fetch(siblings.get(i), new_siblings) == 0) {
				int x;

				if (val != null) {
					x = -val[siblings.get(i).left] - 1;
				} else {
					x = -siblings.get(i).left - 1;
				}
				setBase(begin + siblings.get(i).code, x);

				if (val != null && -val[siblings.get(i).left] - 1 >= 0) {
					error = -2;
					return 0;
				}

				++progress;
			} else {
				int h = insert(new_siblings);
				setBase(begin + siblings.get(i).code, h);
			}
		}

		return begin;
	}

	/**
	 * Builds a double array trie. The specified array of key is <strong>must</strong>
	 * be sorted. If it is not sorted, the results is undefined.
	 * 
	 * @param key
	 *            array of each word
	 * @param value
	 *            array of each value.
	 * @return <tt>true</tt> if build double array completed without errors.
	 */
	public boolean build(char[][] key, int[] value) {
		this.keys = key;
		this.val = value;
		progress = 0;
		used = new boolean[alloc_size];

		length = new int[key.length];
		for (int i = 0; i < key.length; i++) {
			length[i] = key[i].length;
		}

		resize(8192);

		setBase(0, 1);
		next_check_pos = 0;

		Node rootNode = new Node();
		rootNode.left = 0;
		rootNode.right = key.length;
		rootNode.depth = 0;

		List<Node> siblings = new ArrayList<Node>();
		fetch(rootNode, siblings);
		insert(siblings);

		size += (1 << 8 * (Character.SIZE / 8)) + 1;
		if (size >= alloc_size) {
			resize(size);
		}

		used = null;

		return error >= 0;
	}

	/**
	 * Saves double array into file
	 * 
	 * @param filename
	 *            the named of file to save double array
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public void save(String filename) throws IOException {
		RandomAccessFile file = null;
		FileChannel channel = null;

		try {
			file = new RandomAccessFile(filename, "rw");
			int size = alloc_size * (Integer.SIZE * 2 / 8);
			file.setLength(size);
			channel = file.getChannel();
			MappedByteBuffer map = channel.map(MapMode.READ_WRITE, 0, size);
			map.order(ByteOrder.LITTLE_ENDIAN);
			for (int i = 0; i < alloc_size; i++) {
				map.putInt(getBase(i));
			}
			for (int i = 0; i < alloc_size; i++) {
				map.putInt(getCheck(i));
			}
			file.setLength(map.position());
		} finally {
			if (channel != null) {
				channel.close();
			} else if (file != null) {
				file.close();
			}
		}
	}

	/**
	 * Loads double array from file.
	 * 
	 * @param filename
	 *            the system depended filename
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public void load(String filename) throws IOException {
		load(new File(filename));
	}

	/**
	 * Loads double array from file.
	 * 
	 * @param file
	 *            the file object
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public void load(File file) throws IOException {
		RandomAccessFile raf = null;
		FileChannel channel = null;

		try {
			raf = new RandomAccessFile(file, "r");
			channel = raf.getChannel();
			map = channel.map(MapMode.READ_ONLY, 0, raf.length());
			map.order(ByteOrder.LITTLE_ENDIAN);
			alloc_size = (int) (raf.length() / ((Integer.SIZE + Integer.SIZE) / 8));
		} finally {
			IOUtils.closeQuietly(channel);
			IOUtils.closeQuietly(raf);
		}
	}

	/**
	 * Searches the value to which be associated with the specified key.
	 * 
	 * @param key
	 *            the string to be searched for
	 * @param offset
	 *            starting position in the specified key
	 * @param length
	 *            length of the specified key
	 * @param nodePos
	 *            starting position for search in double array node
	 * @return the value to which be as sociated with the specified key, or -1
	 *         if double array does not contains specified key.
	 */
	public int exactMatchSearch(CharSequence key, int offset, int length, int nodePos) {
		int b = getBase(nodePos);
		int p;

		int result = -1;

		for (int i = 0; i < length; ++i) {
			p = b + key.charAt(offset + i) + 1;
			if (b == getCheck(p)) {
				b = getBase(p);
			} else {
				return result;
			}
		}

		p = b;
		int n = getBase(p);
		if (b == getCheck(p) && n < 0) {
			result = -n - 1;
		}

		return result;
	}

	/**
	 * Searches the value to which be associated with the specified key.
	 * 
	 * @param key
	 *            the string to be searched for
	 * @param offset
	 *            starting position in the specified key
	 * @param length
	 *            length of the specified key
	 * @return the value to which be as sociated with the specified key, or -1
	 *         if double array does not contains specified key.
	 */
	public int exactMatchSearch(CharSequence key, int offset, int length) {
		return exactMatchSearch(key, offset, length, 0);
	}

	/**
	 * Searches the value to which be associated with the specified key.
	 * 
	 * @param key
	 *            the string to be searched for
	 * @param offset
	 *            starting position in the specified key
	 * @return the value to which be as sociated with the specified key, -1 if
	 *         double array does not contains specified key.
	 */
	public int exactMatchSearch(CharSequence key, int offset) {
		return exactMatchSearch(key, offset, key.length() - offset, 0);
	}

	/**
	 * Searches the value to which be associated with the specified key.
	 * 
	 * @param key
	 *            the string to be searched for
	 * @return the value to which be as sociated with the specified key, -1 if
	 *         double array does not contains specified key.
	 */
	public int exactMatchSearch(CharSequence key) {
		return exactMatchSearch(key, 0, key.length(), 0);
	}

	/**
	 * Searches the value to which be associated with the specified key.
	 * 
	 * @param key
	 *            the string to be searched for
	 * @param offset
	 *            starting position in the key
	 * @param length
	 *            length of key
	 * @param nodePos
	 *            starting position for search in double array node.
	 * @return list of integer value which is associated with matched keys if
	 *         values is specified. Otherwise, indexes of matched keys.
	 */
	public List<Result> commonPrefixSearch(CharSequence key, int offset, int length, int nodePos) {

		int b = getBase(nodePos);
		int n;
		int p;

		List<Result> result = new ArrayList<Result>();

		for (int i = 0; i < length; ++i) {
			p = b;
			n = getBase(p);
			if (b == getCheck(p) && n < 0) {
				result.add(new Result(-n - 1, i));
			}

			p = b + key.charAt(offset + i) + 1;
			if (b == getCheck(p)) {
				b = getBase(p);
			} else {
				return result;
			}
		}

		p = b;
		n = getBase(p);

		if (b == getCheck(p) && n < 0) {
			result.add(new Result(-n - 1, length));
		}

		return result;
	}

	/**
	 * Searches strings which are specified key's prefix.
	 * 
	 * @param key
	 *            the string to be searched for
	 * @param offset
	 *            starting position in the key
	 * @param length
	 *            length of key
	 * @return list of integer value which is associated with matched keys if
	 *         values is specified. Otherwise, indexes of matched keys.
	 */
	public List<Result> commonPrefixSearch(CharSequence key, int offset, int length) {
		return commonPrefixSearch(key, offset, length, 0);
	}

	/**
	 * Searches strings which are specified key's prefix.
	 * 
	 * @param key
	 *            the string to be searched for
	 * @param offset
	 *            starting position in the key
	 * @param length
	 *            length of key
	 * @return list of integer value which is associated with matched keys if
	 *         values is specified. Otherwise, indexes of matched keys.
	 */
	public List<Result> commonPrefixSearch(CharSequence key, int offset) {
		return commonPrefixSearch(key, offset, key.length() - offset, 0);
	}

	/**
	 * Searches strings which are specified key's prefix.
	 * 
	 * @param key
	 *            the string to be searched for
	 * @return list of integer value which is associated with matched keys if
	 *         values is specified. Otherwise, indexes of matched keys.
	 */
	public List<Result> commonPrefixSearch(CharSequence key) {
		return commonPrefixSearch(key, 0, key.length(), 0);
	}

	/**
	 * Returns longest common prefix between strings in the trie and specified
	 * key.
	 * 
	 * @param key
	 *            the string to be searched for
	 * @param offset
	 *            offset of specified key
	 * @param length
	 *            length of specified key
	 * @param nodePos
	 *            starting position for search in double array node.
	 * @return {@link DoubleArrayTrie.Result} object that contains length of
	 *         prefix and value which is associated with prefix.
	 */
	public Result getLongestCommonPrefix(CharSequence key, int offset, int length, int nodePos) {
		int b = getBase(nodePos);
		int n;
		int p;

		int resultVal = -1;
		int resultLen = -1;

		for (int i = 0; i < length; ++i) {
			p = b;
			n = getBase(p);
			if (b == getCheck(p) && n < 0) {
				resultVal = -n - 1;
				resultLen = i;
			}

			p = b + key.charAt(offset + i) + 1;
			if (b == getCheck(p)) {
				b = getBase(p);
			} else if (resultLen < 0) {
				return null;
			} else {
				return new Result(resultVal, resultLen);
			}
		}

		p = b;
		n = getBase(p);

		if (b == getCheck(p) && n < 0) {
			resultVal = -n - 1;
			resultLen = length;
		}

		if (resultLen > 0) {
			return new Result(resultVal, resultLen);
		} else {
			return null;
		}
	}

	/**
	 * Returns longest common prefix between strings in the trie and specified
	 * key.
	 * 
	 * @param key
	 *            the string to be searched for
	 * @param offset
	 *            offset of specified key
	 * @param length
	 *            length of specified key
	 * @return {@link DoubleArrayTrie.Result} object that contains length of
	 *         prefix and value which is associated with prefix.
	 */
	public Result getLongestCommonPrefix(CharSequence key, int offset, int length) {

		return getLongestCommonPrefix(key, offset, length, 0);
	}

	/**
	 * Returns longest common prefix between strings in the trie and specified
	 * key.
	 * 
	 * @param key
	 *            the string to be searched for
	 * @param offset
	 *            offset of specified key
	 * @return {@link DoubleArrayTrie.Result} object that contains length of
	 *         prefix and value which is associated with prefix.
	 */
	public Result getLongestCommonPrefix(CharSequence key, int offset) {
		return getLongestCommonPrefix(key, offset, key.length() - offset, 0);
	}

	/**
	 * Returns longest common prefix between strings in the trie and specified
	 * key.
	 * 
	 * @param key
	 *            the string to be searched for
	 * @param offset
	 *            offset of specified key
	 * @return {@link DoubleArrayTrie.Result} object that contains length of
	 *         prefix and value which is associated with prefix.
	 */
	public Result getLongestCommonPrefix(CharSequence key) {
		return getLongestCommonPrefix(key, 0, key.length(), 0);
	}

	private int getBase(int index) {
		if (map != null) {
			int offset = index * Integer.SIZE / 8;
			map.position(offset);
			return map.getInt();
		} else {
			return base[index];
		}
	}

	private void setBase(int index, int value) {
		base[index] = value;
	}

	private int getCheck(int index) {
		if (map != null) {
			int offset = alloc_size * Integer.SIZE / 8 + index * Integer.SIZE / 8;
			map.position(offset);
			return map.getInt();
		} else {
			return check[index];
		}
	}

	private void setCheck(int index, int value) {
		check[index] = value;
	}
}
