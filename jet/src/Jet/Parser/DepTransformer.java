// author:  Kai Cao

package Jet.Parser;

import java.util.*;

import tratz.parse.*;
import tratz.parse.types.Arc;
import tratz.parse.types.Parse;
import tratz.parse.types.Sentence;
import tratz.parse.types.Token;

/**
 *  a series of regularizing transformations for a dependency parser (currently the Tratz-Hovy parser).
 */

public class DepTransformer {

    String transformList;

    DepTransformer (String transformList) {
	this.transformList = transformList;
    }

    /**
     *  makes three systematic revisions to the dependency structure
     *  - verb chains
     *  - changes passive to active
     *  - incorporates prepositions into arc labels
     */

    public List<Arc> transform (Arc[] arcs, Sentence sent) {
	if (transformList == null) {
	    return Arrays.asList(arcs);
	}
	/*Get the sentence tokens*/
	List<Token> tokens = sent.getTokens();
     
	/*Let us get the start and end postion of verb chains*/
	ArrayList starts = new ArrayList();
	ArrayList ends = new ArrayList();
	ArrayList<ArrayList<Integer>> paths=new ArrayList<ArrayList<Integer>>();
	ArrayList<Boolean> passives=new ArrayList<Boolean>();
	int start = -1;
	int end = -1;
	boolean passive =false;
 
	/*Let us modify starts and ends*/
	boolean [] vchLeaf=new boolean[arcs.length];
	/*Initialize the boolean values*/
	for (int i=0;i<arcs.length;i++)
	    vchLeaf[i]=true;
	/*Check whether they are leaves*/
	for (int i=1;i<arcs.length;i++){
	    Arc arc1 = arcs[i];
	    if (arc1 == null){     
		arc1 = new Arc((Token)tokens.get(i - 1), new Token("", 0), "ROOT");   
	    }
				  
	    String dep1 = arc1.getDependency();
	    Token head1 = arc1.getHead();
	    Token child1 = arc1.getChild();
				   
	    if (dep1.equals("vch"))
		vchLeaf[head1.getIndex()]=false;
	}
				  
	for (int i = 1; i < arcs.length; i++) {
	    Arc arc1 = arcs[i];
	    if (arc1 == null)
		{
		    arc1 = new Arc((Token)tokens.get(i - 1), new Token("", 0), "ROOT");
		}
	    /*If it is not a leaf contine*/
	    if (!vchLeaf[i])
		continue;
	    /*If it is a leaf */
	    String dep1 = arc1.getDependency();
	    Token head1 = arc1.getHead();
	    Token child1 = arc1.getChild();
          
				
	    /*Get along the tree structure*/
	    if (dep1.equals("vch")){
		ArrayList<Integer> path=new ArrayList<Integer>();
		path.add(i);
		int next =head1.getIndex();
		passive=false;
		if(isBe(arcs[next].getChild().getText()))
		    passive=true;
		path.add(next);
		while (arcs[next].getDependency().equals("vch")){
						
		    next=arcs[next].getHead().getIndex();
		    if(isBe(arcs[next].getChild().getText()))
			passive=true;
		    path.add(next);
						
		}
		starts.add(next);
		ends.add(i);
		passives.add(passive);
		paths.add(path);
	    }
	}//for arcs
 	  
	/*for every verb chain*/
	for (int i = 0; i < starts.size(); i++) {
	    /*Get start and end position in pairs*/
	    start = ((Integer)starts.get(i)).intValue();
	    end = ((Integer)ends.get(i)).intValue();
	    passive=passives.get(i);
				
	    /*Relink start's dependency to end*/
	    arcs[end].setHead(arcs[start].getHead());
	    arcs[end].setDependency(arcs[start].getDependency());

	    /*Redirect every dependency pointing to start to end*/
	    for (int k = 1; k < arcs.length; k++) {
		Arc arc = arcs[k];
		Token child = arc.getChild();
		Token head = arc.getHead();
		String dep = arc.getDependency();
          		  
		/*Modify the passive voice structure*/
		if ((passive) && (head.getIndex() == end) && 
		    (dep.equals("agent"))) {
		    for (int l = 1; l < arcs.length; l++) {
			Arc arc2 = arcs[l];
			Token child2 = arc2.getChild();
			Token head2 = arc2.getHead();
			String dep2 = arc2.getDependency();
			if ((head2.getIndex() == child.getIndex()) && (dep2.equals("pobj"))) {
			    arc2.setHead(head);
			    arc2.setDependency("nsubj");
			}
          
		    }
          
		}
          
		if (head.getIndex() == start) {
		    arc.setHead((Token)tokens.get(end - 1));
		    if ((passive) && (dep.equals("nsubj"))) {
			arc.setDependency("dobj");
		    }
		}
	    }
	    /*reverse the verb chain*/
	    ArrayList<Integer> path=paths.get(i);
	    for (int j = path.size()-1; j >0; j--) {
		arcs[path.get(j)].setDependency("vch");
		arcs[path.get(j)].setHead((Token)tokens.get(path.get(j-1)-1));
	    }//for every pair of verb chain
	}// for every verb chain

	/* Modify the relative clause. This may build cycles, so heuristically we have to modify it in the end.
	 * The word of "pos" will be replaced by the original word it refers.*/
	for (int i=1;i<arcs.length;i++){
	    Arc arc = arcs[i];
	    Token child = arc.getChild();
	    Token head = arc.getHead();
	    String dep = arc.getDependency();
				
	    if (dep.equals("rcmod")){
					
		/*First define the word with pos "wh"*/
		Token wh=null;
		boolean order=head.getIndex()<child.getIndex();
		if (order){
		    for (int j=head.getIndex();j<child.getIndex();j++){
			//Token tempTok=tokens.get(j);
			if (tokens.get(j).getPos().toLowerCase().startsWith("w")){
			    /*Check whether this wh word is part of the RC*/
			    boolean check=false;
			    Arc inarc = arcs[j+1];
			    int inchild = inarc.getChild().getIndex();
			    int inhead = inarc.getHead().getIndex();
			    String indep = inarc.getDependency();
								
			    //System.out.println("We just found a wh!");
			    while (inhead!=0){
				/*Part of the relative clause*/
				if (inhead==child.getIndex()){
				    check=true;
				    break;
				}//if we found that word depends on the child
				inhead=arcs[inhead].getHead().getIndex();
			    }//Find until you find the root
								
			    //if (check)
			    //System.out.println("WH!");
			    if (check)
				wh=tokens.get(j);
			    break;
			}
		    }
		}//if order
		else {
		    for (int j=head.getIndex()-2;j>=child.getIndex()-1;j--){
			if (tokens.get(j).getPos().toLowerCase().startsWith("w")){
			    /*Check whether this wh word is part of the RC*/
			    boolean check=false;
			    Arc inarc = arcs[j+1];
			    int inchild = arc.getChild().getIndex();
			    int inhead = arc.getHead().getIndex();
			    String indep = arc.getDependency();
								
			    while (inhead>0){
				/*Part of the relative clause*/
				if (inhead==child.getIndex()){
				    check=true;
				    break;
				}//if we found that word depends on the child
				inhead=arcs[inhead].getHead().getIndex();
			    }//Find until you find the root
								
			    if (check)
				wh=tokens.get(j);
			    break;
			}
		    }
		}//else order
					
		/*if we have this wh word*/
		if (wh!=null){
		    //System.out.println("There is a wh");
		    /*relink pointing to wh*/
		    for (int m=1;m<arcs.length;m++){
			Arc inarc = arcs[m];
			int inchild = arc.getChild().getIndex();
			int inhead = arc.getHead().getIndex();
			String indep = arc.getDependency();
			if (inhead==wh.getIndex())
			    inarc.setHead(wh);
		    }//for arcs
		    /*relink wh*/
		    arcs[wh.getIndex()].setChild(head);
						
		}// if wh
	    }//if we met rc
	}//for arcs			
			
	/* collapses the two-arc chain for prepositions,
	   -- prep --> P -- pobj -->
           into a single arc with dependency label prep_P */

	for (int i=1;i<arcs.length;i++){
	    Arc arc = arcs[i];
	    Token child = arc.getChild();
	    Token head = arc.getHead();
	    String dep = arc.getDependency();
				
	    if (dep.equals("pobj")){
		/*Get the head of prep*/
		Token in=head;
		Arc arc2=arcs[in.getIndex()];
		Token inhead=arc2.getHead();
		String inDep=arc2.getDependency();
					
		if (inDep.equals("prep")){
		    arc.setDependency("prep_"+in.getText());
		    arc.setHead(inhead);
		}
	    }
	}//for arcs
			
    return Arrays.asList(arcs);
    }//transform

    public static boolean isBe(String word) {
	return (word.equals("be")) || (word.equals("being")) || (word.equals("been")) || (word.equals("am")) 
	    || (word.equals("'m")) || (word.equals("is")) || (word.equals("'s")) || (word.equals("are")) 
	    || (word.equals("'re")) || (word.equals("was")) || (word.equals("were"));
    }//is be

}
