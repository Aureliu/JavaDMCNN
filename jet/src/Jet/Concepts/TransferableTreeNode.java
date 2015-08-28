// -*- tab-width: 4 -*-
package Jet.Concepts;

import javax.swing.tree.*;
import java.awt.dnd.*;
import java.awt.datatransfer.*;
import java.io.*;

public class TransferableTreeNode /*extends DefaultMutableTreeNode*/ implements Transferable {

  final public static DataFlavor DEFAULT_MUTABLE_TREENODE_FLAVOR =
    new DataFlavor(DefaultMutableTreeNode.class, "Default Mutable Tree Node");

  static DataFlavor flavors[] = {DEFAULT_MUTABLE_TREENODE_FLAVOR};

  private DefaultMutableTreeNode data;

  public TransferableTreeNode(DefaultMutableTreeNode node) {
    data = node;
  }
/*
  public TransferableTreeNode() {
    super();
    data = new DefaultMutableTreeNode();
  }

  public TransferableTreeNode(Object userObject) {
    super(userObject);
    data = new DefaultMutableTreeNode(userObject);
  }

  public TransferableTreeNode(Object userObject, boolean allowsChildren) {
    super(userObject, allowsChildren);
    data = new DefaultMutableTreeNode(userObject, allowsChildren);
  }
*/
/*
  public TransferableTreeNode(DefaultMutableTreeNode data) {
    super(data);
    this.data = data;
  }

  public TransferableTreeNode(Object userObject) {
    super(userObject);
    data = new DefaultMutableTreeNode(userObject);
  }

  public TransferableTreeNode() {
    super();
    data = new DefaultMutableTreeNode();
  }
*/
  // 3 methods of Transferable
  public DataFlavor[] getTransferDataFlavors() {
    return flavors;
  }

  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    if (flavor.equals(DEFAULT_MUTABLE_TREENODE_FLAVOR)) {
//        System.out.println("my getTransferData invoked");
        return data;
    }
    else {
      throw new UnsupportedFlavorException(flavor);
    }
  }

  public boolean isDataFlavorSupported(DataFlavor flavor) {
    for (int i = 0; i < flavors.length; i++)
      if (flavor.equals(flavors[i]))
        return true;
    return false;
  }

}

