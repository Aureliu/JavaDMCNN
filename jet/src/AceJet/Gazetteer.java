// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.60
//Copyright(c): 2011
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package AceJet;

import java.util.*;
import java.io.*;
import Jet.JetTest;
import Jet.Refres.Resolve;
import Jet.Lisp.FeatureSet;
import Jet.Lex.Lexicon;
import Jet.Tipster.*;

/**
 *  a set of names of locations (countries, cities, etc.).
 *  <P>
 *  Since names often consist of several tokens, all name parameters are of type
 *  String[], with one array element for each token of a name.
 */

public class Gazetteer {

	HashMap<String, String> nationalityToCountry;
	HashMap<String, String> nationalToCountry;
	HashMap<String, String> nationalsToCountry;
	HashMap<String, String> aliasToCountry;
	HashMap<String, String> capitalToCountry;
	// a map from the location name to its type:  country, continent, region
	HashMap<String,String> locations;
	LineNumberReader reader;
	boolean monocase = false;
	// map from monocase name to mixed-case name
	HashMap<String, String> monocaseMap;

	/**
	 *  create a new, empty Gazetteer.
	 */

	public Gazetteer () {
		nationalityToCountry = new HashMap<String, String>();
		nationalToCountry = new HashMap<String, String>();
		nationalsToCountry = new HashMap<String, String>();
		aliasToCountry = new HashMap<String, String>();
		capitalToCountry = new HashMap<String, String>();
		locations = new HashMap<String, String>();
		monocaseMap = new HashMap<String, String>();
	}

	/**
	 *  load the Gazetteer from the file specified by parameter <CODE>Gazetteer.fileName</CODE>
	 *  of the configuration file.
	 */

	public void load () throws IOException {
		String fileName = JetTest.getConfigFile("Gazetteer.fileName");
		if (fileName != null) {
			load(fileName);
		} else {
			System.err.println ("Gazetteer.load:  no file name specified in config file");
		}
	}

	/**
	 *  load the Gazetteer from file <CODE>fileName</CODE>.
	 */

	public void load (String fileName) throws IOException {
		System.err.println ("Loading gazetteer.");
		reader = new LineNumberReader (new FileReader (fileName));
		StreamTokenizer tok = new StreamTokenizer(reader);
		while (tok.nextToken() != StreamTokenizer.TT_EOF) {
			readGazetteerEntry (tok);
		}
		// enter location names in lexicon with annotation type 'onoma'
		for (String location : locations.keySet()) {
			Lexicon.addEntry (splitAtWS(location),
			                  new FeatureSet ("TYPE", "GPE",
			                                  "SUBTYPE", aceSubtype(locations.get(location))),
			                  "onoma");
		}
	}

	private String aceSubtype (String subtype) {
		if (subtype.equals("continent"))
			return "Continent";
		else if (subtype.equals("region"))
			return "Region-International";
		else if (subtype.equals("country"))
			return "Nation";
		else if (subtype.equals("usstate"))
			return "State-or-Province";
		else
			return "Population-Center";
	}

	private void readGazetteerEntry (StreamTokenizer tok) throws IOException {
		String key;
		String value;
		String primaryName = "";
		String type = "";
		String nationality = "";
		String national = "";
		String nationals = "";
		do {
			if (tok.ttype == StreamTokenizer.TT_WORD) {
				key = tok.sval.intern();
			} else {
				int ln = reader.getLineNumber();
				System.err.println ("*** Syntax error in gazetteer: " + tok);
				return;
			}
			if (tok.nextToken() == '"') {
				value = tok.sval;
				monocaseMap.put(value.toLowerCase(), value);
			} else {
				int ln = reader.getLineNumber();
				System.err.println ("*** Syntax error in gazetteer, line" + ln);
				return;
			}
			if (key == "country" || key == "continent" || key == "region" ||
			    key == "usstate" || key == "city") {
				primaryName = value;
				type = key;
				locations.put(value, key);
			} else if (key == "nationality") {
				nationality = value;
				nationalityToCountry.put(nationality, primaryName);
			} else if (key == "aka") {
				aliasToCountry.put(value, primaryName);
				locations.put(value, type);
			} else if (key == "national") {
				national = value;
			} else if (key == "nationals") {
				nationals = value;
			} else if (key == "capital") {
				capitalToCountry.put(value, primaryName);
				locations.put(value, "city");
			} else {
				int ln = reader.getLineNumber();
				System.err.println ("*** Syntax error in gazetteer, line" + ln);
			}
		}	while (tok.nextToken() != ';');
		if (type == "country" && nationality != "") {
			if (national == "") national = nationality;
			if (nationals == "") {
				nationals = national + "s";
				monocaseMap.put(nationals.toLowerCase(), nationals);
			}
			nationalToCountry.put(national, primaryName);
			nationalsToCountry.put(nationals, primaryName);
		}
	}

	/**
	 *  sets the <CODE>monocase</CODE> which, when true, ignores case for
	 *  Gazetteer predicates.
	 */

	public void setMonocase (boolean monocase) {
		this.monocase = monocase;
	}

	/**
	 *  returns <CODE>true</CODE> if <CODE>s</CODE> is a nationality
	 *  adjective.
	 */

	public boolean isNationality (String[] s) {
		return nationalityToCountry.containsKey(foldArg(s));
	}

	/**
	 *  if <CODE>s</CODE> is a nationality adjective, returns the
	 *  associated country name, else <CODE>null</CODE>.
	 */

	public String[] nationalityToCountry (String[] s) {
		return (String[]) splitAtWS((String) nationalityToCountry.get(foldArg(s)));
	}

	/**
	 *  returns <CODE>true</CODE> if <CODE>s</CODE> is the name of a national.
	 */

	public boolean isNational (String[] s) {
		return nationalToCountry.containsKey(foldArg(s));
	}

	/**
	 *  if <CODE>s</CODE> is the name of a national, returns the
	 *  associated country name, else <CODE>null</CODE>.
	 */

	public String[] nationalToCountry (String[] s) {
		return (String[]) splitAtWS((String) nationalToCountry.get(foldArg(s)));
	}

	/**
	 *  returns <CODE>true</CODE> if <CODE>s</CODE> is the name of a set of
	 *  nationals.
	 */

	public boolean isNationals (String[] s) {
		return nationalsToCountry.containsKey(foldArg(s));
	}

	/**
	 *  if <CODE>s</CODE> is the name of a set of nationals, returns the
	 *  associated country name, else <CODE>null</CODE>.
	 */

	public String[] nationalsToCountry (String[] s) {
		return (String[]) splitAtWS((String) nationalsToCountry.get(foldArg(s)));
	}

	/**
	 *  if <CODE>s</CODE> is the name of the capital of a country, returns the
	 *  associated country name, else <CODE>null</CODE>.
	 */

	public String[] capitalToCountry (String[] s) {
		return (String[]) splitAtWS((String) capitalToCountry.get(foldArg(s)));
	}

	/**
	 *  returns <CODE>true</CODE> if <CODE>s</CODE> is the name of any type of location
	 *  (either the primary name or an alias).
	 */

	public boolean isLocation (String[] s) {
		return locations.containsKey(foldArg(s));
	}

	/**
	 *  returns <CODE>true</CODE> if <CODE>s</CODE> is the name of a country
	 *  (either the primary name or an alias).
	 */

	public boolean isCountry (String[] s) {
		return locations.get(foldArg(s)) == "country";
	}

	/**
	 *  returns <CODE>true</CODE> if <CODE>s</CODE> is an alias of a
	 *  location name.
	 */

	public boolean isCountryAlias (String[] s) {
		return aliasToCountry.containsKey(foldArg(s));
	}

	/**
	 *  returns <CODE>true</CODE> if <CODE>s</CODE> is the name of a U.S.
	 *  state (either the primary name or an alias).
	 */

	public boolean isState (String[] s) {
		return locations.get(foldArg(s)) == "usstate";
	}

	/**
	 *  returns <CODE>true</CODE> if <CODE>s</CODE> is the name of a region
	 *  or continent (either the primary name or an alias).
	 */

	public boolean isRegionOrContinent (String[] s) {
		String type = (String) locations.get(foldArg(s));
		return type == "region" || type == "continent";
	}

	/**
	 *  if <CODE>s</CODE> is the alias of a location name, returns the
	 *  primary location name, else <CODE>null</CODE>.
	 */

	public String[] canonicalCountryName (String[] s) {
		return (String[]) splitAtWS((String) aliasToCountry.get(foldArg(s)));
	}

	public static void main (String[] args) throws IOException {
		Gazetteer g = new Gazetteer();
		g.load ("data/loc.dict");
		g.setMonocase(true);
		System.out.println (g.isNationals(new String[]{"palestinians"}));
	}

	public static String[] splitAtWS (String s) {
		if (s == null) return null;
		StringTokenizer st = new StringTokenizer(s);
		int length = st.countTokens();
		String[] splitS = new String[length];
		for (int i=0; i<length; i++)
			splitS[i] = st.nextToken();
		return splitS;
	}

	private String foldArg (String[] s) {
		String x = Resolve.concat(s);
		if (monocase && monocaseMap.containsKey(x))
			x = (String) monocaseMap.get(x);
		return x;
	}

    /**
      *  returns "country", "stateorprovince", or "city" as the type
      *  of 'locationName' based on three types of evidence:
      *  - entries in the gazetteer itself
      *  - a coreferential nominal mention, typically from a
      *    construct 'city of X' or 'X province'
      *  - the last token of the location name
      */

    public String locationType (Document doc, String locationName) {
        String[] locationTokens = locationName.split(" ");	
	if (isCountry(locationTokens) || isNationality(locationTokens)) {
	     return "country";
	} else if (isState(locationTokens)) {
	    return "stateorprovince";
	} else if (capitalToCountry(locationTokens) != null) {
	    return "city";
	}
	// look for an entity with this name
	Vector<Annotation> entities = doc.annotationsOfType("entity");
	if (entities != null) {
	    entityLoop:
	    for (Annotation entity : entities) {
		String[] entityName = (String[]) entity.get("name");
		if (entityName == null) continue;
		if (entityName.length != locationTokens.length) continue;
		for (int i = 0; i < entityName.length; i++) {
		    if (!entityName[i].equals(locationTokens[i]))
			continue entityLoop;
		}
	        // does entity have a nominal mention with a state / city word?
	        Vector<Annotation> mentions = (Vector<Annotation>) entity.get("mentions");
		for (Annotation mention : mentions) {
			Object pax = mention.get("pa");
		        if (!(pax instanceof FeatureSet)) continue;
		        FeatureSet pa = (FeatureSet) pax;
		        String hd = (String) pa.get("head");
			if (hd.equals("city")) return "city";
			if (hd.equals("town")) return "city";
			if (hd.equals("village")) return "city";
			if (hd.equals("port")) return "city";
			if (hd.equals("province")) return "stateorprovince";
			if (hd.equals("state")) return "stateorprovince";
			if (hd.equals("county")) return "stateorprovince";
			if (hd.equals("district")) return "stateorprovince";
			// also island, region
		}
	    }
	}
        // does name end in District / Region?
	String lastToken = locationTokens[locationTokens.length - 1];
	    if (lastToken.equals("Province")) 
		return "stateorprovince";
	    if (lastToken.equals("District")) 
		return "stateorprovince";
	    if (lastToken.equals("County")) 
		return "stateorprovince";
	    if (lastToken.equals("State")) 
		return "stateorprovince";
	    if (lastToken.equals("City")) 
		return "city";
	return null;
    }

}
