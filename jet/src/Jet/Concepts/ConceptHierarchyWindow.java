// -*- tab-width: 4 -*-
package Jet.Concepts;

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.tree.*;

/**
 * <CODE>ConceptHierarchyWindow</CODE> is a UI that uses the
 * <CODE>ConceptHierarchy</CODE>. It provides node add, delete, search,
 * drag-and-drop, and file IO functions for the underlying
 * <CODE>ConceptHierarchy</CODE>. Proper error messages will pop out
 * in some cases.
 */

public class ConceptHierarchyWindow extends JFrame implements ActionListener {

  public static ConceptHierarchy conceptHierarchy;
  private JScrollPane jScrollPane = new JScrollPane();
  private final JFileChooser fcOpen = new JFileChooser(".");
  private final JFileChooser fcSave = new JFileChooser(".");
  private final JFileChooser fcSaveAs = new JFileChooser(".");
  private File currentFile = null;
  private FindDialog[] findDialog = new FindDialog[2];
  boolean dirty = false;

  public ConceptHierarchyWindow(ConceptHierarchy ch, File file) {
    try {
      conceptHierarchy = ch;
      currentFile = file;
      conceptHierarchy.setOwner(this);
      conceptHierarchy.getModel().addTreeModelListener(new TreeModelListener() {
        public void treeNodesChanged(TreeModelEvent e) {
          Object changedNode = conceptHierarchy.getSelectionPath().getLastPathComponent();
          if (changedNode instanceof Concept) {
            Concept changedConcept = (Concept) changedNode;
            if (changedConcept.getName().length() == 0) {
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  JOptionPane.showMessageDialog(ConceptHierarchyWindow.this,
                                                "You must type a concept name.",
                                                "Error Renaming Concept",
                                                JOptionPane.ERROR_MESSAGE);
                }
              });
              conceptHierarchy.startEditingAtPath(conceptHierarchy.getSelectionPath());
            }
            String oldConceptName = (String) conceptHierarchy.conceptsInv.get(changedNode);
            if (!changedConcept.getName().equals(oldConceptName) &&
                conceptHierarchy.isDuplicateConcept(changedConcept)) {
              conceptHierarchy.startEditingAtPath(conceptHierarchy.getSelectionPath());
            }
            else {
              conceptHierarchy.concepts.remove(oldConceptName);
              conceptHierarchy.concepts.put(changedConcept.getName(), changedConcept);
              conceptHierarchy.conceptsInv.remove(changedNode);
              conceptHierarchy.conceptsInv.put(changedConcept, changedConcept.getName());
            }
          }
          else { // changedNode instanceof Word
            Word changedWord = (Word) changedNode;
            if (changedWord.getName().length() == 0) {
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  JOptionPane.showMessageDialog(ConceptHierarchyWindow.this,
                                                "You must type a word.",
                                                "Error Renaming Word",
                                                JOptionPane.ERROR_MESSAGE);
                }
              });
              conceptHierarchy.startEditingAtPath(conceptHierarchy.getSelectionPath());
            }
            String oldWordName = (String) conceptHierarchy.wordsInv.get(changedNode);
            if (!changedWord.getName().equals(oldWordName) &&
                conceptHierarchy.isDuplicateWord(changedWord)) {
              conceptHierarchy.startEditingAtPath(conceptHierarchy.getSelectionPath());
            }
            else {
              conceptHierarchy.words.remove(oldWordName);
              conceptHierarchy.words.put(changedWord.getName(), changedWord);
              conceptHierarchy.wordsInv.remove(changedNode);
              conceptHierarchy.wordsInv.put(changedWord, changedWord.getName());
            }
          }
          dirty = true;
          updateCaption();
          if (conceptHierarchy.DEBUG) conceptHierarchy.printHashtables();
        }
        public void treeNodesInserted(TreeModelEvent e) {
          dirty = true;
          updateCaption();
        }
        public void treeNodesRemoved(TreeModelEvent e) {
          dirty = true;
          updateCaption();
        }
        public void treeStructureChanged(TreeModelEvent e) {
          dirty = true;
          updateCaption();
        }
      });

      jbInit();
      ConcreteFileFilter filter = new ConcreteFileFilter("hrc", "Concept Hierarchy Files (*.hrc)");
      fcOpen.setFileFilter(filter);
      fcSave.setFileFilter(filter);
      fcSaveAs.setFileFilter(filter);
      findDialog[0] = new FindDialog(this, 0);
      findDialog[1] = new FindDialog(this, 1);
      findDialog[0].pack();
      findDialog[1].pack();

      //Center the window
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      Dimension frameSize = this.getSize();
      // System.out.println(frameSize);
      if (frameSize.height > screenSize.height) {
        frameSize.height = screenSize.height;
      }
      if (frameSize.width > screenSize.width) {
        frameSize.width = screenSize.width;
      }
      this.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);

      setVisible(true);
    }
    catch(Exception e) {
      e.printStackTrace();
    }
  }

  private void jbInit() throws Exception {

    // Menu Bar
    JMenuBar jmb = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    fileMenu.setMnemonic(KeyEvent.VK_F);
    JMenuItem item;

    item = new JMenuItem("Open ...");
    item.setMnemonic(KeyEvent.VK_O);
    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
    fileMenu.add(item);
    item.addActionListener(this);

    item = new JMenuItem("Save");
    item.setMnemonic(KeyEvent.VK_S);
    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
    fileMenu.add(item);
    item.addActionListener(this);

    item = new JMenuItem("Save As ...");
    item.setMnemonic(KeyEvent.VK_A);
    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK + ActionEvent.ALT_MASK));
    fileMenu.add(item);
    item.addActionListener(this);

    item = new JMenuItem("Clear");
    item.setMnemonic(KeyEvent.VK_C);
    fileMenu.add(item);
    item.addActionListener(this);

    fileMenu.addSeparator();

    item = new JMenuItem("Exit");
    item.setMnemonic(KeyEvent.VK_X);
    fileMenu.add(item);
    item.addActionListener(this);

    JMenu editMenu = new JMenu("Edit");
    editMenu.setMnemonic(KeyEvent.VK_E);

    JMenu newSubmenu = new JMenu("New");
    newSubmenu.setMnemonic(KeyEvent.VK_N);
    editMenu.add(newSubmenu);

    item = new JMenuItem("Concept");
    item.setMnemonic(KeyEvent.VK_C);
    newSubmenu.add(item);
    item.addActionListener(this);

    item = new JMenuItem("Word");
    item.setMnemonic(KeyEvent.VK_W);
    newSubmenu.add(item);
    item.addActionListener(this);

    item = new JMenuItem("Delete");
    item.setMnemonic(KeyEvent.VK_D);
    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, ActionEvent.CTRL_MASK));
    editMenu.add(item);
    item.addActionListener(this);

    item = new JMenuItem("Rename");
    item.setMnemonic(KeyEvent.VK_R);
    editMenu.add(item);
    item.addActionListener(this);

    editMenu.addSeparator();

    JMenu findSubmenu = new JMenu("Find ...");
    findSubmenu.setMnemonic(KeyEvent.VK_F);
//    findSubmenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK));
    editMenu.add(findSubmenu);

    item = new JMenuItem("Concept ");
    item.setMnemonic(KeyEvent.VK_C);
    findSubmenu.add(item);
    item.addActionListener(this);

    item = new JMenuItem("Word ");
    item.setMnemonic(KeyEvent.VK_W);
    findSubmenu.add(item);
    item.addActionListener(this);

    jmb.add(fileMenu);
    jmb.add(editMenu);
    setJMenuBar(jmb);

    fcOpen.setDialogTitle("Open");
    fcOpen.setApproveButtonText("Open");
    fcOpen.setApproveButtonMnemonic(KeyEvent.VK_O);
    fcOpen.setApproveButtonToolTipText("Open Hierarchy");
    fcSave.setDialogTitle("Save");
    fcSave.setApproveButtonText("Save");
    fcSave.setApproveButtonMnemonic(KeyEvent.VK_S);
    fcSave.setApproveButtonToolTipText("Save Hierarchy");
    fcSaveAs.setDialogTitle("Save As");
    fcSaveAs.setApproveButtonText("Save");
    fcSaveAs.setApproveButtonMnemonic(KeyEvent.VK_S);
    fcSaveAs.setApproveButtonToolTipText("Save Hierarchy");

    // Content Pane
    conceptHierarchy.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
/*    conceptHierarchy.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        TreeNode node = (TreeNode) (e.getPath().getLastPathComponent());
          nodeSelected(node);
      }
    });
*/
    jScrollPane.getViewport().add(conceptHierarchy, null);

    updateCaption();
    this.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        if (okToAbandon())
          ConceptHierarchyWindow.this.dispose();
//        else
//          ConceptHierarchyWindow.this.setVisible(true);
      }
    });
    this.getContentPane().setLayout(new BorderLayout());
    this.getContentPane().add(jScrollPane, BorderLayout.CENTER);
    this.setSize(400, 600);
  }

  /**
   *  called to handle menu actions.
   */
  public void actionPerformed(ActionEvent e) {
    if (e.getActionCommand().equals("Open ...")) {
      if (okToAbandon()) {
        int returnVal = fcOpen.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
          currentFile = fcOpen.getSelectedFile();
          conceptHierarchy.readHierarchy(currentFile);
          dirty = false;
          updateCaption();
        }
      }
    }
    else if (e.getActionCommand().equals("Save")) {
      if (!conceptHierarchy.isEditing()) {
        saveFile();
      }
    }
    else if (e.getActionCommand().equals("Save As ...")) {
      if (!conceptHierarchy.isEditing()) {
        if (currentFile == null) {
          File tempFile = new File("Untitled Hierarchy.hrc");
          fcSaveAs.setSelectedFile(tempFile);
        }
        int returnVal = fcSaveAs.showDialog(this, null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
          currentFile = fcSaveAs.getSelectedFile();
          conceptHierarchy.writeHierarchy(currentFile);
          dirty = false;
          updateCaption();
        }
      }
    }
    else if (e.getActionCommand().equals("Clear")) {
      conceptHierarchy.clear();
      dirty = true;
      updateCaption();
    }
    else if (e.getActionCommand().equals("Concept")) {
      if (!conceptHierarchy.isEditing()) {
        TreePath selectedPath = conceptHierarchy.getSelectionPath();
        if (selectedPath != null) {
          Object selectedNode = selectedPath.getLastPathComponent();
          if (selectedNode instanceof Concept) {
            conceptHierarchy.newConcept((Concept) selectedNode);
          }
        }
        else {
          conceptHierarchy.newConcept((Concept) conceptHierarchy.getModel().getRoot());
        }
        dirty = true;
        updateCaption();
      }
    }
    else if (e.getActionCommand().equals("Word")) {
      if (!conceptHierarchy.isEditing()) {
        TreePath selectedPath = conceptHierarchy.getSelectionPath();
        if (selectedPath != null) {
          Object selectedNode = selectedPath.getLastPathComponent();
          if (selectedNode instanceof Concept) {
            conceptHierarchy.newWord((Concept) selectedNode);
          }
        }
        else {
          conceptHierarchy.newWord((Concept) conceptHierarchy.getModel().getRoot());
        }
        dirty = true;
        updateCaption();
      }
    }
    else if (e.getActionCommand().equals("Delete")) {
      if (!conceptHierarchy.isEditing()) {
        TreePath selectedPath = conceptHierarchy.getSelectionPath();
        if (selectedPath != null) {
          Object selectedNode = selectedPath.getLastPathComponent();
          if (selectedNode instanceof Concept) {
            conceptHierarchy.removeConcept((Concept) selectedNode);
          }
          else { // selectedNode instanceof Word
            conceptHierarchy.removeWord((Word) selectedNode);
          }
        }
        dirty = true;
        updateCaption();
      }
    }
    else if (e.getActionCommand().equals("Rename")) {
      if (!conceptHierarchy.isEditing()) {
        conceptHierarchy.startEditingAtPath(conceptHierarchy.getSelectionPath());
        dirty = true;
        updateCaption();
      }
    }
    else if (e.getActionCommand().equals("Concept ")) {
      if (!conceptHierarchy.isEditing())
        find(0);
    }
    else if (e.getActionCommand().equals("Word ")) {
      if (!conceptHierarchy.isEditing())
        find(1);
    }
    else { // e.getActionCommand().equals("Exit")
      if (okToAbandon())
        this.dispose();
    }

  }

  /**
   * Finds the concept in the hierarchy if mode is 0;
   * finds the word in the hierarchy if mode is 1.
   * Suppose there no two concepts have the same name
   * and no two words have the same name.
   */
  private void find(int mode) {
    findDialog[mode].setLocationRelativeTo(this);
    findDialog[mode].setVisible(true);
    String query = findDialog[mode].getQuery();
    boolean caseSensitive = findDialog[mode].getCaseSensitivity();
    Concept c;
    Word w;

    if (query == null) return;

    if (mode == 0) {
      if (caseSensitive)
        c = conceptHierarchy.getConceptByName(query);
      else
        c = conceptHierarchy.getConceptByNameIgnoreCase(query);

      if (c != null) {
        TreePath path = new TreePath(c.getPath());
        conceptHierarchy.setSelectionPath(path);
        conceptHierarchy.scrollPathToVisible(path);
      }
      else {
        JOptionPane.showMessageDialog(findDialog[0],
                                      "Concept \'" + query + "\' not found.",
                                      "Search Failed",
                                      JOptionPane.ERROR_MESSAGE);
      }
    }
    else { // mode == 1
      if (caseSensitive)
        w = conceptHierarchy.getWordByName(query);
      else
        w = conceptHierarchy.getWordByNameIgnoreCase(query);

      if (w != null) {
        TreePath path = new TreePath(w.getPath());
        conceptHierarchy.setSelectionPath(path);
        conceptHierarchy.scrollPathToVisible(path);
      }
      else {
        JOptionPane.showMessageDialog(findDialog[1],
                                      "Word \'" + query + "\' not found.",
                                      "Search Failed",
                                      JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  /**
   * Updates the caption of the application to show the filename
   * and its dirty state.
   */
  void updateCaption() {
    String caption;

    if (currentFile == null)
      caption = "Untitled Hierarchy.hrc";
    else
      caption = currentFile.getAbsolutePath();

    // add a "*" in the caption if the file is dirty.
    if (dirty)
      caption = caption + "*";
    caption = "Concept Hierarchy - " + caption;
    this.setTitle(caption);
  }

  /**
   * Checks if file is dirty. If so pop up a confirm dialog.
   */
  private boolean okToAbandon() {
    if (!dirty)
      return true;
    String currentFileName;
    if (currentFile != null)
      currentFileName = currentFile.getName();
    else
      currentFileName = "Untitled Hierarchy.hrc";
    int value = JOptionPane.showConfirmDialog(
        this,
        "File modified: \"" + currentFileName + "\". Do you wish to save changes?",
        "Save Modified File?",
        JOptionPane.YES_NO_CANCEL_OPTION);
    switch (value) {
      case JOptionPane.YES_OPTION:
        saveFile();
        return true;
      case JOptionPane.NO_OPTION:
        return true;
      case JOptionPane.CANCEL_OPTION:
      default:
        return false;
    }
  }

  /**
   * Saves a file.
   */
  private void saveFile() {
    if (currentFile != null) {
      conceptHierarchy.writeHierarchy(currentFile);
      dirty = false;
      updateCaption();
    }
    else {
      File tempFile = new File("Untitled Hierarchy.hrc");
//      fc.ensureFileIsVisible(currentFile);
//	fc.setCurrentDirectory(currentFile);
      fcSave.setSelectedFile(tempFile);
      int returnVal = fcSave.showSaveDialog(this);
      if (returnVal == JFileChooser.APPROVE_OPTION) {
        currentFile = fcSave.getSelectedFile();
        conceptHierarchy.writeHierarchy(currentFile);
        dirty = false;
        updateCaption();
      }
    }
  }


  class FindDialog extends JDialog {
    private String query = null;
    private boolean caseSensitivity;
    private JOptionPane optionPane;
    private final int mode;

    public String getQuery() {
      return query;
    }

    public boolean getCaseSensitivity() {
      return caseSensitivity;
    }

    public FindDialog(Frame owner, int m) {
      super(owner, "Find", true);
      mode = m;
/*
      if (mode == 0)
        super(owner, "Find Concept", true);
      else
        super(owner, "Find Word", true);
*/
      final String[] msgString = { "Enter a concept name to find.",
                                   "Enter a word to find" };
      JRadioButton[] radioButtons = new JRadioButton[2];
      final ButtonGroup group = new ButtonGroup();
      radioButtons[0] = new JRadioButton("Case Insensitive");
      radioButtons[0].setActionCommand("insensitive");
      radioButtons[1] = new JRadioButton("Case Sensitive");
      radioButtons[1].setActionCommand("sensitive");
      group.add(radioButtons[0]);
      group.add(radioButtons[1]);
      radioButtons[0].setSelected(true);
      final JTextField textField = new JTextField(10);
      Object[] array = {msgString[mode], radioButtons[0], radioButtons[1], textField};

      final String btnString1 = "Find";
      final String btnString2 = "Cancel";
      Object[] options = {btnString1, btnString2};

      optionPane = new JOptionPane(array,
                                   JOptionPane.QUESTION_MESSAGE,
                                   JOptionPane.YES_NO_OPTION,
                                   null,
                                   options,
                                   options[0]);
      setContentPane(optionPane);
      setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
      addWindowListener(new WindowAdapter() {
        public void windowClosing(WindowEvent we) {
          optionPane.setValue(new Integer(JOptionPane.CLOSED_OPTION));
        }
      });

      textField.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          optionPane.setValue(btnString1);
        }
      });

      optionPane.addPropertyChangeListener(new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent e) {
          String prop = e.getPropertyName();

          if (isVisible()
            && (e.getSource() == optionPane)
            && (prop.equals(JOptionPane.VALUE_PROPERTY) ||
            prop.equals(JOptionPane.INPUT_VALUE_PROPERTY))) {

            Object value = optionPane.getValue();
            if (value == JOptionPane.UNINITIALIZED_VALUE)
              return;

            // Reset the JOptionPane's value.
            // If I don't do this, then if the user
            // presses the same button next time, no
            // property change event will be fired.
            optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);

            if (value.equals(btnString1)) {
              query = textField.getText();
              caseSensitivity = group.getSelection().getActionCommand().equals("sensitive");
              if (query != null && !query.equals("")) {
                textField.selectAll();
                setVisible(false);
              }
              else {
                query = null;
                String name = (mode == 0) ? "concept name" : "word";
                JOptionPane.showMessageDialog(FindDialog.this,
                                              "Nothing entered.\nPlease enter a " + name + ".",
                                              "Try again",
                                              JOptionPane.ERROR_MESSAGE);
              }
            }
            else {
              query = null;
              setVisible(false);
            }
          }
        }
      });
    }
  }

}
