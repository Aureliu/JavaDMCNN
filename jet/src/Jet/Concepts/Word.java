// -*- tab-width: 4 -*-
package Jet.Concepts;

import java.io.IOException;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.datatransfer.*;

/**
 * A <CODE>Word</CODE> is basically a <CODE>DefaultMutableTreeNode</CODE>.
 * Its <CODE>userObject</CODE> is the word itself as a <CODE>String</CODE>.
 * It does not allow children.<br>
 * <CODE>Word</CODE> instances are transferable.
 */

public class Word extends DefaultMutableTreeNode implements Transferable {

  /**
   * Creates a tree node with no children and parent, initialized
   * with the <CODE>word<CODE> as its user object and that does not
   * allow children.
   */
  public Word(Object word) {
    super(word, false);
  }

  /**
   * Returns the name of this <CODE>Word</CODE>.
   * @return the name of this <CODE>Word</CODE>
   */
  public String getName() {
    return (String) super.getUserObject();
  }

  /**
   * The flavors supported by <CODE>Word</CODE>.
   */
  static DataFlavor[] flavors = {Concept.DEFAULT_MUTABLE_TREENODE_FLAVOR};

  /**
   * Overrides the <CODE>isLeaf()</CODE> method in <CODE>DefaultMutableTreeNode</CODE>
   * so that only <CODE>Word</CODE>s are treated as leaves and <CODE>Concept</CODE>s
   * are always treated as internal nodes. This is for the graphical representation
   * of tree nodes.
   * @return always <CODE>true</CODE>
   */
  public boolean isLeaf() {
    return true;
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
