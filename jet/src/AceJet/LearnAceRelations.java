// -*- tab-width: 4 -*-
package AceJet;

import java.io.IOException;

//Author:       Ralph Grishman
//Date:         March 10, 2005

/**
 *  command-line callable method for training Ace relation tagger.
 */

public class LearnAceRelations {
	
	/**
	 *  train relation tagger from text and APF files.  Takes 2n+3
	 *  arguments:
	 *  <ul>
	 *  <li>  jet-properties:  properties file
	 *  <li>  pattern-file:  (output) file containing set of pattern-relation pairs
	 *  <li>  year:          2004 or 2005
	 *  <li>  directory-1:   directory containing text and apf files
	 *  <li>  filelist-1:    list of documents
	 *  <li>  directory-2:
	 *  <li>  ...
	 *  </ul>
	 */

	public static void main (String[] args) throws IOException {
		if (args.length < 5 || args.length % 2 == 0) {
			System.err.println ("LearnAceRelations requires 2n+3 arguments:  ");
			System.err.println ("  jet-properties pattern-file year (directory filelist)*");
			System.exit (1);
		}
		String configFile = args[0];
		String patternFile = args[1];
		String year = args[2];
		AceDocument.ace2004 = true;
		if (year.equals("2004")) {
			AceDocument.ace2005 = false;
		} else if (year.equals("2005")) {
			AceDocument.ace2005 = true;
		} else {
			System.err.println ("Invalid year " + year + " in argument list.");
			System.err.println ("(Only 2004 - 2005 allowed.)");
			System.exit (1);
		}
		LearnRelations relLearner = new LearnRelations (configFile, patternFile);
		for (int iarg = 3; iarg < args.length; iarg+=2) {
			String directory = args[iarg];
			if (!directory.endsWith("/")) directory += "/";
			String filelist = args[iarg + 1];
			relLearner.learnFromFileList (filelist, directory, directory);
		}
		relLearner.finish();
	}
}
