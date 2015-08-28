// author:  Kai Cao

package Jet.Parser;

import java.util.*;

import tratz.parse.*;
import tratz.parse.io.*;
import tratz.parse.FullSystemWrapper.FullSystemResult;
import tratz.parse.types.Arc;
import tratz.parse.types.Parse;
import tratz.parse.types.Sentence;
import tratz.parse.types.Token;
import tratz.parse.util.ParseConstants;

import Jet.JetTest;
import Jet.Tipster.*;
import Jet.Parser.SyntacticRelation;
import Jet.Parser.SyntacticRelationSet;

/**
 *  interface to a dependency parser (currently the Tratz-Hovy parser).
 */

public class DepParser {

    private static FullSystemWrapper fsw=null;

    static DepTransformer transformer = null;

    /**
     *  load the parse model file from parameter 'DepParser.model.fileName'
     *  of the Jet properties file.
     */

    public static void initialize (String dataPath, Properties config) {
	String parseModelFile = config.getProperty("DepParser.model.fileName");
	if (parseModelFile != null) {
	    initWrapper(dataPath + "/" + parseModelFile);
	    transformer = new DepTransformer (config.getProperty("DepParser.transformations"));
	}
    }

    /**Initialize the Wrapper*/
    private static void initWrapper(String parseModelFile){
	initWrapper(null, null, null, null, null, null, parseModelFile, null);
    }
    /**Initialize the Wrapper*/
    private static void initWrapper(String prepositionModelFile, String nounCompoundModelFile,
				    String possessivesModelFile, String srlArgsModelFile, 
				    String srlPredicatesModelFile, String posModelFile, 
				    String parseModelFile, String wnDir){
	if (fsw==null){
	    try{
		fsw=new FullSystemWrapper(prepositionModelFile, 
					  nounCompoundModelFile, 
					  possessivesModelFile, 
					  srlArgsModelFile, srlPredicatesModelFile,
					  posModelFile, parseModelFile, wnDir);
	    }
	    catch(Exception ex){
		System.out.println(ex);
	    }
	}
    }

    public static boolean isInitialized () {
	return fsw != null;
    }
	
    /**
     *  parse all the sentences in Document 'doc', returning a
     *  SyntacticRelationSet containing all the dependency relations.
     */

    public static SyntacticRelationSet parseDocument (Document doc) {
	Vector<Annotation> sentences = doc.annotationsOfType("sentence");
	if (sentences == null || sentences.size() == 0) {
	    System.out.println ("DepParser:  no sentences");
	    return null;
	}
	if (fsw == null) {
	    System.out.println ("DepParser:  no model loaded");
	    return null;
	}
	SyntacticRelationSet relations = new SyntacticRelationSet();
	for (Annotation sentence : sentences) {
	    Span span = sentence.span();
	    parseSentence (doc, span, relations);
	}
	return relations;
    }

    static String[] SPECIAL_TOKEN = new String[] {"ENAMEX", "NUMEX", "TIMEX", "TIMEX2", "TERM"};

    /**
     *  generate the dependency parse for a sentence, adding its arcs to
     *  'relations'.
     */

    public static void parseSentence (Document doc, Span span, SyntacticRelationSet relations) {
	if (fsw == null) {
	    System.out.println ("DepParser:  no model loaded");
	    return;
	}
	// System.out.println ("parseSentence:  " + doc.text(span));
	// run Penn part-of-speech tagger
	// JetTest.tagger.annotate(doc, span, "tagger");
	// build sentence
	List<Token> tokens = new ArrayList<Token>();
	List<Integer> offset = new ArrayList<Integer>();
	offset.add(0); // don't use 0th entry
	int tokenNum = 0;
	int posn = span.start();
	while (posn < span.end()) {
	    tokenNum++;
	    Annotation tokenAnnotation = doc.tokenAt(posn);
	    for (String s : SPECIAL_TOKEN) {
		Vector<Annotation> va = doc.annotationsAt(posn, s);
		if (va != null && va.size() > 0) {
		    tokenAnnotation = va.get(0);
		    break;
		}
	    }
	    if (tokenAnnotation == null)
		return;
	    String tokenText = doc.normalizedText(tokenAnnotation).replaceAll(" ", "_");
	    Vector v = doc.annotationsAt(posn, "tagger");
	    Annotation a = (Annotation) v.get(0);
	    String pos = (String) a.get("cat");
	    tokens.add (new Token(tokenText, pos, tokenNum));
	    offset.add(posn);
	    if (posn >= tokenAnnotation.end()) {
		break;
	    }
	    posn = tokenAnnotation.end();
	}
	Sentence sent = new Sentence(tokens);
	// parse sentence
	Arc[] arcs = fsw.process(sent, tokens.size() > 0 && tokens.get(0).getPos() == null,
				 true, true, true, true, true).getParse().getHeadArcs();

	// regularize selected syntactic structures
	List arcList = transformer.transform(arcs,sent);

	// get dependencies
	for (Arc arc : arcs) {
	    if (arc == null) continue;
	    if (arc.getDependency().equalsIgnoreCase("ROOT")) continue;
	    Token head=arc.getHead();
	    String headText = head.getText();
	    String  headPos = head.getPos();
	    Integer headOffset = offset.get(head.getIndex());
	    Token dep=arc.getChild();
	    String depText = dep.getText();
	    String depPos = dep.getPos();
	    Integer depOffset = offset.get(dep.getIndex());
	    String type=arc.getDependency();
	    SyntacticRelation r = new SyntacticRelation 
		(headOffset, headText, headPos, type, depOffset, depText, depPos);
	    relations.add(r);
	    // System.out.println ("parseSentence:  adding relation " + r);
	}
    }

}
