// -*- tab-width: 4 -*-
package AceJet;

import java.util.*;
import java.io.*;

import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.parsers.*;

import Jet.Refres.Resolve;

/**
 *  analyze a set of ACE APF files for coreference relations
 *  among names.
 */

public class APFNameAnalyzer {

	static String encoding = "ISO-8859-1";  // default:  ISO-LATIN-1
	static HashMap startTag;
	static HashSet endTag;
	static DocumentBuilder builder;
	static final String ACEdir =
	    "C:/Documents and Settings/Ralph Grishman/My Documents/ACE/";
	static final String fileList =
		// ACEdir + "training all.txt";
		// ACEdir + "feb02 all.txt";
		// ACEdir + "sep02 all.txt";
		// ACEdir + "aug03 all.txt";
		// ACEdir + "files-to-process.txt";
		ACEdir + "training nwire.txt";

	static int identityCount = 0;
	static int equalsIgnoreCaseCount = 0;
	static int lastNameCount = 0;
	static int lastTwoNameCount = 0;
	static int firstNameCount = 0;
	static int personSubseqCount = 0;
	static int acronymCount = 0;
	static int reverseAcronymCount = 0;
	static int abbreviationCount = 0;
	static int reverseAbbreviationCount = 0;
	static int capitalCount = 0;
	static int subseqCount = 0;
	static int leftovers = 0;

	public static void main (String [] args) throws Exception  {
		Resolve.trace = false;
		// load gazetteer
		AceJet.Ace.gazetteer = new Gazetteer();
		AceJet.Ace.gazetteer.load(ACEdir + "loc.dict");
		// initialize APF reader
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		builder = factory.newDocumentBuilder();

		// open list of files
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		String currentDoc;
		while ((currentDoc = reader.readLine()) != null) {
			// process file 'currentDoc'
			docCount++;
			// if (docCount != 65) continue;
			System.out.println ("\nProcessing document " + docCount + ": " + currentDoc);
			String textFileName = ACEdir + currentDoc + ".sgm";
			boolean newData = fileList.indexOf("03") > 0;
			String APFfileName = ACEdir + currentDoc + (newData ? ".apf.xml" : ".sgm.tmx.rdc.xml");
			analyzeDocument (textFileName, APFfileName);
		}
		report();
	}

	private static void analyzeDocument (String textFileName, String APFfileName)
	    throws SAXException, IOException {
		Document apfDoc = builder.parse(APFfileName);
		StringBuffer fileText = readDocument(textFileName);
		computeOffsets (fileText);
		findNames (apfDoc, fileText);
	}

	/**
	 *  read the names in the APF file.  For each name, create an entry in
	 *  the startTag map and endTag set.
	 */

	static void findNames (Document apfDoc, StringBuffer fileText) {
		startTag = new HashMap();
		endTag = new HashSet();
		NodeList entities = apfDoc.getElementsByTagName("entity");
		for (int i=0; i<entities.getLength(); i++) {
			Element entity = (Element) entities.item(i);
			// System.out.println ("Found entity " + entityID);
			NodeList entityTypeList = entity.getElementsByTagName("entity_type");
			Element entityType = (Element) entityTypeList.item(0);
			String type = getElementText (entity, "entity_type");
			ArrayList priorNames = new ArrayList();
			NodeList names = entity.getElementsByTagName("name");
			TreeMap nameStart = new TreeMap();
			for (int j=0; j<names.getLength(); j++) {
				Element name = (Element) names.item(j);
				String startS = getElementText (name, "start");
				int start = Integer.parseInt(startS);
				int startJet = JEToffsetMap[start];
				String endS = getElementText (name, "end");
				int end = Integer.parseInt(endS);
				int endJet = JEToffsetMap[end];
				String nameString = fileText.substring(startJet, endJet+1);
				nameStart.put(new Integer(startJet), nameString);
			}
			Iterator it = nameStart.values().iterator();
			while (it.hasNext()) {
				String nameString = (String) it.next();
				analyzeNames (priorNames, nameString, type);
			}
		}
	}

	/*  assumes elementType is a leaf element type */

	private static String getElementText (Element e, String elementType) {
		NodeList typeList = e.getElementsByTagName(elementType);
		Element typeElement = (Element) typeList.item(0);
		String text = (String) typeElement.getFirstChild().getNodeValue();
		return text;
	}

	/**
	 *  read file 'fileName' and return its contents as a StringBuffer
	 */

	static StringBuffer readDocument (String fileName) throws IOException {
		File file = new File(fileName);
		String line;
		BufferedReader reader = new BufferedReader (
			// (new FileReader(file));
			new InputStreamReader (new FileInputStream(file), encoding));
		StringBuffer fileText = new StringBuffer();
		while((line = reader.readLine()) != null)
			fileText.append(line + "\n");
		return fileText;
	}

	// map from ACE offset to Jet offset
	static int[] ACEoffsetMap = null;
	static int[] JEToffsetMap = null;

	/**
	 *  compute ACEoffsetMap, a map from ACE offsets (which exclude XML tags
	 *  to Jet offsets (which include all characters in the file)
	 */

	static void computeOffsets (StringBuffer fileText) {
		boolean inTag = false;
		int xmlCount = 0;
		int length = fileText.length();
		ACEoffsetMap = new int[length];
		JEToffsetMap = new int[length];
		for (int i=0; i<length; i++) {
			if(fileText.charAt(i) == '<') inTag = true;
			JEToffsetMap[i - xmlCount] = i;
			if (inTag) xmlCount++;
			ACEoffsetMap[i] = i - xmlCount;
			if(fileText.charAt(i) == '>') inTag = false;
		}
	}

	// map from APF type names to 'standard' names

	static HashMap standardType = new HashMap();
	static {standardType.put("GSP", "GPE");
	        standardType.put("PER", "PERSON");
	        standardType.put("ORG", "ORGANIZATION");
	        standardType.put("LOC", "LOCATION");
	        standardType.put("FAC", "FACILITY");
	     }

	static void analyzeNames (ArrayList priorNames, String currentName, String type) {
		// System.out.println ("Found name " + currentName);
		String[] tokens = Gazetteer.splitAtWS(currentName);
		tokens = Resolve.normalizeGazName(tokens, false, false);
		currentName = Resolve.concat(tokens);
		priorNames.add(currentName);
		String[] currentCountry = AceJet.Ace.gazetteer.capitalToCountry(tokens);
		// is currentName identical to some prior name?
		for (int i=0; i<priorNames.size()-1; i++) {
			String priorName = (String) priorNames.get(i);
			if (currentName.equals(priorName)) {
				identityCount++;
				return;
			}
		}
		for (int i=priorNames.size()-2; i >= 0; i--) {
			String priorName = (String) priorNames.get(i);
			String[] priorNameTokens = Gazetteer.splitAtWS(priorName);
			if (currentName.equalsIgnoreCase(priorName)) {
				equalsIgnoreCaseCount++;
				return;
			} else if ((type.equals("PER") || type.equals("PERSON")) &&
			           tokens.length == 1 &&
			           currentName.equals(priorNameTokens[priorNameTokens.length-1])) {
			  lastNameCount++;
			  return;
			} else if ((type.equals("PER") || type.equals("PERSON")) &&
			           tokens.length == 2 &&
			           priorNameTokens.length > 2 &&
			           // must ignore case for al -> Al, de -> De
			           tokens[0].equalsIgnoreCase(priorNameTokens[priorNameTokens.length-2]) &&
			           tokens[1].equals(priorNameTokens[priorNameTokens.length-1])) {
			  lastTwoNameCount++;
			  return;
			} else if ((type.equals("PER") || type.equals("PERSON")) &&
			           tokens.length == 1 &&
			           currentName.equals(priorNameTokens[0])) {
			  firstNameCount++;
			  return;
			} else if ((type.equals("PER") || type.equals("PERSON"))
			           && Resolve.matchFullName(tokens, "", priorNameTokens, "") >= 0) {
				personSubseqCount++;
				return;
			  // need GPE for 'EU = European Union'
			} else if ((type.equals("ORG") || type.equals("ORGANIZATION") || type.equals("GPE")) &&
			           tokens.length == 1 &&
			           isAcronym(priorNameTokens, currentName)) { // << put back to Resolve.isAcronym
			  acronymCount++;
			  return;
			} else if ((type.equals("ORG") || type.equals("ORGANIZATION") || type.equals("GPE")) &&
			           priorNameTokens.length == 1 &&
			           isAcronym(tokens, priorName)) { // << put back to Resolve.isAcronym
			  reverseAcronymCount++;
			  return;
			} else if ((type.equals("ORG") || type.equals("ORGANIZATION") || type.equals("GPE")) &&
			           tokens.length == 1 &&
			           isAbbreviation(priorNameTokens, currentName)) {
			  abbreviationCount++;
			  return;
			} else if ((type.equals("ORG") || type.equals("ORGANIZATION") || type.equals("GPE")) &&
			           priorNameTokens.length == 1 &&
			           Resolve.isAbbreviation(tokens, priorName) == 0) {
			  reverseAbbreviationCount++;
			  return;
			} else if (type.equals("GPE") &&
				         currentCountry != null &&
				         Resolve.equalArray(currentCountry, priorNameTokens)) {
				capitalCount++;
				return;
			} else if (!(type.equals("PER") || type.equals("PERSON"))
			           && Resolve.matchFullName(tokens, "", priorNameTokens, "") >= 0) {
				subseqCount++;
				return;
			}
		}
		if (priorNames.size() > 1) {
			System.out.println (currentName + " is alias of " + priorNames.get(0));
			leftovers++;
		}
	}

	static void report () {
		System.out.println ("Coreference counts:");
		System.out.println ("  identity:              " + identityCount);
		System.out.println ("  identityIgnoringCase:  " + equalsIgnoreCaseCount);
		System.out.println ("  last name:             " + lastNameCount);
		System.out.println ("  last two names:        " + lastTwoNameCount);
		System.out.println ("  first name:            " + firstNameCount);
		System.out.println ("  other subseq (person): " + personSubseqCount);
		System.out.println ("  acronym:               " + acronymCount);
		System.out.println ("  name follows acronym:  " + reverseAcronymCount);
		System.out.println ("  abbreviation:          " + abbreviationCount);
		System.out.println ("  name follows abbrev.:  " + reverseAbbreviationCount);
		System.out.println ("  capital of country     " + capitalCount);
		System.out.println ("  subseq. (not person):  " + subseqCount);
		System.out.println ("  other:                 " + leftovers);
	}

	// ---------------------------------------------

	/**
	 *  returns true if 'acronym' is a possible acronym for 'name', such
	 *  as 'USA' for 'United States of America'.  The test
	 *  succeeds if the letters of 'acronym' are a subset of the initial
	 *  letters of the tokens of 'name', appearing in the same order as in
	 *  'name'.  The acronym must be at least 2 letters long.
	 */

	static boolean trace = false;

	public static boolean isAcronym (String[] name, String acronym) {
		if (name.length < 2 || acronym.length() < 2)
			return false;
		int iacr=0;
		for (int i=0; i<name.length; i++) {
			if (name[i].equalsIgnoreCase("the") ||
			    name[i].equalsIgnoreCase("of") ||
			    name[i].equalsIgnoreCase("for") ||
			    name[i].equalsIgnoreCase("and"))
			  continue;
			if (iacr < acronym.length() &&
			    name[i].charAt(0) == acronym.charAt(iacr))
				iacr++;
			else
				return false;
		}
		if (trace) System.out.println ("Refres: recognizing " + acronym
			                             + " as acronym of " + Resolve.concat(name));
		return true;
	}

	/**
	 *  returns true if 'abbrev' is an acronym-style abbreviation for 'name'
	 *  -- i.e., an acronym with periods, such as U.S.A. for 'United
	 *  States of America'.
	 */

	public static boolean isAbbreviation (String[] name, String abbrev) {
		if (name.length < 2 || abbrev.length() < 4 || abbrev.length() % 2 == 1)
			return false;
		int iabr=0;
		for (int i=0; i<name.length; i++) {
			if (name[i].equalsIgnoreCase("the") ||
			    name[i].equalsIgnoreCase("of") ||
			    name[i].equalsIgnoreCase("for") ||
			    name[i].equalsIgnoreCase("and"))
			  continue;
			if (iabr < abbrev.length()-1 &&
			    name[i].charAt(0) == abbrev.charAt(iabr) &&
			    abbrev.charAt(iabr+1) == '.')
				iabr += 2;
			else
				return false;
		}
		if (trace) System.out.println ("Refres: recognizing " + abbrev
			                             + " as abbreviation of " + Resolve.concat(name));
		return true;
	}
}
