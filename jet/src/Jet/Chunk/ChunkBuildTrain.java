// -*- tab-width: 4 -*-
package Jet.Chunk;

/**
 *  Takes an NP-chunk file from RM95 format into a file of features for
 *  the opennlp maxent package.
 */

import java.util.*;
import java.io.*;

class ChunkBuildTrain {

	static public final String chunkDir =
		"C:/Documents and Settings/Ralph Grishman/My Documents/HMM/Chunk/";

	public static void main (String[] args) {
		// String inFile = "C:/My Documents/HMM/Chunk/wsj_15_18_train.log";
		String inFile = chunkDir + "wsj_15_18_train.log";
		// String featureFile = "C:/My Documents/HMM/Chunk/chunk features.txt";
		String featureFile = chunkDir + "chunk features.txt";

		try {
			BufferedReader reader = new BufferedReader(new FileReader(inFile));
			PrintStream writer = new PrintStream (new FileOutputStream (featureFile));
			String line;
			String prevToken = "";
			String prevPOS = "";
			String prevTag = "";
			String currentToken = "";
			String currentPOS = "";
			String currentTag = "";
			String nextToken = "";
			String nextPOS = "";
			String nextTag = "";
			String followingToken = "";
			String followingPOS = "";
			String followingTag = "";
			StringBuffer features = new StringBuffer(200);

			boolean inGroup = false;
			boolean firstToken = true;
			while((line = reader.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(line);
				int count = st.countTokens();
				if (count == 0) {
					// blank line -- end of sentence
					followingToken = "";
					followingPOS = "";
					followingTag = "";
				} else if (count >= 3) {
					// token
					followingToken = st.nextToken();
					followingPOS = st.nextToken();
					followingTag = st.nextToken();
				} else {
					System.out.println ("Error:  invalid input line: " + line);
				}
				if (currentToken != "") {
					features.setLength(0);
					features.append("prevPOS=" + prevPOS + " ");
					features.append("currPOS=" + currentPOS + " ");
					features.append("nextPOS=" + nextPOS + " ");
					if (nextToken == "")
						features.append("POS012=" + currentPOS + ":: ");
					else
						features.append("POS012=" + currentPOS + ":" + nextPOS
						                + ":" + followingPOS + " ");
					features.append("prevTag=" + prevTag + " ");
					features.append("currWord=" + currentToken + " ");
					features.append("W-1W0=" + prevToken + ":" + currentToken + " ");
					features.append("W0W1=" + currentToken + ":" + nextToken + " ");
					features.append(currentTag);
					writer.println(features);
				}
				prevToken = currentToken;
				prevPOS = currentPOS;
				prevTag = currentTag;
				currentToken = nextToken;
				currentPOS = nextPOS;
				currentTag = nextTag;
				nextToken = followingToken;
				nextPOS = followingPOS;
				nextTag = followingTag;
			}
		} catch (IOException e) {
			System.out.println (e);
		}
	}
}
