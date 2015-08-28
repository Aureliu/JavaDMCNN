// -*- tab-width: 4 -*-
package Jet.Chunk;

import opennlp.maxent.*;
import opennlp.maxent.io.*;
import opennlp.model.*;
import java.io.*;
import java.util.*;

/**
 * Test the model on some input.
 */

public class Predict {

    public static void main(String[] args) {
		String dataFileName = ChunkBuildTrain.chunkDir + "wsj_20_test.log";
		String modelFileName = ChunkBuildTrain.chunkDir + "chunk model.txt";

		GISModel m = null;
		int correctTag = 0;
		int incorrectTag = 0;
		boolean matchingStart = false;
		int actualNg = 0;
		int predictedNg = 0;
		int matchingNg = 0;

		try {
		    m = (GISModel) new SuffixSensitiveGISModelReader(new File(modelFileName)).getModel();
		    System.err.println ("GIS model loaded.");
		} catch (Exception e) {
		    e.printStackTrace();
		    System.exit(0);
		}

		try {
			BufferedReader reader = new BufferedReader(new FileReader(dataFileName));
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

			String prevPredictedTag = "";
			String currentPredictedTag = "";

			String[] features = new String[8];

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
					followingTag = st.nextToken().intern();
				} else {
					System.err.println ("Error:  invalid input line: " + line);
				}
				if (currentToken == "") {
					currentPredictedTag = "";
				} else {
					features[0] = "prevPOS=" + prevPOS;
					features[1] = "currPOS=" + currentPOS;
					features[2] = "nextPOS=" + nextPOS;
					if (nextToken == "")
						features[3] = "POS012=" + currentPOS + "::";
					else
						features[3] = "POS012=" + currentPOS + ":" + nextPOS
						                + ":" + followingPOS;
					features[4] = "prevTag=" + prevPredictedTag;
					features[5] = "currWord=" + currentToken;
					features[6] = "W-1W0=" + prevToken + ":" + currentToken;
					features[7] = "W0W1=" + currentToken + ":" + nextToken;
					currentPredictedTag  = m.getBestOutcome(m.eval(features)).intern();
					if ((prevPredictedTag == "O" || prevPredictedTag == "")
					    && currentPredictedTag == "B")
						currentPredictedTag = "I";
					if (currentPredictedTag == currentTag) {
						correctTag++;
					} else {
						incorrectTag++;
					}
				}

				// if (actualNg >= 40) break;
				// System.err.println ("Current tag: " + currentTag +
				//                     "  predicted tag: " + currentPredictedTag);

				boolean ngEnd = (prevTag == "I" || prevTag == "B") &&
				                currentTag != "I";
				boolean predictedNgEnd =
				        (prevPredictedTag == "I" || prevPredictedTag == "B") &&
				        currentPredictedTag != "I";
				if (ngEnd) actualNg++;
				if (predictedNgEnd) predictedNg++;
				if (matchingStart && ngEnd && predictedNgEnd)
					matchingNg++;
				if (ngEnd || predictedNgEnd)
					matchingStart = false;
				boolean ngStart = currentTag == "B" ||
				                  ((prevTag == "O" | prevTag == "") && currentTag == "I");
				boolean predictedNgStart = currentPredictedTag == "B" ||
				                  ((prevPredictedTag == "O" || prevPredictedTag == "")
				                   && currentPredictedTag == "I");
				if (ngStart && predictedNgStart)
					matchingStart = true;

				prevToken = currentToken;
				prevPOS = currentPOS;
				prevTag = currentTag;
				currentToken = nextToken;
				currentPOS = nextPOS;
				currentTag = nextTag;
				nextToken = followingToken;
				nextPOS = followingPOS;
				nextTag = followingTag;
				prevPredictedTag = currentPredictedTag;
			}
		} catch (IOException e) {
			System.err.println (e);
		}
		System.out.println (correctTag + " correctTag predictions");
		System.out.println (incorrectTag + " incorrectTag predictions");
		System.out.println ("Tag accuracy: " +
		                    ((double)correctTag*100/(correctTag + incorrectTag)));

		System.out.println (" ");
		System.out.println (actualNg + " noun groups in key");
		System.out.println (predictedNg + " noun groups in response");
		System.out.println (matchingNg + " matching noun groups");
		System.out.println ("NG recall: " + ((double)matchingNg)/actualNg*100);
		System.out.println ("NG precision: " + ((double)matchingNg)/predictedNg*100);

	}

}
