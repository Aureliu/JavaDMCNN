// -*- tab-width: 4 -*-
package Jet.Concepts;

import java.io.IOException;
import java.util.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.datatransfer.*;

/**
 * A <CODE>Concept</CODE> is basically a <CODE>DefaultMutableTreeNode</CODE>.
 * Its <CODE>userObject</CODE> is its name as a <CODE>String</CODE>. It allows
 * children who can be either a <CODE>Concept</CODE> or a <CODE>word</CODE>.<p>
 * <CODE>Concept</CODE> instances are transferable.
 */

public class Concept extends DefaultMutableTreeNode implements Transferable {

  /**
   * The <CODE>Concept</CODE>s that <I>isa</I> this <CODE>Concept</CODE>
   */
  protected Vector subconcepts = new Vector();

  /**
   * The <CODE>Word</CODE>s of this <CODE>Concept</CODE>
   */
  protected Vector words = new Vector();

  /**
   * The flavor for <CODE>DefaultMutableTreeNode</CODE>
   */
  final public static DataFlavor DEFAULT_MUTABLE_TREENODE_FLAVOR =
    new DataFlavor(DefaultMutableTreeNode.class, "Default Mutable Tree Node");

  /**
   * The flavors supported by <CODE>Concept</CODE>.
   */
  static DataFlavor[] flavors = {DEFAULT_MUTABLE_TREENODE_FLAVOR};

  /**
   * Creates a tree node with no children and parent, initialized
   * with the name <CODE>conceptName<CODE> and that allows children.
   */
  public Concept(Object conceptName) {
    super(conceptName, true);
  }

  /**
   * Returns the name of this <CODE>Concept</CODE>.
   * @return the name of this <CODE>Concept</CODE>
   */
  public String getName() {
    return (String) super.getUserObject();
  }

  /**
   * Overrides the <CODE>isLeaf()</CODE> method in <CODE>DefaultMutableTreeNode</CODE>
   * so that only <CODE>Word</CODE>s are treated as leaves and <CODE>Concept</CODE>s
   * are always treated as internal nodes. This is for the graphical representation
   * of tree nodes.
   * @return always <CODE>false</CODE>
   */
  public boolean isLeaf() {
    return false;
  }

  /**
   * Returns the vector of the subconcepts of this <CODE>Concept</CODE>.
   * @return the vector of the subconcepts of this <CODE>Concept</CODE>
   */
  public Vector getSubconcepts() {
    return subconcepts;
  }

  /**
   * Returns the vector of the words of this <CODE>Concept</CODE>.
   * @return the vector of the words of this <CODE>Concept</CODE>
   */
  public Vector getWords() {
    return words;
  }

  /**
   * Returns the number of subconcepts of this <CODE>Concept</CODE>.
   * @return the number of subconcepts of this <CODE>Concept</CODE>
   */
  public int getSubconceptCount() {
    return subconcepts.size();
  }

  /**
   * Returns the number of <CODE>Word</CODE>s of this <CODE>Concept</CODE>.
   * @return the number of <CODE>Word</CODE>s of this <CODE>Concept</CODE>
   */
  public int getWordCount() {
    return words.size();
  }

  /**
   * Returns the subconcept at the specified index in this
   * <CODE>Concept</CODE>'s subconcept array.
   * @return the subconcept at the specified index in this
   * <CODE>Concept</CODE>'s subconcept array
   */
  public Concept getSubconceptAt(int index) {
    return (Concept) subconcepts.get(index);
  }

  /**
   * Returns the <CODE>Word</CODE> at the specified index in this
   * <CODE>Concept</CODE>'s <CODE>Word</CODE> array.
   * @return the <CODE>Word</CODE> at the specified index in this
   * <CODE>Concept</CODE>'s <CODE>Word</CODE> array
   */
  public Word getWordAt(int index) {
    return (Word) words.get(index);
  }

  /**
   * Adds a new <CODE>Concept</CODE> to this <CODE>Concept</CODE>.
   * @param newConcept the new <CODE>Concept</CODE> to be added to this
   * <CODE>Concept</CODE>
   */
  public void addConcept(Concept newConcept) {
    super.add(newConcept);
//    System.out.println(super.children);
    subconcepts.add(newConcept);
  }

  /**
   * Adds a new <CODE>Word</CODE> to this <CODE>Concept</CODE>.
   * All the words are listed before all the subconcepts in the vector
   * <CODE>children</CODE> inherited from <CODE>super</CODE>.
   * @param newWord the new <CODE>Word</CODE> to be added to this
   * <CODE>Concept</CODE>
   */
  public void addWord(Word newWord) {
    if (super.children == null)
      super.children = new Vector();
    super.children.add(words.size(), newWord);
//    System.out.println(super.children);
    words.add(newWord);
  }

  /**
   * Removes a subconcept with its children from this <CODE>Concept</CODE>.
   * @param oldConcept the subconcept to be removed from this
   * <CODE>Concept</CODE>
   */
  public void removeConcept(Concept oldConcept) {
    super.remove(oldConcept);
    subconcepts.remove(oldConcept);
  }

  /**
   * Removes a word from this <CODE>Concept</CODE>.
   * @param oldWord the word to be removed from this <CODE>Concept</CODE>
   */
  public void removeWord(Word oldWord) {
    super.remove(oldWord);
    words.remove(oldWord);
  }

  /**
   * Overrides the <CODE>removeAllchildren()</CODE> method
   * of <CODE>DefaultMutableTreeNode</CODE>.
   */
  public void removeAllChildren() {
    super.removeAllChildren();
    subconcepts.clear();
    words.clear();
  }

  /**
   * Creates and returns an enumeration of the <CODE>Concept</CODE>s
   * in the subtree rooted at this node in breadth-first order.
   * @return enumeration of the <CODE>Concept</CODE>s in the subtree
   * rooted at this node in breadth-first order
   */
  public Enumeration breadthFirstEnumerationOfConcepts() {
    Object o = null;
    Vector v = new Vector();
    Enumeration e = super.breadthFirstEnumeration();
    while (e.hasMoreElements()) {
      if ((o = e.nextElement()) instanceof Concept)
        v.add(o);
    }
    return v.elements();
  }

  /**
   * Creates and returns an enumeration of the <CODE>Word</CODE>s
   * in the subtree rooted at this node in breadth-first order.
   * @return enumeration of the <CODE>Word</CODE>s in the subtree
   * rooted at this node in breadth-first order
   */
  public Enumeration breadthFirstEnumerationOfWords() {
    Object o = null;
    Vector v = new Vector();
    Enumeration e = super.breadthFirstEnumeration();
    while (e.hasMoreElements()) {
      if ((o = e.nextElement()) instanceof Word)
        v.add(o);
    }
    return v.elements();
  }

  /**
   * This is a <CODE>Transferable</CODE> method.
   */
  public Object getTransferData(DataFlavor flavor) throws IOException, UnsupportedFlavorException {
    if (flavor.equals(flavors[0]))
      return this;
    else
      throw new UnsupportedFlavorException(flavor);
  }

  /**
   * This is a <CODE>Transferable</CODE> method.
   */
  public DataFlavor[] getTransferDataFlavors() {
    return flavors;
  }

  /**
   * This is a <CODE>Transferable</CODE> method.
   */
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    for (int i = 0; i < flavors.length; i++)
      if (flavor.equals(flavors[i]))
        return true;
    return false;
  }
}
