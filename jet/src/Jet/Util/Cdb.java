// -*- tab-width: 4 -*-
package Jet.Util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

/**
 * Constant database implementation.
 * 
 * @author <a href="mailto:oda.org@gmail.com">Akira ODA</a>
 * @see http://cr.yp.to/cdb.html
 */
public final class Cdb {
	/** mapped buffer to be associated database. */
	private ByteBuffer map;

	/** number of hash slots searched under this key. */
	private int loop;

	/** initialized if loop is nonzero. */
	private int khash;

	/** position of key. */
	private int kpos;

	/** position of hash. */
	private int hpos;

	/** slots of hash. */
	private int hslots;

	/**
	 * Constructs constant database reader.
	 * 
	 * @param file
	 *            the file object
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public Cdb(final File file) throws IOException {
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		FileChannel channel = raf.getChannel();
		map = channel.map(MapMode.READ_ONLY, 0, raf.length());
		map.order(ByteOrder.LITTLE_ENDIAN);
		channel.close();
	}

	/**
	 * Consructs constant database reader.
	 * 
	 * @param filename
	 *            filename of databse file
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public Cdb(final String filename) throws IOException {
		this(new File(filename));
	}

	/**
	 * close all resource which is held by this object.
	 */
	public void close() {
		map = null;
	}

	/**
	 * Notifies starting of database lookup.
	 */
	public void findstart() {
		loop = 0;
	}

	/**
	 * Returns next entry to be associated with the specified key.
	 * 
	 * @param key
	 *            key which the specified value to be associated
	 * @param offset
	 *            offset of <var>key</var>
	 * @param len
	 *            length of <var>length</var>
	 * @return next entry to be associated with the specified key or
	 *         <tt>null</tt> if next entry is missing.
	 */
	public byte[] findNext(byte[] key, int offset, int len) {
		int u;
		int dlen;

		if (loop == 0) {
			u = hash(key, offset, len);
			map.position((u << 3) & 2047);
			hpos = map.getInt();
			hslots = map.getInt();
			if (hslots == 0) {
				return null;
			}
			khash = u;
			u >>>= 8;
			u %= hslots;
			u <<= 3;
			kpos = hpos + u;
		}

		while (loop < hslots) {
			int pos;

			map.position(kpos);
			u = map.getInt();
			pos = map.getInt();
			if (pos == 0) {
				return null;
			}

			loop++;
			kpos += 8;

			if (kpos == hpos + (hslots << 3)) {
				kpos = hpos;
			}

			if (u == khash) {
				map.position(pos);
				u = map.getInt();
				if (u == len) {
					dlen = map.getInt();
					byte[] keyInDb = new byte[len];
					map.get(keyInDb, 0, len);
					if (!match(key, offset, len, keyInDb)) {
						continue;
					}

					byte[] data = new byte[dlen];
					map.get(data, 0, dlen);
					return data;
				}
			}
		}

		return null;
	}

	/**
	 * Returns next entry to be associated with the specified key.
	 * 
	 * @param key
	 *            key which the specified value to be associated
	 * @return next entry to be associated with the specified key or
	 *         <tt>null</tt> if next entry is missing.
	 */
	public byte[] findNext(byte[] key) {
		return findNext(key, 0, key.length);
	}

	/**
	 * Returns value to be associated with the specified key.
	 * 
	 * @param key
	 *            key which the specified value to be associated
	 * @param offset
	 *            offset of <var>key</var>
	 * @param len
	 *            length of <var>key</var>
	 * @return value to be associated with the speicified key or <tt>null</tt>
	 *         if database does not contain the specified key.
	 */
	public byte[] find(byte[] key, int offset, int len) {
		findstart();
		return findNext(key, offset, len);
	}

	/**
	 * Returns value to be associated with the specified key.
	 * 
	 * @param key
	 *            key which the specified value to be associated
	 * @return value to be associated with the speicified key or <tt>null</tt>
	 *         if database does not contain the specified key.
	 */
	public byte[] find(byte[] key) {
		return find(key, 0, key.length);
	}

	/**
	 * Returns hash of key.
	 * 
	 * @param key
	 *            a byte array to be calculated hash
	 * @param offset
	 *            offset of <var>key</var>
	 * @param len
	 *            length of <var>key</var>
	 * @return a hash value of key
	 */
	static int hash(byte[] key, int offset, int len) {
		long h = 5381;
		final long mask = 0x00000000ffffffffL;
		for (int i = 0; i < len; i++) {
			h = (h + (h << 5) & mask) & mask;
			h = h ^ ((key[offset + i] + 0x100) & 0xff);
		}

		return (int) (h & mask);
	}

	/**
	 * Determines if the specified key and key in database are equal.
	 * 
	 * @param key
	 *            key to be tested for equality
	 * @param offset
	 *            offset of <var>key</var>
	 * @param len
	 *            length of <var>key</var>
	 * @param keyInDb
	 *            key which is in database to be tested for equality
	 * @return true if the two arrays are equal.
	 */
	private static boolean match(byte[] key, int offset, int len, byte[] keyInDb) {
		if (keyInDb.length < len) {
			return false;
		}

		for (int i = 0; i < len; i++) {
			if (key[offset + i] != keyInDb[i]) {
				return false;
			}
		}
		return true;
	}
}
