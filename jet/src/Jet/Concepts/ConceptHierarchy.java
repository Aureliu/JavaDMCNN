// -*- tab-width: 4 -*-
package Jet.Concepts;

import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.event.*;
import java.awt.*;

import java.awt.dnd.*;
import java.awt.datatransfer.*;

/**
 * A <CODE>ConceptHierarchy</CODE> is an extended JTree. It consists of
 * <CODE>Concept</CODE>s and <CODE>Word</CODE>s as its tree nodes. Besides
 * the tree data structure, it also has four hashtables to store references
 * to the data, making searching faster. These hashtables are only accessible
 * from within the packet though. One doesn't need to know the roles of
 * these hashtables in order to use the <CODE>ConceptHierarchy</CODE>.<p>
 * <CODE>ConceptHierarchy</CODE> has a default root "UNIVERSE" which is
 * invisible. User defined hierarchy either read from a file or entered
 * from a <CODE>ConceptHierarchyWindow</CODE> is added to "UNIVERSE".
 * Thus a <CODE>ConceptHierarchy</CODE> can be a forest with the
 * underlined tree structure.<p>
 * <CODE>ConceptHierarchy</CODE> should be called within a <CODE>Component</CODE>
 * in order to have its error message shown properly. See the constructors.
 */

public class ConceptHierarchy extends JTree
                              implements DragGestureListener,
                                         DragSourceListener,
                                         DropTargetListener {

  final boolean DEBUG = false;

  /**
   * The hashtable to hold <CODE>Concept</CODE>s in the hierarchy,
   * with concept name as key and concept as value.
   */
  Hashtable concepts = new Hashtable();

  /**
   * The hashtable to hold <CODE>Concept</CODE>s in the hierarachy,
   * with concept as key and concept name as value.
   */
  Hashtable conceptsInv = new Hashtable();

  /**
   * The hashtable to hold <CODE>Word</CODE>s in the hierarchy,
   * with word name as key and word as value.
   */
  Hashtable words = new Hashtable();

  /**
   * The hashtable to hold <CODE>Word</CODE>s in the hierarachy,
   * with word as key and word name as value.
   */
  Hashtable wordsInv = new Hashtable();

  private Component owner;
  private DragSource dragSource;
  private DropTarget dropTarget;
  private TreePath selectedTreePath = null;
  private DefaultMutableTreeNode selectedNode = null;
  private Point cursorLocation = null;

  /**
   * Creates a <CODE>ConceptHierarchy</CODE> with only the default root
   * <I>UNIVERSE</I>.
   */
  public ConceptHierarchy() {
    super(new Concept("UNIVERSE"));
    setRootVisible(false);
    setShowsRootHandles(true);
    setEditable(true);
    if (!Jet.JetTest.batchFlag) {
      dragSource = DragSource.getDefaultDragSource();
      dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, this);
      dropTarget = new DropTarget(this, this);
    }
  }

  /**
   * Creates a <CODE>ConceptHierarchy</CODE> from the file <CODE>fileName</CODE>.
   * @param file the file from which the concept hierarchy data is to be read
   */
  public ConceptHierarchy(File file) {
    this();
    readHierarchy(file);
  }

  public void setOwner (Component owner) {
    this.owner = owner;
  }

  /**
   * Returns the <CODE>Concept</CODE> named <CODE>conceptName</CODE> or
   * <CODE>null</CODE> if no <CODE>Concept</CODE> has <CODE>conceptName</CODE>.
   * Suppose there are no two <CODE>Concept</CODE>s in the same hierarchy
   * with the same name.
   * @param conceptName the name of the <CODE>Concept</CODE>
   * @return the <CODE>Concept</CODE> named <CODE>conceptName</CODE>
   */
  public Concept getConceptByName(String conceptName) {
    Object c = concepts.get(conceptName);
    if (c == null)
      return null;
    else
      return (Concept) c;
  }

  /**
   * Returns the <CODE>Concept</CODE> named <CODE>conceptName</CODE> (ignoring case)
   * or <CODE>null</CODE> if no <CODE>Concept</CODE> has <CODE>conceptName</CODE>.
   * Suppose there are no two <CODE>Concept</CODE>s in the same hierarchy
   * with the same name.
   * @param conceptName the name of the <CODE>Concept</CODE>
   * @return the <CODE>Concept</CODE> named <CODE>conceptName</CODE>
   */
  public Concept getConceptByNameIgnoreCase(String conceptName) {
    Enumeration enu = concepts.keys();
    String name;
    while (enu.hasMoreElements()) {
      name = (String) enu.nextElement();
      if (name.equalsIgnoreCase(conceptName))
        return (Concept) concepts.get(name);
    }
    return null;
  }

  /**
   * Returns the <CODE>Concept</CODE> of the word <CODE>word</CODE> or
   * <CODE>null</CODE> if no <CODE>Concept</CODE> has <CODE>word</CODE>.
   * Suppose there are no two <CODE>Word</CODE>s in the same hierarchy
   * with the same name.
   * @param word the word whose <CODE>Concept</CODE> is to be looked up
   * @return the <CODE>Concept</CODE> of the word <CODE>word</CODE>
   */
  public Concept getConceptFor(String word) {
    Word w = getWordByName(word);
    if (w != null)
      return (Concept) w.getParent();
    else
      return null;
  }

  /**
   * Returns the <CODE>Word</CODE> named <CODE>wordName</CODE> or
   * <CODE>null</CODE> if no <CODE>Word</CODE> has <CODE>wordName</CODE>.
   * Suppose there are no two <CODE>Word</CODE>s in the same hierarchy
   * with the same name.
   * @param wordName the name of the <CODE>Word</CODE>
   * @return the <CODE>Word</CODE> named <CODE>wordName</CODE>
   */
  public Word getWordByName(String wordName) {
    Object w = words.get(wordName);
    if (w == null)
      return null;
    else
      return (Word) w;
  }

  /**
   * Returns the <CODE>Word</CODE> named <CODE>wordName</CODE> (ignoring case)
   * or <CODE>null</CODE> if no <CODE>Word</CODE> has <CODE>wordName</CODE>.
   * Suppose there are no two <CODE>Word</CODE>s in the same hierarchy
   * with the same name.
   * @param wordName the name of the <CODE>Word</CODE>
   * @return the <CODE>Word</CODE> named <CODE>wordName</CODE>
   */
  public Word getWordByNameIgnoreCase(String wordName) {
    Enumeration enu = words.keys();
    String name;
    while (enu.hasMoreElements()) {
      name = (String) enu.nextElement();
      if (name.equalsIgnoreCase(wordName))
        return (Word) words.get(name);
    }
    return null;
  }

  /**
   * Returns true if <CODE>concept1</CODE> <I>isa</I> <CODE>concept2</CODE>,
   * <I>i.e.</I> <CODE>concept2</CODE> is the parent of <CODE>concept1</CODE>
   * in the concept hierarchy.
   * @return true if <CODE>concept2</CODE> is the parent of <CODE>concept1</CODE>,
   * else fase
   */
  public static boolean isa(Concept concept1, Concept concept2) {
    return concept2.isNodeChild(concept1);
  }

  /**
   * Returns true if <CODE>concept1</CODE> <I>isa*</I> <CODE>concept2</CODE>,
   * <I>i.e.</I> <CODE>concept2</CODE> is <CODE>concept1</CODE> itself or
   * an ancestor of <CODE>concept1</CODE> in the concept hierarchy.
   * @return true if <CODE>concept2</CODE> is <CODE>concept1</CODE> itself or
   * an ancestor of <CODE>concept1</CODE>, else false
   */
  public static boolean isaStar(Concept concept1, Concept concept2) {
    return concept1.isNodeAncestor(concept2);
  }

  /**
   * Adds a new <CODE>Concept</CODE> to this <CODE>ConceptHierarchy</CODE> as
   * a child of the node <CODE>parent</CODE>.
   * If <CODE>newConcept</CODE> is already in the hierarchy, show error message.
   * @param newConcept the new <CODE>Concept</CODE> to be added
   * @param parent the <CODE>Concept</CODE> to which <CODE>newConcept</CODE>
   * is to be added
   * @return true if succeeded, false otherwise (duplicate concepts)
   */
  public boolean addConcept(Concept newConcept, Concept parent) {
    if (!isDuplicateConcept(newConcept)) {
      parent.addConcept(newConcept);
      newConcept.setParent(parent);
      Enumeration enuConcept = newConcept.breadthFirstEnumerationOfConcepts();
      Enumeration enuWord = newConcept.breadthFirstEnumerationOfWords();
      Concept c;
      Word w;
      concepts.put(newConcept.getName(), newConcept);
      conceptsInv.put(newConcept, newConcept.getName());
      while (enuConcept.hasMoreElements()) {
        c = (Concept) enuConcept.nextElement();
        concepts.put(c.getName(), c);
        conceptsInv.put(c, c.getName());
      }
      while (enuWord.hasMoreElements()) {
        w = (Word) enuWord.nextElement();
        words.put(w.getName(), w);
        wordsInv.put(w, w.getName());
      }
//      System.out.println(newConcept + " added to " + parent);
//      printTree();
      updateUI();
      if (DEBUG) printHashtables();
      return true;
    }
    return false;
  }

  /**
   * Adds a new <CODE>Word</CODE> to this <CODE>ConceptHierarchy</CODE> as a
   * child of the node <CODE>parent</CODE>.<br>
   * All the <CODE>Word</CODE>s of <CODE>Concept parent</CODE> are listed before
   * all the <CODE>Concept</CODE>s that <I>isa</I> <CODE>parent</CODE>.
   * If <CODE>newWord</CODE> is already in the hierarchy, show error message.
   * @param newWord the new <CODE>Word</CODE> to be added
   * @param parent the <CODE>Concept</CODE> to which <CODE>newWord</CODE>
   * is to be added
   * @return true if succeeded, false otherwise (duplicate words)
   */
  public boolean addWord(Word newWord, Concept parent) {
    if (!isDuplicateWord(newWord)) {
      parent.addWord(newWord);
      newWord.setParent(parent);
      words.put(newWord.getName(), newWord);
      wordsInv.put(newWord, newWord.getName());
//      System.out.println(newWord + " added to " + parent);
//      printTree();
      updateUI();
      if (DEBUG) printHashtables();
      return true;
    }
    return false;
  }

  /**
   * Makes a new <CODE>Concept</CODE> and adds it to <CODE>selectedConcept</CODE>
   * in the hierarchy. The default name of the new <CODE>Concept</CODE> is
   * <I>New Concept</I>.
   */
  public void newConcept(Concept selectedConcept) {
    Concept newConcept = new Concept("NEW-CONCEPT");
    addConcept(newConcept, selectedConcept);
    setSelectionPath(new TreePath(newConcept.getPath()));
    startEditingAtPath(getSelectionPath());
  }

  /**
   * Makes a new <CODE>Word</CODE> and adds it to <CODE>selectedConcept</CODE>
   * in the hierarchy. The default name of the new <CODE>Word</CODE> is
   * <I>New Word</I>.
   */
  public void newWord(Concept selectedConcept) {
    Word newWord = new Word("New Word");
    addWord(newWord, selectedConcept);
    setSelectionPath(new TreePath(newWord.getPath()));
    startEditingAtPath(getSelectionPath());
  }

  /**
   * Removes a <CODE>Concept</CODE> from the hierarchy with all its children.
   * @param oldConcept the <CODE>Concept</CODE> to be removed
   * @param parent the <CODE>Concept</CODE> from which <CODE>oldConcept</CODE>
   * is to be removed
   */
  public void removeConcept(Concept oldConcept) {
    Concept parent = (Concept) oldConcept.getParent();
    parent.removeConcept(oldConcept);
    Enumeration enuConcept = oldConcept.breadthFirstEnumerationOfConcepts();
    Enumeration enuWord = oldConcept.breadthFirstEnumerationOfWords();
    Concept c;
    Word w;
    concepts.remove(oldConcept.getName());
    conceptsInv.remove(oldConcept);
    while (enuConcept.hasMoreElements()) {
      c = (Concept) enuConcept.nextElement();
      concepts.remove(c.getName());
      conceptsInv.remove(c);
    }
    while (enuWord.hasMoreElements()) {
      w = (Word) enuWord.nextElement();
      words.remove(w.getName());
      wordsInv.remove(w);
    }
    updateUI();
    if (DEBUG) printHashtables();
  }

  /**
   * Removes a <CODE>Word</CODE> from the hierarchy.
   * @param oldWord the <CODE>Word</CODE> to be removed
   * @param parent the <CODE>Concept</CODE> from which <CODE>oldWord</CODE>
   * is to be removed
   */
  public void removeWord(Word oldWord) {
    Concept parent = (Concept) oldWord.getParent();
    parent.removeWord(oldWord);
    words.remove(oldWord.getName());
    wordsInv.remove(oldWord);
    updateUI();
    if (DEBUG) printHashtables();
  }

  /**
   * Removes all the nodes from the hierarchy except the default root
   * <I>UNIVERSE</I>.
   */
  public void clear() {
    Concept root = ((Concept) getModel().getRoot());
    root.removeAllChildren();
    concepts.clear();
    conceptsInv.clear();
    words.clear();
    wordsInv.clear();
    updateUI();
    if (DEBUG) printHashtables();
  }

  /**
   * Checks if <CODE>concept</CODE> is already in the hierarchy, if so,
   * pop up an error message.
   * @param concept the concept to be checked
   * @return true if <CODE>concept</CODE> is already in the hierarchy,
   * false otherwise
   */
  public boolean isDuplicateConcept(Concept concept) {
    final Concept c = concept;
    if (concepts.containsKey(concept.getName())) {
      final String message = "Concept \'" + c.getName() +
                       "\' already exists.\nNo duplicate concepts allowed.";
      if (owner != null) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            JOptionPane.showMessageDialog(owner, message,
              "Error -- Duplicate Concept", JOptionPane.ERROR_MESSAGE);
          }
        });
      } else {
        System.out.println (message);
      }
      return true;
    }
    else
      return false;
  }

  /**
   * Checks if <CODE>word</CODE> is already in the hierarchy, if so,
   * pop up an error message.
   * @param word the word to be checked
   * @return true if <CODE>word</CODE> is already in the hierarchy,
   * false otherwise
   */
  public boolean isDuplicateWord(Word word) {
    final Word w = word;
    if (words.containsKey(word.getName())) {
      final String message = "Word \'" + w.getName() +
                             "\' already exists.\nNo duplicate words allowed.";
      if (owner != null) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            JOptionPane.showMessageDialog(owner, message, "Error -- Duplicate Word",
                                        JOptionPane.ERROR_MESSAGE);
          }
        });
      } else {
        System.out.println (message);
      }
      return true;
    }
    else
      return false;
  }

  /**
   * Re-create this <CODE>ConceptHierarchy</CODE> from the file
   * <CODE>fileName</CODE>. The current hierarchy is replaced.
   * File format is defined elsewhere with the constraint that all concepts
   * and words are listed such that all the nodes appear after their direct
   * ancestors appear, e.g. in Breadth-First order, Depth-First order, etc.
   * @param fileName the name of the file from which the concept hierarchy
   * data is to be read
   */
  public void readHierarchy(File file) {
    try {
      BufferedReader in = new BufferedReader(new FileReader(file));
      String line;
      String key;  // "isa" or "words"
      StringTokenizer stok;
      String conceptName;
      String parentName;
      String wordPart;
      StringBuffer wordBuffer;
      Concept concept;
      Concept parent;
      Word word;

      clear();

      while((line = in.readLine()) != null) {
        stok = new StringTokenizer(line);

        // skip blank lines
        if (!stok.hasMoreTokens()) continue;
        // skip comment lines beginning with "//"
        if (line.substring(0, 2).equals("//")) continue;

        conceptName = stok.nextToken();
        concept = new Concept(conceptName);

        if (stok.hasMoreTokens()) {
          key = stok.nextToken();
        }
        else {
          System.out.println("throw 01");
          throw new IOException(line);
        }

        if (key.equals("isa")) {
          if (stok.hasMoreTokens()) {
            parentName = stok.nextToken();
            parent = getConceptByName(parentName);
            if (parent == null) {
              parent = new Concept(parentName);
              addConcept(parent, (Concept) this.getModel().getRoot());
            }
            addConcept(concept, parent);
            if (stok.hasMoreTokens()) {
              System.out.println("throw 02");
              throw new IOException(line);
            }
          }
          else {
            System.out.println("throw 03");
            throw new IOException(line);
          }
        }
        else if (key.equals("words")) {
          if (!stok.hasMoreTokens()) {
            System.out.println("throw 04");
            throw new IOException(line);
          }
          concept = getConceptByName(conceptName);
          if (concept == null) {
            concept = new Concept(conceptName);
            addConcept(concept, (Concept) this.getModel().getRoot());
          }
          wordBuffer = new StringBuffer();
          while (stok.hasMoreTokens()) {
            wordBuffer.append(stok.nextToken() + " ");
            int len = wordBuffer.length();
            if (wordBuffer.substring(len - 2, len).equals(", ")) {
              word = new Word(wordBuffer.substring(0, len - 2));
              addWord(word, concept);
              wordBuffer = new StringBuffer();
            }
          }
          int len = wordBuffer.length();
          if (!wordBuffer.substring(len - 2, len).equals(", ")) {
            word = new Word(wordBuffer.toString().trim());
            addWord(word, concept);
          }
        }
        else {
          System.out.println("throw 05");
          throw new IOException(line);
        }
      }
    }
    catch (FileNotFoundException e) {
      System.err.println("Error: File " + file.toString() + " not found.");
    }
    catch (IOException e) {
      System.err.println("Error processing file " + file.toString() +
                         " at the following line:\n" + e.getMessage());
    }
  }

  /**
   * Write this <CODE>ConceptHierarchy</CODE> to the file <CODE>fileName</CODE>.
   * Previous content of the file is replaced.
   * @param fileName the name of the file to which the concept hierarchy data
   * is to be written.
   */
  public void writeHierarchy(File file) {
    try {
      PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)));
      Concept root = (Concept) getModel().getRoot();  // the default root "UNIVERSE"

      for (int i = 0; i < root.getSubconceptCount(); i++) {
        recursiveWrite(root.getSubconceptAt(i), out);
      }
    }
    catch (IOException e) {
      System.err.println("Error writing to file " + file.toString() + ".");
    }
  }

  // recusively write the hierarchy out
  private void recursiveWrite(Concept concept, PrintWriter out) {
    Concept parent = (Concept) concept.getParent();
    if (!parent.equals((Concept) getModel().getRoot())) {
      out.println(concept.getName() + " isa " + parent.getName());
      out.flush();
    }
    StringBuffer words = new StringBuffer();
    for (int i = 0; i < concept.getWordCount(); i++) {
      if (i == 0)
        words.append(concept.getWordAt(i).getName());
      else
        words.append(", " + concept.getWordAt(i).getName());
    }
    if (words.length() > 0) {
      out.println(concept.getName() + " words " + words.toString());
      out.flush();
    }
    for (int i = 0; i < concept.getSubconceptCount(); i++) {
      Concept nextConcept = concept.getSubconceptAt(i);
      recursiveWrite(nextConcept, out);
    }
  }

  //
  // Interfaces
  //

  /**
   * Method of TreeSelectionListener interface
   */
/*  public void valueChanged(TreeSelectionEvent e) {
    selectedTreePath = e.getNewLeadSelectionPath();
    if (selectedTreePath == null) {
      selectedNode = null;
      return;
    }
    selectedNode = (DefaultMutableTreeNode) selectedTreePath.getLastPathComponent();
  }
*/
  /**
   * Method of DragGestureListener interface
   */
  public void dragGestureRecognized(DragGestureEvent dge) {
    selectedTreePath = getSelectionPath();
    if (selectedTreePath != null) {
      selectedNode = (DefaultMutableTreeNode) selectedTreePath.getLastPathComponent();
      Transferable tr = new TransferableTreeNode(selectedNode);

      Cursor cursor = DragSource.DefaultMoveDrop;
      int action = dge.getDragAction();
      if (action == DnDConstants.ACTION_COPY)
        cursor = DragSource.DefaultCopyDrop;

      dragSource.startDrag(dge, cursor, tr, this);
      // startDrag(DragGestureEvent trigger, Cursor dragCursor, transferable tr, DragSourceListener dsl)
    }
  }

  /**
   * Method of DragSourceListener interface
   */
  public void dragDropEnd(DragSourceDropEvent dsde) {}

  /**
   * Method of DragSourceListener interface
   */
  public void dragEnter(DragSourceDragEvent dsde) {
    setCursor(dsde);
  }

  /**
   * Method of DragSourceListener interface
   */
  public void dragOver(DragSourceDragEvent dsde) {
    setCursor(dsde);
  }

  /**
   * Method of DragSourceListener interface
   */
  public void dropActionChanged(DragSourceDragEvent dsde) {}

  /**
   * Method of DragSourceListener interface
   */
  public void dragExit(DragSourceEvent dsde) {}

  /**
   * Method of DropTargetListener interface
   */
  public void drop(DropTargetDropEvent e) {
    try {
      Transferable tr = e.getTransferable();

      //flavor not supported, reject drop
      if (!tr.isDataFlavorSupported(TransferableTreeNode.DEFAULT_MUTABLE_TREENODE_FLAVOR))
        e.rejectDrop();

      Object userObject = tr.getTransferData(TransferableTreeNode.DEFAULT_MUTABLE_TREENODE_FLAVOR);

      //get new parent node
      Point loc = e.getLocation();
      TreePath destinationPath = getPathForLocation(loc.x, loc.y);

      final String msg = testDropTarget(destinationPath, selectedTreePath);
      if (msg != null) {
        e.rejectDrop();
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            JOptionPane.showMessageDialog(owner,
                                          msg,
                                          "Error Moving Node",
                                          JOptionPane.ERROR_MESSAGE);
          }
        });
        return;
      }

//      int action = e.getDropAction();
//      boolean copyAction = (action == DnDConstants.ACTION_COPY);
      Concept newParent = (Concept) destinationPath.getLastPathComponent();
      Concept oldParent = (Concept) selectedNode.getParent();

      try {
        if (userObject instanceof Concept) {
          // only allows move, not copy
          Concept child = (Concept) userObject;
          removeConcept((Concept) selectedNode);
          addConcept(child, newParent);
          e.acceptDrop(DnDConstants.ACTION_MOVE);
        }
        else { //userObject instanceof Word
          // only allows move, not copy
          Word child = (Word) userObject;
          removeWord((Word) selectedNode);
          addWord(child, newParent);
          e.acceptDrop(DnDConstants.ACTION_MOVE);
        }
      }
      catch (IllegalStateException ils) {
        e.rejectDrop();
      }

      e.getDropTargetContext().dropComplete(true);

      DefaultTreeModel model = (DefaultTreeModel) getModel();
      model.reload(oldParent);
      model.reload(newParent);
      TreePath parentPath = new TreePath(newParent.getPath());
      expandPath(parentPath);
    }
    catch (IOException io) {
      e.rejectDrop();
    }
    catch (UnsupportedFlavorException ufe) {
      e.rejectDrop();
    }
  }

  /**
   * Method of DropTaregetListener interface
   */
  public void dragEnter(DropTargetDragEvent e) {}

  /**
   * Method of DropTaregetListener interface
   */
  public void dragOver(DropTargetDragEvent e) {
    cursorLocation = e.getLocation();
  }

  /**
   * Method of DropTaregetListener interface
   */
  public void dragExit(DropTargetEvent e) {}

  /**
   * Method of DropTaregetListener interface
   */
  public void dropActionChanged(DropTargetDragEvent e) {}

  /**
   * Tests whether drop location is valid
   * @param destination The destination path
   * @param dropper The path for the node to be dropped
   * @return null if no problems, otherwise an explanation
   */
  private String testDropTarget(TreePath destination, TreePath dropper) {

    if (destination == null)
      return "Invalid drop location.";

    Object node = destination.getLastPathComponent();
    if (node instanceof Word)
      return "A word does not allow children.";

    if (destination.equals(dropper))
      return "Destination cannot be same as source.";

    if (dropper.isDescendant(destination))
       return "Destination cannot be a descendant of source.";

    if ( dropper.getParentPath().equals(destination))
       return "Destination cannot be a parent of source.";

    return null;
  }

  private void setCursor(DragSourceDragEvent dsde) {
    if (cursorLocation == null) return;
    TreePath destinationPath = getPathForLocation(cursorLocation.x, cursorLocation.y);
    DragSourceContext dsc = dsde.getDragSourceContext();
    if (testDropTarget(destinationPath, selectedTreePath) == null)
      dsc.setCursor(DragSource.DefaultMoveDrop);
    else
      dsc.setCursor(DragSource.DefaultMoveNoDrop);
  }
/*
  // For debugging
  private void printTree() {
    System.out.println("\nprintTree()");
    Enumeration e = ((DefaultMutableTreeNode) this.getModel().getRoot()).breadthFirstEnumeration();
    while (e.hasMoreElements()) {
      System.out.println(e.nextElement());
    }
    System.out.println();
  }
*/
  // For debugging
  void printHashtables() {
    System.out.println("\n***concepts***\n");
    Enumeration conceptEnu = concepts.elements();
    while (conceptEnu.hasMoreElements()) {
      System.out.println(conceptEnu.nextElement());
    }
    System.out.println("\n***conceptsInv***\n");
    Enumeration conceptInvEnu = conceptsInv.elements();
    while (conceptInvEnu.hasMoreElements()) {
      System.out.println(conceptInvEnu.nextElement());
    }
    System.out.println("\n***words***\n");
    Enumeration wordEnu = words.elements();
    while (wordEnu.hasMoreElements()) {
      System.out.println(wordEnu.nextElement());
    }
    System.out.println("\n***wordsInv***\n");
    Enumeration wordInvEnu = wordsInv.elements();
    while (wordInvEnu.hasMoreElements()) {
      System.out.println(wordInvEnu.nextElement());
    }
  }
}
