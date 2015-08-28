// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.40
//Description:  A Java-based Information Extraction Tool

package Jet;

import Jet.Tipster.MakeCollection;
import Jet.Refres.CorefEval;
import java.io.*;

/**
 *  validation procedure for Jet.  Currently only runs coreference test.
 */

public class Validate {

	public static double score = 0;

	public static void main (String[] args) {
		boolean result = validateCoref();
		System.out.println ("validation: " + result);
	}

	private static boolean validateCoref () {
		// announce
		System.out.println ("Coreference validation -------------------------------");
		// run coref
		//  problems:  does not set Ace flag
		//             does not load type dict
		//             does not test for monocase
		// write coref output to 'valdata/response-corefTestData'
		JetTest.main (new String[] {"valdata/coref.jet"});
		// convert output to a collection
		MakeCollection.main (new String[] {"valdata/response-corefTestData.txt",
		                                   "temp/coref-response-collection",
		                                   "coref-response",
		                                   "co.txt"});
		(new File("valdata/response-corefTestData.txt")).delete();
		// score
		CorefEval.task (new String[] {"", "temp/coref-response-collection",
		                                  "valdata/coref-key-collection"});
		System.out.println ("Coreference validation complete ----------------------");
		// check score
		return score > 0.68;
	}
}
