// -*- tab-width: 4 -*-
package Jet.Util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of Constant database creation.
 * 
 * @author <a href="mailto:oda.org@gmail.com">Akira ODA</a>
 * @see http://cr.yp.to/cdb.html
 */
public class CdbBuilder {
	/**
	 * A pair of hash value and record pointer.
	 */
	private static final class HashPointer {
		/** hash. */
		int h;

		/** pointer. */
		int p;

		/**
		 * Construct HashPointer
		 * 
		 * @param h
		 *            hash value
		 * @param p
		 *            entry pointer
		 */
		public HashPointer(int h, int p) {
			this.h = h;
			this.p = p;
		}
	}

	/** database filename. */
	private String filename;

	/** temporary filename in building database. */
	private String tmpFilename;

	/** database file's file handle. */
	private RandomAccessFile file;

	/** bucket of HashPointer */
	private List<List<HashPointer>> bucket;

	/**
	 * Constructs an object to build constant database.
	 * 
	 * @param filename
	 *            database filename.
	 * @param tmpFilename
	 *            temporary filename in building database.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public CdbBuilder(String filename, String tmpFilename) throws IOException {
		this.filename = filename;
		this.tmpFilename = tmpFilename;
		file = new RandomAccessFile(tmpFilename, "rw");
		bucket = new ArrayList<List<HashPointer>>(256);
		for (int i = 0; i < 256; i++) {
			bucket.add(new ArrayList<HashPointer>());
		}

		file.seek(8 * 256);
	}

	/**
	 * Adds data.
	 * 
	 * @param key
	 *            key which the specified value to be associated
	 * @param koff
	 *            offset of key
	 * @param klen
	 *            length of key
	 * @param value
	 *            value to be associated with specified key
	 * @param voff
	 *            offset of value
	 * @param vlen
	 *            length of value
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public void add(byte[] key, int koff, int klen, byte[] value, int voff, int vlen)
			throws IOException {
		int p = (int) file.getFilePointer();
		writeIntInLittleEndian(file, klen);
		writeIntInLittleEndian(file, vlen);
		file.write(key, koff, klen);
		file.write(value, voff, vlen);
		int h = Cdb.hash(key, koff, klen);
		bucket.get(h & 0xff).add(new HashPointer(h, p));
	}

	/**
	 * Adds data.
	 * 
	 * @param key
	 *            key which the specified value to be associated.
	 * @param value
	 *            value to be associated with specified key.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public void add(byte[] key, byte[] value) throws IOException {
		int p = (int) file.getFilePointer();
		writeIntInLittleEndian(file, key.length);
		writeIntInLittleEndian(file, value.length);
		file.write(key);
		file.write(value);
		int h = Cdb.hash(key, 0, key.length);
		bucket.get(h & 0xff).add(new HashPointer(h, p));
	}

	/**
	 * Finishs building constant database.
	 * 
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public void finish() throws IOException {
		int pos_hash = (int) file.getFilePointer();
		for (List<HashPointer> b1 : bucket) {
			if (b1.size() == 0) {
				continue;
			}

			int ncells = b1.size() * 2;
			HashPointer[] cell = new HashPointer[ncells];
			for (int i = 0; i < cell.length; i++) {
				cell[i] = new HashPointer(0, 0);
			}

			for (HashPointer pair : b1) {
				int i = (pair.h >>> 8) % ncells;
				while (cell[i].p != 0) {
					i = (i + 1) % ncells;
				}
				cell[i] = pair;
			}

			for (HashPointer pair : cell) {
				writeIntInLittleEndian(file, pair.h);
				writeIntInLittleEndian(file, pair.p);
			}
		}

		file.seek(0);
		for (List<HashPointer> b1 : bucket) {
			writeIntInLittleEndian(file, pos_hash);
			writeIntInLittleEndian(file, b1.size() * 2);
			pos_hash += b1.size() * 2 * 8;
		}

		file.close();

		File fromFile = new File(tmpFilename);
		File toFile = new File(filename);
		fromFile.renameTo(toFile);
	}

	/**
	 * Writes integer value in little endian.
	 * 
	 * @param file
	 * @param n
	 * @throws IOException
	 */
	private static void writeIntInLittleEndian(RandomAccessFile file, int n) throws IOException {
		file.write((byte) n & 0xff);
		file.write((byte) ((n >> 8) & 0xff));
		file.write((byte) ((n >> 16) & 0xff));
		file.write((byte) ((n >> 24) & 0xff));
	}
}
