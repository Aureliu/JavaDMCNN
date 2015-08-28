// -*- tab-width: 4 -*-
package Jet.Chunk;

/**
 *  convert an NP-chunk file from RM95 format into XML mark-up.
 */

import java.util.*;
import java.io.*;

class ChunkConvert {

	public static void main (String[] args) {
		String inFile = "C:/My Documents/HMM/Chunk/RM Data.txt";
		String textFile = "C:/My Documents/HMM/Chunk/chunk text.txt";
		String keyFile = "C:/My Documents/HMM/Chunk/chunk key.txt";
		try {
			BufferedReader reader = new BufferedReader(new FileReader(inFile));
			PrintStream textWriter = new PrintStream (new FileOutputStream (textFile));
			PrintStream keyWriter = new PrintStream (new FileOutputStream (keyFile));
			String line;
			StringBuffer sent = new StringBuffer(200);
			StringBuffer key = new StringBuffer(300);

			textWriter.println ("<TEXT>");
			keyWriter.println ("<TEXT>");
			sent.append("<S>");
			key.append("<S>");
			boolean inGroup = false;
			boolean firstToken = true;
			while((line = reader.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(line);
				int count = st.countTokens();
				if (count == 0) {
					// blank line -- end of sentence
					if (inGroup) key.append("</NG>");
					sent.append("</S>");
					textWriter.println(sent);
					sent.setLength(0);
					sent.append("<S>");
					key.append("</S>");
					keyWriter.println(key);
					key.setLength(0);
					key.append("<S>");
					firstToken = true;
					inGroup = false;
				} else if (count >= 3) {
					// token
					String token = st.nextToken();
					String pos = st.nextToken();
					String iob = st.nextToken();
					if (iob.equals("I")) {
						if (!firstToken) {
							sent.append(" ");
							key.append(" ");
						}
						if (!inGroup) key.append("<NG>");
						inGroup = true;
						sent.append(token);
						key.append(token);
					} else if (iob.equals("O")) {
						if (inGroup) key.append("</NG>");
						inGroup = false;
						if (!firstToken) {
							sent.append(" ");
							key.append(" ");
						}
						sent.append(token);
						key.append(token);
					} else if (iob.equals("B")) {
						if (!inGroup) {
							System.out.println ("Error:  B tag with inGroup=false");
							inGroup = true;
						}
						if (firstToken) {
							System.out.println ("Error:  B tag at start of sentence");
						}
						sent.append(" ");
						sent.append(token);
						key.append("</NG>");
						key.append(" ");
						key.append("<NG>");
						key.append(token);
					} else {
						System.out.println ("Error:  unknown IOB tag " + iob);
					}
					firstToken = false;
				} else {
					System.out.println ("Error:  invalid input line: " + line);
				}
			}
			if (!firstToken) {
				if (inGroup) key.append("</NG>");
				sent.append("</S>");
				textWriter.println(sent);
				key.append("</S>");
				keyWriter.println(key);
			}
			textWriter.println ("</TEXT>");
			keyWriter.println ("</TEXT>");
		} catch (IOException e) {
			System.out.println (e);
		}
	}
}
