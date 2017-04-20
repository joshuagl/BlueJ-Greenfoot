/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2010,2011,2012,2013,2014,2015,2016,2017  Michael Kolling and John Rosenberg

 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 

 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 

 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 

 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.editor.moe;

import java.awt.Event;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.TextAction;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import bluej.Config;
import bluej.debugger.gentype.JavaType;
import bluej.editor.moe.MoeIndent.AutoIndentInformation;
import bluej.editor.moe.MoeSyntaxDocument.Element;
import bluej.parser.entity.JavaEntity;
import bluej.parser.nodes.CommentNode;
import bluej.parser.nodes.MethodNode;
import bluej.parser.nodes.NodeTree.NodeAndPosition;
import bluej.parser.nodes.ParsedNode;
import bluej.prefmgr.PrefMgr;
import bluej.prefmgr.PrefMgrDialog;
import bluej.utility.Debug;
import bluej.utility.DialogManager;

import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.stream.Collectors;

import bluej.utility.javafx.FXPlatformRunnable;
import bluej.utility.javafx.FXRunnable;
import javafx.application.Platform;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.input.*;
import javafx.scene.input.KeyCombination.Modifier;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.Nodes;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A set of actions supported by the Moe editor. This is a singleton: the
 * actions are shared between all editor instances.
 *
 * Actions are stored both in a hash-map and in an array. The hash-map is used
 * for fast lookup by name, whereas the array is needed to support complete,
 * ordered access.
 *
 * @author Michael Kolling
 * @author Bruce Quig
 */

public final class MoeActions
{
    // -------- CONSTANTS --------

    private static final String KEYS_FILE = "editor.keys";
    private static final int tabSize = Config.getPropInteger("bluej.editor.tabsize", 4);
    private static final String spaces = "                                        ";
    private static final char TAB_CHAR = '\t';
    private static Modifier SHORTCUT_MASK = KeyCombination.SHORTCUT_DOWN;
    private static int ALT_SHORTCUT_MASK;
    private static Modifier[] SHIFT_SHORTCUT_MASK = { KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN};
    private static int SHIFT_ALT_SHORTCUT_MASK;
    private static int DOUBLE_SHORTCUT_MASK; // two masks (ie. CTRL + META)

    // -------- INSTANCE VARIABLES --------
    private static final IdentityHashMap<MoeEditor, MoeActions> moeActions = new IdentityHashMap<>();
    private final MoeEditor editor;
    //MOEFX
    //public FindNextAction findNextAction;
    //public FindNextBackwardAction findNextBackwardAction;
    // frequently needed actions
    public MoeAbstractAction compileOrNextErrorAction;
    public Action contentAssistAction;
    private MoeAbstractAction[] actionTable; // table of all known actions
    private HashMap<Object, MoeAbstractAction> actions; // the same actions in a hash-map
    private String[] categories;
    private int[] categoryIndex;
    private final Map<KeyCombination, MoeAbstractAction> keymap = new HashMap<>();
    private org.fxmisc.wellbehaved.event.InputMap<javafx.scene.input.KeyEvent> curKeymap; // the editor's keymap
    //MOEFX private final KeyCatcher keyCatcher;
    private boolean lastActionWasCut; // true if last action was a cut action
    private MoeAbstractAction[] overrideActions;

    private MoeActions(MoeEditor editor)
    {
        this.editor = editor;
        // sort out modifier keys...

        /*MOEFX
        if (SHORTCUT_MASK == Event.CTRL_MASK)
            ALT_SHORTCUT_MASK = Event.META_MASK; // alternate (second) modifier
        else
            ALT_SHORTCUT_MASK = Event.CTRL_MASK;

        SHIFT_SHORTCUT_MASK = SHORTCUT_MASK + Event.SHIFT_MASK;
        SHIFT_ALT_SHORTCUT_MASK = Event.SHIFT_MASK + ALT_SHORTCUT_MASK;
        DOUBLE_SHORTCUT_MASK = SHORTCUT_MASK + ALT_SHORTCUT_MASK;
        */
        createActionTable(editor);
        //MOEFX
        //keyCatcher = new KeyCatcher();
        if (!load())
            setDefaultKeyBindings();
        lastActionWasCut = false;

        // install our own keymap, with the existing one as parent:
        updateKeymap();
    }

    private void updateKeymap()
    {
        if (curKeymap != null)
            Nodes.removeInputMap(getTextComponent(), curKeymap);
        curKeymap = org.fxmisc.wellbehaved.event.InputMap.sequence(keymap.entrySet().stream()
            .map(e -> org.fxmisc.wellbehaved.event.InputMap.consume(EventPattern.keyPressed(e.getKey()), ev -> e.getValue().actionPerformed())).collect(Collectors.toList()).toArray(new org.fxmisc.wellbehaved.event.InputMap[0]));
        Nodes.addInputMap(getTextComponent(), curKeymap);
    }

    /**
     * Get the actions object for the given editor.
     */
    public static MoeActions getActions(MoeEditor editor)
    {
        return moeActions.computeIfAbsent(editor, MoeActions::new);
    }

    private static int findWordLimit(MoeEditorPane c, int pos, boolean forwards)
    {
        int maxLen = c.getDocument().length();
        if (forwards && pos >= maxLen) return maxLen;
        if (! forwards && pos <= 0) return 0;
        char curChar = c.getText(pos, 1).charAt(0);
        if (Character.isWhitespace(curChar)) {
            while (Character.isWhitespace(curChar)) {
                if (forwards) pos++; else pos--;
                if (pos == maxLen) return pos;
                if (pos == 0) return 0;
                curChar = c.getText(pos, 1).charAt(0);
            }
            // If we are going back, we'll have gone one character too far
            // so adjust for that; but if going forwards, the limit is exclusive
            return forwards ? pos : pos + 1;
        }
        else if (Character.isJavaIdentifierPart(curChar)) {
            while (Character.isJavaIdentifierPart(curChar)) {
                if (forwards) pos++; else pos--;
                if (pos == maxLen) return pos;
                if (pos == 0) return 0;
                curChar = c.getText(pos, 1).charAt(0);
            }
            // If we are going back, we'll have gone one character too far
            // so adjust for that; but if going forwards, the limit is exclusive
            return forwards ? pos : pos + 1;
        }
        else {
            // Can't form an identifier, isn't a space, therefore
            // this char is a word by itself.  If we're looking for the start,
            // this is it, and the end is one character on
            return forwards ? pos + 1 : pos;
        }
    }

    /**
     * Check whether any text is currently selected.
     * @return True, if a selection is active.
     */
    private static boolean haveSelection(MoeEditor ed)
    {
        MoeEditorPane textPane = ed.getSourcePane();
        return textPane.getCaretMark() != textPane.getCaretDot();
    }

    // =========================== STATIC METHODS ===========================

    /**
     * Return the current column number.
     */
    private static int getCurrentColumn(MoeEditorPane textPane)
    {
        int pos = Math.min(textPane.getCaretMark(), textPane.getCaretDot());
        AbstractDocument doc = (AbstractDocument) textPane.getDocument();
        int lineStart = doc.getParagraphElement(pos).getStartOffset();
        return (pos - lineStart);
    }

    /**
     * Find and return a line by line number
     */
    private static Element getLine(MoeEditorPane text, int lineNo)
    {
        //MOEFX
        return null;
        //return text.getSourceDocument().getDefaultRootElement().getElement(lineNo);
    }

    /**
     * Return the number of the current line.
     */
    private static int getCurrentLineIndex(MoeEditorPane text)
    {
        MoeSyntaxDocument document = (MoeSyntaxDocument) text.getDocument();
        return document.getDefaultRootElement().getElementIndex(text.getCaretPosition());
    }

    // ========================== INSTANCE METHODS ==========================

    /**
     * Check whether the indentation s opens a new multi-line comment
     * @param lineStart The position in the document of the (newly-added) line start
     */
    private static boolean isNewCommentStart(String s, MoeSyntaxDocument doc, int lineStart)
    {
        s = s.trim();
        if (s.endsWith("/**") || s.endsWith("/*"))
        {
            // The user has just pressed enter after the beginning of a comment
            // We must now decide if their comment was already fine
            // (and thus we shouldn't add the ending), or if they had, in fact,
            // begun a new comment (and do need the ending)

            // Find the comment node that corresponds to our position:
            NodeAndPosition<ParsedNode> curNode = doc.getParser().findNodeAt(lineStart, 0);
            while (curNode != null && !(curNode.getNode() instanceof CommentNode))
            {
                curNode = curNode.getNode().findNodeAt(lineStart, curNode.getPosition());
            }

            if (curNode == null) {
                //Can't work it out; it's probably a new comment that is unterminated:
                return true;
            }

            String comment = getNodeContents(doc, curNode);

            // If the comment has a comment begin inside it (after the first two characters)
            // it is likely a new comment that has over-run and matched an ending further
            // down.  If it has no comment begin inside it, it's probably a pre-existing
            // valid comment.
            comment = comment.substring(2);
            // if comment has beginning return true
            return comment.contains("/*");
        }
        return false;
    }

    /**
     * Insert text to complete a new, started block comment and place the cursor
     * appropriately.
     *
     * The indentString passed in always ends with "/*".
     */
    private static void completeNewCommentBlock(MoeEditorPane textPane, String indentString)
    {
        String nextIndent = indentString.substring(0, indentString.length() - 2);
        textPane.replaceSelection(nextIndent + " * ");
        int pos = textPane.getCaretPosition();
        textPane.replaceSelection("\n");
        textPane.replaceSelection(nextIndent + " */");
        textPane.setCaretPosition(pos);
    }

    /**
     * Check whether the given line ends with an opening brace.
     */
    private static boolean isOpenBrace(String s)
    {
        int index = s.lastIndexOf('{');
        if (index == -1) {
            return false;
        }

        return s.indexOf('}', index + 1) == -1;
    }

    /**
     * Transform indentation string to ensure:
     * <ul>
     * <li>after " / *" follows " *"
     * <li>after " / * *" follows " *"
     * <li>after " * /" follows ""
     * </ul>
     */
    private static String nextIndent(String s, boolean openBrace, boolean commentEndOnly)
    {
        // after an opening brace, add some spaces to the indentation
        if (openBrace) {
            return s + spaces.substring(0, tabSize);
        }

        if (commentEndOnly) {
            return s.substring(0, s.length() - 1);
        }

        if (s.endsWith("/*")) {
            return s.substring(0, s.length() - 2) + " * ";
        }

        return s;
    }

    /**
     * Insert a spaced tab at the current caret position in to the textPane.
     */
    private static void insertSpacedTab(MoeEditorPane textPane)
    {
        int numSpaces = tabSize - (getCurrentColumn(textPane) % tabSize);
        textPane.replaceSelection(spaces.substring(0, numSpaces));
    }

    /**
     * Remove characters before the current caret position to take the
     * caret back to the previous TAB position. No check is made what kind
     * of characters those are - the caller should make sure they can be
     * removed (usually they should be whitespace).
     */
    private static void removeTab(MoeEditorPane textPane, MoeSyntaxDocument doc) throws BadLocationException
    {
        int col = getCurrentColumn(textPane);
        if(col > 0) {
            int remove = col % tabSize;
            if(remove == 0) {
                remove = tabSize;
            }
            int pos = textPane.getCaretPosition();
            doc.remove(pos-remove, remove);
        }
    }

    private static String expandTab(String s, int idx)
    {
        int numSpaces = tabSize - (idx % tabSize);
        return s.substring(0, idx) + spaces.substring(0, numSpaces) + s.substring(idx + 1);
    }

    /**
     * Insert text from a named template into the editor at the current cursor
     * position. Every line in the template will be indented to the current
     * cursor position (in addition to possible indentation in the template
     * itself), and TAB characters at beginnings of lines in the template will
     * be converted to a spaced tab according to the current tabsize.
     *
     * @param textPane
     *            The editor pane to enter the text into
     * @param editor
     * @param templateName
     *            The name of the template (without path or suffix)
     */
    private void insertTemplate(String templateName)
    {
        try {
            MoeEditorPane textPane = getTextComponent();
            File template = Config.getTemplateFile(templateName);

            InputStream fileStream = new FileInputStream(template);
            BufferedReader in = new BufferedReader(new InputStreamReader(fileStream, "UTF-8"));

            int addedTextLength = 0;
            String line = in.readLine();
            while (line != null) {
                while ((line.length() > 0) && (line.charAt(0) == '\t')) {
                    line = line.substring(1);
                }
                addedTextLength += line.length() + 1;
                textPane.replaceSelection(line);
                textPane.replaceSelection("\n");
                line = in.readLine();
            }
            // The position of the caret should be in the right place now.
            // Previously it was set to the position it was at before adding the
            // template, but that resulted in errors when selecting the entire
            // contents of the class before inserting the template.
            int caretPos = editor.getSourcePane().getCaretPosition();
            AutoIndentInformation info = MoeIndent.calculateIndentsAndApply(editor.getSourceDocument(),caretPos - addedTextLength,caretPos+2,caretPos);
            editor.setCaretPositionForward(info.getNewCaretPosition() - editor.getSourcePane().getCaretPosition());

            in.close();
        }
        catch (IOException exc) {
            Debug.reportError("Could not read method template.");
            Debug.reportError("Exception: " + exc);
        }
    }

    /**
     * Perform an action on all selected lines in the source document.
     */
    private static void blockAction(MoeEditor editor, LineAction lineAction)
    {
        editor.setCaretActive(false);

        int selectionStart = editor.getSourcePane().getCaretMark();
        int selectionEnd = editor.getSourcePane().getCaretDot();
        if (selectionStart > selectionEnd) {
            int tmp = selectionStart;
            selectionStart = selectionEnd;
            selectionEnd = tmp;
        }
        if (selectionStart != selectionEnd)
            selectionEnd = selectionEnd - 1; // skip last position

        MoeSyntaxDocument doc = editor.getSourceDocument();
        Element text = doc.getDefaultRootElement();

        int firstLineIndex = text.getElementIndex(selectionStart);
        int lastLineIndex = text.getElementIndex(selectionEnd);
        for (int i = firstLineIndex; i <= lastLineIndex; i++) {
            Element line = text.getElement(i);
            lineAction.apply(line, doc);
        }

        editor.setSelection(firstLineIndex + 1, 1,
                text.getElement(lastLineIndex).getEndOffset()
                - text.getElement(firstLineIndex).getStartOffset());

        editor.setCaretActive(true);
    }

    private static String getNodeContents(MoeSyntaxDocument doc, NodeAndPosition<ParsedNode> nap)
    {
        return doc.getText(nap.getPosition(), nap.getSize());
    }

    public MoeAbstractAction[] getActionTable()
    {
        return actionTable;
    }

    public String[] getCategories()
    {
        return categories;
    }

    public void setCategories(String[] categories)
    {
        this.categories = categories;
    }

    public int[] getCategoryIndex()
    {
        return categoryIndex;
    }

    public void setCategoryIndex(int[] categoryIndex)
    {
        this.categoryIndex = categoryIndex;
    }

    // ============================ USER ACTIONS =============================

    public void setPasteEnabled(boolean enabled)
    {
        actions.get(DefaultEditorKit.pasteAction).setEnabled(enabled);
    }

    // === File: ===
    // --------------------------------------------------------------------


    // --------------------------------------------------------------------
    //MOEFX
/*
    public FindNextAction getFindNextAction()
    {
        return findNextAction;
    }

    // --------------------------------------------------------------------

    public FindNextBackwardAction getFindNextBackwardAction()
    {
        return findNextBackwardAction;
    }
*/
    // --------------------------------------------------------------------

    /**
     * Allow the enabling/disabling of an action.
     * @param action  String representing name of action
     * @param flag  true to enable action from menu.
     */

    public void enableAction(String action, boolean flag)
    {
        MoeAbstractAction moeAction = getActionByName(action);
        if (moeAction != null) {
            moeAction.setEnabled(flag);
        }
    }

    // --------------------------------------------------------------------

    /**
     * Return an action with a given name.
     */
    public MoeAbstractAction getActionByName(String name)
    {
        return actions.get(name);
    }

    // --------------------------------------------------------------------

    /**
     * BUG WORKAROUND: currently, keymap.getKeyStrokesForAction() misses
     * keystrokes that come from JComponents inputMap. Here, we add those
     * ourselves...
     */
    /*MOEFX: is this still needed?
    public KeyStroke[] addComponentKeyStrokes(Action action, KeyStroke[] keys)
    {
        ArrayList<KeyStroke> keyStrokes = null;
        KeyStroke[] componentKeys = componentInputMap.allKeys();

        // find all component keys that bind to this action
        for (KeyStroke componentKey : componentKeys) {
            if (componentInputMap.get(componentKey).equals(action.getValue(Action.NAME))) {
                if (keyStrokes == null)
                    keyStrokes = new ArrayList<>();
                keyStrokes.add(componentKey);
            }
        }

        // test whether this keyStroke was redefined in keymap
        if (keyStrokes != null) {
            for (Iterator<KeyStroke> i = keyStrokes.iterator(); i.hasNext();) {
                if (keymap.getAction(i.next()) != null) {
                    i.remove();
                }
            }
        }

        // merge found keystrokes into key array
        if ((keyStrokes == null) || (keyStrokes.isEmpty())) {
            return keys;
        }

        KeyStroke[] allKeys;
        if (keys == null) {
            allKeys = new KeyStroke[keyStrokes.size()];
            keyStrokes.toArray(allKeys);
        }
        else { // merge new keystrokes into keys
            allKeys = new KeyStroke[keyStrokes.size() + keys.length];
            keyStrokes.toArray(allKeys);
            System.arraycopy(allKeys, 0, allKeys, keys.length, keyStrokes.size());
            System.arraycopy(keys, 0, allKeys, 0, keys.length);
        }
        return allKeys;
    }*/

    // --------------------------------------------------------------------

    /**
     * Add a new key binding into the action table.
     */
    public void addActionForKeyStroke(KeyCombination key, MoeAbstractAction a)
    {
        keymap.put(key, a);
        updateKeymap();
    }

    // --------------------------------------------------------------------

    /**
     * Remove a key binding from the action table.
     */
    public void removeKeyStrokeBinding(KeyCombination key)
    {
        keymap.remove(key);
    }

    // --------------------------------------------------------------------

    /**
     * Save the key bindings. Return true if successful.
     */
    public boolean save()
    {
        /*MOEFX
        try {
            File file = Config.getUserConfigFile(KEYS_FILE);
            FileOutputStream ostream = new FileOutputStream(file);
            ObjectOutputStream stream = new ObjectOutputStream(ostream);
            KeyStroke[] keys = keymap.getBoundKeyStrokes();
            stream.writeInt(MoeEditor.version);
            stream.writeInt(keys.length);
            for (KeyStroke key : keys) {
                stream.writeObject(key);
                stream.writeObject(keymap.getAction(key).getValue(Action.NAME));
            }
            stream.flush();
            ostream.close();
            return true;
        }
        catch (Exception exc) {
            Debug.message("Cannot save key bindings: " + exc);
            return false;
        }
        */
        return false;
    }

    // --------------------------------------------------------------------

    /**
     * Load the key bindings. Return true if successful.
     */
    public boolean load()
    {
        /*MOEFX
        try {
            File file = Config.getUserConfigFile(KEYS_FILE);
            FileInputStream istream = new FileInputStream(file);
            ObjectInputStream stream = new ObjectInputStream(istream);
            //KeyStroke[] keys = keymap.getBoundKeyStrokes();
            int version = 0;
            int count = stream.readInt();
            if (count > 100) { // it was new format: version number stored first
                version = count;
                count = stream.readInt();
            }
            if (Config.isMacOS() && (version < 140)) {
                // do not attempt to load old bindings on MacOS when switching
                // to jdk 1.4.1
                istream.close();
                return false;
            }

            for (int i = 0; i < count; i++) {
                KeyStroke key = (KeyStroke) stream.readObject();
                String actionName = (String) stream.readObject();
                MoeAbstractAction action = actions.get(actionName);
                if (action != null) {
                    keymap.addActionForKeyStroke(key, action);
                }
            }
            istream.close();

            // set up bindings for new actions in recent releases

            if (version < 252) {
                keymap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SHORTCUT_MASK), actions.get("increase-font"));
                keymap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, SHORTCUT_MASK), actions.get("decrease-font"));
            }
            if (version < 300) {
                keymap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, Event.CTRL_MASK), actions.get("code-completion"));
                keymap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_I, SHIFT_SHORTCUT_MASK ), actions.get("autoindent"));
            }
            if (version < 320) {
                keymap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_K, SHORTCUT_MASK ), actions.get("compile"));
            }
            if (version < 330) {
                keymap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, SHORTCUT_MASK), actions.get("preferences"));
            }
            return true;
        }
        catch (IOException | ClassNotFoundException exc) {
            // ignore - file probably didn't exist (yet)
            return false;
        }
        */
        return false;
    }

    // --------------------------------------------------------------------

    /**
     * Called to inform that any one of the user actions (text edit or caret
     * move) was executed.
     */
    public void userAction()
    {
        lastActionWasCut = false;
    }

    // --------------------------------------------------------------------

    /**
     * Called at every insertion of text into the document.
     */
    public void textInsertAction(DocumentEvent evt, JTextComponent textPane)
    {
        //MOEFX
        /*
        try {
            if (evt.getLength() == 1) { // single character inserted
                Document doc = evt.getDocument();
                int offset = evt.getOffset();
                char ch = doc.getText(offset, 1).charAt(0);

                // 'ch' is the character that was just typed
                // currently, the only character upon which we act is the
                // closing brace ('}')

                if (ch == '}' && PrefMgr.getFlag(PrefMgr.AUTO_INDENT)) {
                    closingBrace(textPane, doc, offset);
                }
            }
        }
        catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
        */
    }

    // --------------------------------------------------------------------

    /**
     * We just typed a closing brace character - indent appropriately.
     */
    private void closingBrace(MoeEditorPane textPane, Document doc, int offset) throws BadLocationException
    {
        int lineIndex = getCurrentLineIndex(textPane);
        Element line = getLine(textPane, lineIndex);
        int lineStart = line.getStartOffset();
        String prefix = doc.getText(lineStart, offset - lineStart);

        if(prefix.trim().length() == 0) {  // only if there is no other text before '}'
            // Determine where the cursor appears horizontally (before insertion)
            //MOEFX
            Rectangle r = null;//textPane.modelToView(textPane.getCaretPosition() - 1);
            Point p = r.getLocation();

            // Indent the line
            textPane.setCaretPosition(lineStart);
            doIndent(textPane, true);
            textPane.setCaretPosition(textPane.getCaretPosition() + 1);

            // Set the magic position to the original position. This means that
            // cursor up will go to the beginning of the previous line, which is much
            // nicer behaviour.
            //MOEFX
            //textPane.getCaret().setMagicCaretPosition(p);
        }
    }

    // --------------------------------------------------------------------

    /**
     * Add the current selection of the text component to the clipboard.
     */
    public static void addSelectionToClipboard(MoeEditor ed)
    {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();

        // get text from clipboard
        String clipContent = clipboard.getString();
        if (clipContent == null)
            clipContent = "";
        // add current selection and store back in clipboard
        clipboard.setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, clipContent + ed.getSourcePane().getSelectedText()));
    }

    // --------------------------------------------------------------------

    /**
     * Do some semi-intelligent indentation. That is: indent the current line to
     * the same depth, using the same characters (TABs or spaces) as the line
     * immediately above.
     *
     * @param isNewLine   true if the action was to insert a line or closing brace;
     *                     false if the action was to tab/indent
     */
    private void doIndent(MoeEditorPane textPane, boolean isNewLine)
    {
        int lineIndex = getCurrentLineIndex(textPane);
        if (lineIndex == 0) { // first line
            if(!isNewLine) {
                insertSpacedTab(textPane);
            }
            return;
        }

        MoeSyntaxDocument doc = (MoeSyntaxDocument) textPane.getDocument();

        Element line = getLine(textPane, lineIndex);
        int lineStart = line.getStartOffset();
        int pos = textPane.getCaretPosition();

        try {
            boolean isOpenBrace = false;
            boolean isCommentEnd = false, isCommentEndOnly = false;

            // if there is any text before the cursor, just insert a tab

            String prefix = doc.getText(lineStart, pos - lineStart);
            if (prefix.trim().length() > 0) {
                insertSpacedTab(textPane);
                return;
            }

            // get indentation string from previous line

            boolean foundLine = false;
            int lineOffset = 1;
            String prevLineText = "";
            while ((lineIndex - lineOffset >= 0) && !foundLine) {
                Element prevline = getLine(textPane, lineIndex - lineOffset);
                int prevLineStart = prevline.getStartOffset();
                int prevLineEnd = prevline.getEndOffset();
                prevLineText = doc.getText(prevLineStart, prevLineEnd - prevLineStart);
                if(!MoeIndent.isWhiteSpaceOnly(prevLineText)) {
                    foundLine = true;
                }
                else {
                    lineOffset++;
                }
            }
            if(!foundLine) {
                if(!isNewLine)
                    insertSpacedTab(textPane);
                return;
            }

            if (isOpenBrace(prevLineText)) {
                isOpenBrace = true;
            }
            else {
                isCommentEnd = prevLineText.trim().endsWith("*/");
                isCommentEndOnly = prevLineText.trim().equals("*/");
            }

            int indentPos = MoeIndent.findFirstNonIndentChar(prevLineText, isCommentEnd);
            String indent = prevLineText.substring(0, indentPos);

            if (isOpenBrace) {
                indentPos += tabSize;
            }

            // if the cursor is already past the indentation point, insert tab
            // (unless we just did a line break, then we just stop)

            int caretColumn = getCurrentColumn(textPane);
            if (caretColumn >= indentPos) {
                if (!isNewLine) {
                    insertSpacedTab(textPane);
                }
                return;
            }

            if (isNewLine && isNewCommentStart(indent, doc, lineStart)) {
                completeNewCommentBlock(textPane, indent);
                return;
            }

            // find and replace indentation of current line

            int lineEnd = line.getEndOffset();
            String lineText = doc.getText(lineStart, lineEnd - lineStart);
            indentPos = MoeIndent.findFirstNonIndentChar(lineText, true);
            char firstChar = lineText.charAt(indentPos);
            doc.remove(lineStart, indentPos);
            String newIndent = nextIndent(indent, isOpenBrace, isCommentEndOnly);
            if (firstChar == '*') {
                newIndent = newIndent.replace('*', ' ');
            }
            doc.insertString(lineStart, newIndent, null);
            if(firstChar == '}') {
                removeTab(textPane, doc);
            }
        }
        catch (BadLocationException exc) {
            throw new RuntimeException(exc);
        }
    }

    // --------------------------------------------------------------------

    /**
     * Do some semi-intelligent de-indentation. That is: indent the current line
     * one indentation level less that the line above, or less than it currently
     * is.
     */
    private void doDeIndent(MoeEditorPane textPane)
    {
        // set cursor to first non-blank character (or eol if none)
        // if indentation is more than line above: indent as line above
        // if indentation is same or less than line above: indent one level back

        int lineIndex = getCurrentLineIndex(textPane);
        MoeSyntaxDocument doc = (MoeSyntaxDocument) textPane.getDocument();

        try {
            Element line = getLine(textPane, lineIndex);
            int lineStart = line.getStartOffset();
            int lineEnd = line.getEndOffset();
            String lineText = doc.getText(lineStart, lineEnd - lineStart);

            int currentIndentPos = MoeIndent.findFirstNonIndentChar(lineText, true);
            char firstChar = lineText.charAt(currentIndentPos);

            textPane.setCaretPosition(lineStart + currentIndentPos);

            if (lineIndex == 0) { // first line
                removeTab(textPane, doc);
                return;
            }

            // get indentation details from previous line

            Element prevline = getLine(textPane, lineIndex - 1);
            int prevLineStart = prevline.getStartOffset();
            int prevLineEnd = prevline.getEndOffset();
            String prevLineText = doc.getText(prevLineStart, prevLineEnd - prevLineStart);

            int targetIndentPos = MoeIndent.findFirstNonIndentChar(prevLineText, true);

            if (currentIndentPos > targetIndentPos) {
                // indent same as line above
                String indent = prevLineText.substring(0, targetIndentPos);
                doc.remove(lineStart, currentIndentPos);
                doc.insertString(lineStart, indent, null);
                if(firstChar == '}')
                    removeTab(textPane, doc);
            }
            else {
                // we are at same level as line above or less - go one indentation
                // level back
                removeTab(textPane, doc);
            }
        }
        catch (BadLocationException exc) {
            throw new RuntimeException(exc);
        }
    }

    // --------------------------------------------------------------------

    /**
     * Indent a block of lines (defined by the current selection) by one
     * additional level.
     */
    private void doBlockIndent(MoeEditor editor)
    {
        editor.undoManager.beginCompoundEdit();
        blockAction(editor, new IndentLineAction());
        editor.undoManager.endCompoundEdit();
    }

    // --------------------------------------------------------------------

    /**
     * De-indent a block of lines (defined by the current selection) by one
     * level.
     */
    private void doBlockDeIndent(MoeEditor editor)
    {
        editor.undoManager.beginCompoundEdit();
        blockAction(editor, new DeindentLineAction());
        editor.undoManager.endCompoundEdit();
    }

    // --------------------------------------------------------------------

    /**
     * Convert all tabs in this text to spaces, maintaining the current
     * indentation.
     *
     * @param textPane The text pane to convert
     * @return  The number of tab characters converted
     */
    private static int convertTabsToSpaces(MoeEditor editor)
    {
        /*MOEFX
        int count = 0;
        int lineNo = 0;
        AbstractDocument doc = (AbstractDocument) textPane.getDocument();
        //MOEFX
        Element root = null;//doc.getDefaultRootElement();
        Element line = root.getElement(lineNo);
        try {
            while (line != null) {
                int start = line.getStartOffset();
                int length = line.getEndOffset() - start;
                String text = doc.getText(start, length);
                int startCount = count;
                int tabIndex = text.indexOf('\t');
                while (tabIndex != -1) {
                    text = expandTab(text, tabIndex);
                    count++;
                    tabIndex = text.indexOf('\t');
                }
                if (count != startCount) { // there was a TAB in this line...
                    doc.remove(start, length);
                    doc.insertString(start, text, null);
                }
                lineNo++;
                line = root.getElement(lineNo);
            }
        }
        catch (BadLocationException exc) {
            throw new RuntimeException(exc);
        }
        return count;
        */
        return 0;
    }

    // --------------------------------------------------------------------

    /**
     * Create the table of action supported by this editor
     */
    private void createActionTable(MoeEditor editor)
    {
        compileOrNextErrorAction = compileOrNextErrorAction();

        // get all actions into arrays

        overrideActions = new MoeAbstractAction[]{
                //With and without selection for each:
                new NextWordAction(false),
                new NextWordAction(true),
                new PrevWordAction(false),
                new PrevWordAction(true),

                //With and without selection for each:
                new EndWordAction(false),
                new EndWordAction(true),
                new BeginWordAction(false),
                new BeginWordAction(true),

                deleteWordAction(),

                selectWordAction()
        };

        MoeAbstractAction[] myActions = {
                saveAction(),
                reloadAction(),
                pageSetupAction(),
                printAction(),
                closeAction(),

                undoAction(),
                redoAction(),
                commentBlockAction(),
                uncommentBlockAction(),
                autoIndentAction(),
                indentBlockAction(),
                deindentBlockAction(),
                insertMethodAction(),
                addJavadocAction(),
                indentAction(),
                deIndentAction(),
                newLineAction(),
                copyLineAction(),
                cutLineAction(),
                cutEndOfLineAction(),
                cutWordAction(),
                cutEndOfWordAction(),

                //MOEFX
                //new FindAction(editor),
                //findNextAction=new FindNextAction(editor),
                //findNextBackwardAction=new FindNextBackwardAction(editor),
                replaceAction(),
                compileOrNextErrorAction,
                goToLineAction(),
                toggleInterfaceAction(),
                toggleBreakPointAction(),

                keyBindingsAction(),
                preferencesAction(),

                describeKeyAction(),

                increaseFontAction(),
                decreaseFontAction(),

                contentAssistAction()
        };

        // insert all actions into a hash map

        actions = new HashMap<>();

        for (MoeAbstractAction action : overrideActions)
        {
            actions.put(action.getName(), action);
        }

        for (MoeAbstractAction action : myActions)
        {
            actions.put(action.getName(), action);
        }

        // sort all actions into a big, ordered table

        actionTable = new MoeAbstractAction[]{

                actions.get(DefaultEditorKit.deletePrevCharAction), // 0
                actions.get(DefaultEditorKit.deleteNextCharAction),
                actions.get("delete-previous-word"),
                actions.get(DefaultEditorKit.copyAction),
                actions.get(DefaultEditorKit.cutAction),
                actions.get("copy-line"),
                actions.get("cut-line"),
                actions.get("cut-end-of-line"),
                actions.get("cut-word"),
                actions.get("cut-end-of-word"),
                actions.get(DefaultEditorKit.pasteAction),
                actions.get("indent"),
                actions.get("de-indent"),
                actions.get(DefaultEditorKit.insertTabAction),
                actions.get("new-line"),
                actions.get(DefaultEditorKit.insertBreakAction),
                actions.get("insert-method"),
                actions.get("add-javadoc"),
                actions.get("comment-block"),
                actions.get("uncomment-block"),
                actions.get("autoindent"),
                actions.get("indent-block"),
                actions.get("deindent-block"),

                actions.get(DefaultEditorKit.selectWordAction), // 23
                actions.get(DefaultEditorKit.selectLineAction),
                actions.get(DefaultEditorKit.selectParagraphAction),
                actions.get(DefaultEditorKit.selectAllAction),
                actions.get(DefaultEditorKit.selectionBackwardAction),
                actions.get(DefaultEditorKit.selectionForwardAction),
                actions.get(DefaultEditorKit.selectionUpAction),
                actions.get(DefaultEditorKit.selectionDownAction),
                actions.get(DefaultEditorKit.selectionBeginWordAction),
                actions.get(DefaultEditorKit.selectionEndWordAction),
                actions.get(DefaultEditorKit.selectionPreviousWordAction), // 33
                actions.get(DefaultEditorKit.selectionNextWordAction),
                actions.get(DefaultEditorKit.selectionBeginLineAction),
                actions.get(DefaultEditorKit.selectionEndLineAction),
                actions.get(DefaultEditorKit.selectionBeginParagraphAction),
                actions.get(DefaultEditorKit.selectionEndParagraphAction),
                actions.get("selection-page-up"),
                actions.get("selection-page-down"),
                actions.get(DefaultEditorKit.selectionBeginAction),
                actions.get(DefaultEditorKit.selectionEndAction),
                actions.get("unselect"),

                actions.get(DefaultEditorKit.backwardAction), // 44
                actions.get(DefaultEditorKit.forwardAction),
                actions.get(DefaultEditorKit.upAction),
                actions.get(DefaultEditorKit.downAction),
                actions.get(DefaultEditorKit.beginWordAction),
                actions.get(DefaultEditorKit.endWordAction),
                actions.get(DefaultEditorKit.previousWordAction),
                actions.get(DefaultEditorKit.nextWordAction),
                actions.get(DefaultEditorKit.beginLineAction),
                actions.get(DefaultEditorKit.endLineAction),    // 53
                actions.get(DefaultEditorKit.beginParagraphAction),
                actions.get(DefaultEditorKit.endParagraphAction),
                actions.get(DefaultEditorKit.pageUpAction),
                actions.get(DefaultEditorKit.pageDownAction),
                actions.get(DefaultEditorKit.beginAction),
                actions.get(DefaultEditorKit.endAction),

                actions.get("save"), // 60
                actions.get("reload"),
                actions.get("close"),
                actions.get("print"),
                actions.get("page-setup"),

                actions.get("key-bindings"), // 65
                actions.get("preferences"),

                actions.get("describe-key"), // 67
                doNothingAction(), //actions.get("help-mouse"), MOEFX
                doNothingAction(), //actions.get("about-editor"), MOEFX

                // misc functions
                actions.get("undo"), // 70
                actions.get("redo"),
                actions.get("find"),
                actions.get("find-next"),
                actions.get("find-next-backward"),
                actions.get("replace"),
                actions.get("compile"),
                actions.get("toggle-interface-view"),
                actions.get("toggle-breakpoint"),
                actions.get("go-to-line"),
                actions.get("increase-font"),
                actions.get("decrease-font"),
                actions.get("code-completion"),

        }; // 83

        categories = new String[]{
                Config.getString("editor.functions.editFunctions"),
                Config.getString("editor.functions.moveScroll"),
                Config.getString("editor.functions.classFunctions"),
                Config.getString("editor.functions.customisation"),
                Config.getString("editor.functions.help"),
                Config.getString("editor.functions.misc")
        };

        categoryIndex = new int[]{0, 44, 60, 65, 67, 70, 83};
    }

    // --------------------------------------------------------------------

    /**
     * Set up the default key bindings. Used for initial setup, or restoring the
     * default later on.
     */
    public void setDefaultKeyBindings()
    {
        keymap.clear();

        // Previously in the Swing editor, no distinction was made between accelerators
        // and custom key bindings.  Now in the FX editor, we do distinguish.  The rule is:
        // if an item has a menu item and a command key which can act as an accelerator then
        // it is set as an accelerator.  If not, it is set as a custom key binding.


        setAccelerator(new KeyCodeCombination(KeyCode.S, SHORTCUT_MASK), actions.get("save"));
        // "reload" not bound
        setAccelerator(new KeyCodeCombination(KeyCode.P, SHORTCUT_MASK), actions.get("print"));
        // "page-setup" not bound
        setAccelerator(new KeyCodeCombination(KeyCode.W, SHORTCUT_MASK), actions.get("close"));
        setAccelerator(new KeyCodeCombination(KeyCode.Z, SHORTCUT_MASK), actions.get("undo"));
        setAccelerator(new KeyCodeCombination(KeyCode.Y, SHORTCUT_MASK), actions.get("redo"));
        setAccelerator(new KeyCodeCombination(KeyCode.F8), actions.get("comment-block"));
        setAccelerator(new KeyCodeCombination(KeyCode.F7), actions.get("uncomment-block"));
        setAccelerator(new KeyCodeCombination(KeyCode.F6), actions.get("indent-block"));
        setAccelerator(new KeyCodeCombination(KeyCode.F5), actions.get("deindent-block"));
        setAccelerator(new KeyCodeCombination(KeyCode.M, SHORTCUT_MASK), actions.get("insert-method"));
        keymap.put(new KeyCodeCombination(KeyCode.TAB), actions.get("indent"));
        keymap.put(new KeyCodeCombination(KeyCode.TAB, KeyCombination.SHIFT_DOWN), actions.get("de-indent"));
        keymap.put(new KeyCodeCombination(KeyCode.I, SHORTCUT_MASK), actions.get("insert-tab"));
        keymap.put(new KeyCodeCombination(KeyCode.ENTER), actions.get("new-line"));
        keymap.put(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHIFT_DOWN), actions.get("insert-break"));
        setAccelerator(new KeyCodeCombination(KeyCode.F, SHORTCUT_MASK), actions.get("find"));
        setAccelerator(new KeyCodeCombination(KeyCode.G, SHORTCUT_MASK), actions.get("find-next"));
        setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.SHIFT_DOWN), actions.get("find-next-backward"));
        setAccelerator(new KeyCodeCombination(KeyCode.R, SHORTCUT_MASK), actions.get("replace"));
        setAccelerator(new KeyCodeCombination(KeyCode.L, SHORTCUT_MASK), actions.get("go-to-line"));
        setAccelerator(new KeyCodeCombination(KeyCode.K, SHORTCUT_MASK), actions.get("compile"));
        setAccelerator(new KeyCodeCombination(KeyCode.J, SHORTCUT_MASK), actions.get("toggle-interface-view"));
        setAccelerator(new KeyCodeCombination(KeyCode.B, SHORTCUT_MASK), actions.get("toggle-breakpoint"));
        // "key-bindings" not bound
        setAccelerator(new KeyCodeCombination(KeyCode.COMMA, SHORTCUT_MASK), actions.get("preferences"));
        // "about-editor" not bound
        setAccelerator(new KeyCodeCombination(KeyCode.D, SHORTCUT_MASK), actions.get("describe-key"));
        // "help-mouse" not bound

        setAccelerator(new KeyCodeCombination(KeyCode.C, SHORTCUT_MASK), actions.get(DefaultEditorKit.copyAction));
        setAccelerator(new KeyCodeCombination(KeyCode.X, SHORTCUT_MASK), actions.get(DefaultEditorKit.cutAction));
        setAccelerator(new KeyCodeCombination(KeyCode.V, SHORTCUT_MASK), actions.get(DefaultEditorKit.pasteAction));

        // F2, F3, F4
        setAccelerator(new KeyCodeCombination(KeyCode.F2), actions.get("copy-line"));
        setAccelerator(new KeyCodeCombination(KeyCode.F3), actions.get(DefaultEditorKit.pasteAction));
        setAccelerator(new KeyCodeCombination(KeyCode.F4), actions.get("cut-line"));

        // cursor block
        /*MOEFX
        keymap.put(new KeyCodeCombination(KeyCode.UP, ALT_SHORTCUT_MASK), actions.get(DefaultEditorKit.pasteAction));
        keymap.put(new KeyCodeCombination(KeyCode.LEFT, ALT_SHORTCUT_MASK), actions.get(DefaultEditorKit.deletePrevCharAction));
        keymap.put(new KeyCodeCombination(KeyCode.RIGHT, ALT_SHORTCUT_MASK), actions.get(DefaultEditorKit.deleteNextCharAction));
        keymap.put(new KeyCodeCombination(KeyCode.LEFT, SHIFT_ALT_SHORTCUT_MASK), actions.get("cut-line"));
        keymap.put(new KeyCodeCombination(KeyCode.RIGHT, SHIFT_ALT_SHORTCUT_MASK), actions.get("cut-end-of-line"));
        keymap.put(new KeyCodeCombination(KeyCode.LEFT, DOUBLE_SHORTCUT_MASK), actions.get("cut-word"));
        keymap.put(new KeyCodeCombination(KeyCode.RIGHT, DOUBLE_SHORTCUT_MASK), actions.get("cut-end-of-word"));
        */
        setAccelerator(new KeyCodeCombination(KeyCode.EQUALS, SHORTCUT_MASK), actions.get("increase-font"));
        setAccelerator(new KeyCodeCombination(KeyCode.MINUS, SHORTCUT_MASK), actions.get("decrease-font"));
        keymap.put(new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN), actions.get("code-completion"));
        setAccelerator(new KeyCodeCombination(KeyCode.I, SHIFT_SHORTCUT_MASK), actions.get("autoindent"));
    }

    private void setAccelerator(KeyCombination accelerator, MoeAbstractAction action)
    {
        if (action == null)
            Debug.printCallStack("Setting accelerator for unfound action");
        else
            action.setAccelerator(accelerator);
    }

    private MoeAbstractAction action(String name, FXRunnable action)
    {
        return new MoeAbstractAction(name)
        {
            @Override
            public @OnThread(value = Tag.FX, ignoreParent = true) void actionPerformed()
            {
                action.run();
            }
        };
    }

    // --------------------------------------------------------------------

    /**
     * Interface LineAction - a superclass for all line actions. Line actions
     * manipulate a single line of text and are used by the blockAction method.
     * The blockAction applies a LineAction to each line in a block of text.
     */
    interface LineAction
    {
        /**
         * Apply some action to a line in the document.
         */
        public void apply(Element line, MoeSyntaxDocument doc);
    }

    // --------------------------------------------------------------------

    @OnThread(Tag.FX)
    abstract class MoeAbstractAction
    {
        private final String name;
        private final BooleanProperty disabled = new SimpleBooleanProperty(false);
        private final ObjectProperty<KeyCombination> accelerator = new SimpleObjectProperty<>(null);

        public MoeAbstractAction(String name)
        {
            this.name = name;
        }

        public abstract void actionPerformed();

        public MoeAbstractAction bindEnabled(BooleanExpression enabled)
        {
            disabled.bind(enabled.not());
            return this;
        }

        public void setEnabled(boolean enabled)
        {
            if (disabled.isBound())
                disabled.unbind();
            disabled.set(!enabled);
        }

        public void setAccelerator(KeyCombination accelerator)
        {
            this.accelerator.set(accelerator);
        }

        public String getName()
        {
            return name;
        }

        public Button makeButton()
        {
            Button button = new Button(name);
            button.disableProperty().bind(disabled);
            button.setOnAction(e -> actionPerformed());
            return button;
        }

        public MenuItem makeMenuItem()
        {
            MenuItem menuItem = new MenuItem(name);
            menuItem.disableProperty().bind(disabled);
            menuItem.setOnAction(e -> actionPerformed());
            menuItem.acceleratorProperty().bind(accelerator);
            return menuItem;
        }
    }


    /* retained side effect: clears message in editor! */
    private final MoeEditor getEditor()
    {
        editor.clearMessage();
        return editor;
    }


    // --------------------------------------------------------------------

    private MoeAbstractAction saveAction()
    {
        return action("save", () -> getEditor().userSave());
    }

    // --------------------------------------------------------------------

    /**
     * Reload has been chosen. Ask "Really?" and call "doReload" if the answer
     * is yes.
     */
    private MoeAbstractAction reloadAction()
    {
        return action("reload", () -> getEditor().reload());
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction printAction()
    {
        return action("print", () -> getEditor().print());
    }

    private MoeAbstractAction pageSetupAction()
    {
        return action("page-setup", () -> MoeEditor.pageSetup());
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction closeAction()
    {
        return action("close", () -> getEditor().close());
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction undoAction()
    {
        return action("undo", () ->
        {
            MoeEditor editor = getEditor();
            try {
                editor.undoManager.undo();
            }
            catch (CannotUndoException ex) {
                Debug.message("moe: cannot undo...");
            }
        }).bindEnabled(editor.undoManager.canUndo());
    }

    private MoeAbstractAction redoAction()
    {
        return action("redo", () -> {
            MoeEditor editor = getEditor();
            try {
                editor.undoManager.redo();
            }
            catch (CannotRedoException ex) {
                Debug.message("moe: cannot redo...");
            }
        }).bindEnabled(editor.undoManager.canRedo());
    }

    private MoeAbstractAction commentBlockAction()
    {
        return action("comment-block", () -> {
            MoeEditor editor = getEditor();
            editor.undoManager.beginCompoundEdit();
            blockAction(editor, new CommentLineAction());
            editor.undoManager.endCompoundEdit();
        });
    }

    // --------------------------------------------------------------------    

    private MoeAbstractAction uncommentBlockAction()
    {
        return action("uncomment-block", () -> {
            MoeEditor editor = getEditor();
            editor.undoManager.beginCompoundEdit();
            blockAction(editor, new UncommentLineAction());
            editor.undoManager.endCompoundEdit();
        });
    }

    // === Tools: ===
    // --------------------------------------------------------------------

    private MoeAbstractAction indentBlockAction()
    {
        return action("indent-block", () -> doBlockIndent(getEditor()));
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction deindentBlockAction()
    {
        return action("deindent-block", () -> doBlockDeIndent(getEditor()));
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction autoIndentAction()
    {
        return action("autoindent", () -> {
            MoeEditor editor = getEditor();
            MoeSyntaxDocument doc = editor.getSourceDocument();
            if (doc.getParsedNode() == null) {
                // The Readme, or some other file which isn't parsed
                return;
            }

            int prevCaretPos = editor.getSourcePane().getCaretPosition();
            editor.setCaretActive(false);
            editor.undoManager.beginCompoundEdit();
            AutoIndentInformation info = MoeIndent.calculateIndentsAndApply(doc, prevCaretPos);
            editor.undoManager.endCompoundEdit();
            editor.setCaretPositionForward(info.getNewCaretPosition() - prevCaretPos);
            editor.setCaretActive(true);

            if (info.isPerfect()) {
                editor.writeMessage(Config.getString("editor.info.perfectIndent"));
            }
        });
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction insertMethodAction()
    {
        return action("insert-method", () -> {
            MoeEditor editor = getEditor();
            //this method should not be actioned if the editor is not displaying source code
            if (!editor.containsSourceCode()){
                return;
            }
            editor.undoManager.beginCompoundEdit();
            insertTemplate("method");
            editor.undoManager.endCompoundEdit();
        });
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction addJavadocAction()
    {
        return action("add-javadoc", () -> {
            MoeEditor editor = getEditor();
            //this method should not be actioned if the editor is not displaying source code
            if (!editor.containsSourceCode()) {
                return;
            }
            int caretPos = editor.getCurrentTextPane().getCaretPosition();
            NodeAndPosition<ParsedNode> node = editor.getParsedNode().findNodeAt(caretPos, 0);
            while (node != null && node.getNode().getNodeType() != ParsedNode.NODETYPE_METHODDEF) {
                node = node.getNode().findNodeAt(caretPos, node.getPosition());
            }
            if (node == null || !(node.getNode() instanceof MethodNode)) {
                editor.writeMessage(Config.getString("editor.addjavadoc.notAMethod"));
            } else {
                MethodNode methodNode = ((MethodNode)node.getNode());

                boolean hasJavadocComment = false;
                Iterator<NodeAndPosition<ParsedNode>> it = methodNode.getChildren(node.getPosition());
                while (it.hasNext()) {
                    ParsedNode subNode = it.next().getNode();
                    if (subNode instanceof CommentNode) {
                        hasJavadocComment = hasJavadocComment || ((CommentNode)subNode).isJavadocComment();
                    }
                }

                if (hasJavadocComment) {
                    editor.writeMessage(Config.getString("editor.addjavadoc.hasJavadoc"));
                } else {
                    StringBuilder indent = new StringBuilder();
                    int column = editor.getLineColumnFromOffset(node.getPosition()).getColumn();
                    for (int i = 0;i < column-1;i++)
                        indent.append(' ');
                    StringBuilder newComment = new StringBuilder();
                    newComment.append("/**\n");

                    JavaEntity retTypeEntity = methodNode.getReturnType();

                    if (retTypeEntity == null) {
                        // It's a constructor:
                        newComment.append(indent).append(" * ").append(methodNode.getName()).append(" ");
                        newComment.append(Config.getString("editor.addjavadoc.constructor")).append("\n");
                    } else {
                        // It's a method:
                        newComment.append(indent).append(" * ").append(Config.getString("editor.addjavadoc.method"));
                        newComment.append(" ").append(methodNode.getName()).append("\n");
                    }
                    newComment.append(indent).append(" *\n");

                    for (String s: methodNode.getParamNames()) {
                        newComment.append(indent).append(" * @param ").append(s).append(" ");
                        newComment.append(Config.getString("editor.addjavadoc.parameter")).append("\n");
                    }

                    if (retTypeEntity != null) {
                        JavaType retType = retTypeEntity.resolveAsType().getType();
                        if (retType != null && !retType.isVoid()) {
                            newComment.append(indent).append(" * @return ");
                            newComment.append(Config.getString("editor.addjavadoc.returnValue")).append("\n");
                        }
                    }

                    newComment.append(indent).append(" */\n").append(indent);

                    editor.undoManager.beginCompoundEdit();
                    editor.getCurrentTextPane().setCaretPosition(node.getPosition());
                    editor.getCurrentTextPane().replaceSelection(newComment.toString());
                    editor.getCurrentTextPane().setCaretPosition((caretPos + newComment.length()));
                    editor.undoManager.endCompoundEdit();
                }
            }
        });
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction indentAction()
    {
        return action("indent", () -> {
            MoeEditor ed = getEditor();

            if(haveSelection(ed)) {
                doBlockIndent(ed);
            }
            else {
                // if necessary, convert all TABs in the current editor to spaces
                int converted = 0;
                if (ed.checkExpandTabs()) {
                    // do TABs need expanding?
                    ed.setCaretActive(false);
                    converted = convertTabsToSpaces(ed);
                    ed.setCaretActive(true);
                }

                if (PrefMgr.getFlag(PrefMgr.AUTO_INDENT)) {
                    //MOEFX
                    //doIndent(textPane, false);
                }
                else {
                    //MOEFX
                    //insertSpacedTab(textPane);
                }

                if (converted > 0) {
                    ed.writeMessage(Config.getString("editor.info.tabsExpanded"));
                }
            }
        });
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction deIndentAction()
    {
        return action("de-indent", () -> {
            MoeEditor ed = getEditor();

            if(haveSelection(ed)) {
                doBlockDeIndent(ed);
            }
            else {
                // if necessary, convert all TABs in the current editor to spaces
                if (ed.checkExpandTabs()) { // do TABs need expanding?
                    ed.setCaretActive(false);
                    int converted = convertTabsToSpaces(ed);
                    ed.setCaretActive(true);

                    if (converted > 0)
                        ed.writeMessage(Config.getString("editor.info.tabsExpanded"));
                }
                //MOEFX
                //doDeIndent(textPane);
            }
        });
    }

    // === Options: ===
    // --------------------------------------------------------------------

    private MoeEditorPane getTextComponent()
    {
        return editor.getSourcePane();
    }

    private MoeAbstractAction newLineAction()
    {
        return action("new-line", () -> {

            MoeAbstractAction action = actions.get(DefaultEditorKit.insertBreakAction);
            action.actionPerformed();

            if (PrefMgr.getFlag(PrefMgr.AUTO_INDENT))
            {
                doIndent(getTextComponent(), true);
            }
        });
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction copyLineAction()
    {
        return action("copy-line", () -> {
            boolean addToClipboard = lastActionWasCut;
            getActionByName("caret-begin-line").actionPerformed();
            getActionByName("selection-down").actionPerformed();
            if (addToClipboard) {
                addSelectionToClipboard(editor);
            }
            else {
                getActionByName("copy-to-clipboard").actionPerformed();
            }
            lastActionWasCut = true;
        });
    }

    // === Help: ===
    // --------------------------------------------------------------------

    private MoeAbstractAction cutLineAction()
    {
        return action("cut-line", () -> {
            boolean addToClipboard = lastActionWasCut;
            getActionByName("caret-begin-line").actionPerformed();
            getActionByName("selection-down").actionPerformed();
            if (addToClipboard) {
                addSelectionToClipboard(editor);
                getActionByName("delete-previous").actionPerformed();
            }
            else {
                getActionByName("cut-to-clipboard").actionPerformed();
            }
            lastActionWasCut = true;
        });
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction increaseFontAction()
    {
        return action("increase-font", () -> {
            MoeEditorPane textPane = getTextComponent();
            /*MOEFX
            Font textPFont= textPane.getFont();           
            int newFont=textPFont.getSize()+1;
            PrefMgr.setEditorFontSize(newFont);
            getTextComponent(e).setFont(textPane.getFont().deriveFont((float)newFont));
            */
        });
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction decreaseFontAction()
    {
        return action("decrease-font", () -> {
            /*MOEFX
            JTextComponent textPane = getTextComponent(e);
            Font textPFont= textPane.getFont();            
            int newFont=textPFont.getSize()-1;
            PrefMgr.setEditorFontSize(newFont);
            getTextComponent(e).setFont(textPFont.deriveFont((float)newFont));
            */
        });
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction cutEndOfLineAction()
    {
        return action("cut-end-of-line", () -> {
            boolean addToClipboard = lastActionWasCut;

            getActionByName("selection-end-line").actionPerformed();
            MoeEditorPane textComponent = getTextComponent();
            String selection = textComponent.getSelectedText();
            if (selection == null)
                getActionByName("selection-forward").actionPerformed();

            if (addToClipboard) {
                addSelectionToClipboard(editor);
                getActionByName("delete-previous").actionPerformed();
            }
            else {
                getActionByName("cut-to-clipboard").actionPerformed();
            }
            lastActionWasCut = true;
        });
    }

    // ========================= SUPPORT ROUTINES ==========================

    private MoeAbstractAction cutWordAction()
    {
        return action("cut-word", () -> {
            boolean addToClipboard = lastActionWasCut;
            getActionByName("caret-previous-word").actionPerformed();
            getActionByName("selection-next-word").actionPerformed();
            if (addToClipboard) {
                addSelectionToClipboard(editor);
                getActionByName("delete-previous").actionPerformed();
            }
            else {
                getActionByName("cut-to-clipboard").actionPerformed();
            }
            lastActionWasCut = true;
        });
    }

    private MoeAbstractAction contentAssistAction()
    {
        return action("code-completion", () -> {
            MoeEditor editor = getEditor();
            if (Config.getPropBoolean("bluej.editor.codecompletion", true)){
                editor.createContentAssist();
            }
        });
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction cutEndOfWordAction()
    {
        return action("cut-end-of-word", () -> {
            boolean addToClipboard = lastActionWasCut;
            getActionByName("selection-next-word").actionPerformed();
            if (addToClipboard) {
                addSelectionToClipboard(editor);
                getActionByName("delete-previous").actionPerformed();
            }
            else {
                getActionByName("cut-to-clipboard").actionPerformed();
            }
            lastActionWasCut = true;
        });
    }

    // --------------------------------------------------------------------

    private abstract class MoeActionWithOrWithoutSelection extends MoeAbstractAction
    {
        private final boolean withSelection;

        protected MoeActionWithOrWithoutSelection(String actionName, boolean withSelection)
        {
            super(actionName);
            this.withSelection = withSelection;
        }

        protected void moveCaret(MoeEditorPane c, int pos)
        {
            if (withSelection) {
                c.moveCaretPosition(pos);
            }
            else {
                c.setCaretPosition(pos);
            }
        }
    }

    // -------------------------------------------------------------------

    class NextWordAction extends MoeActionWithOrWithoutSelection
    {
        public NextWordAction(boolean withSelection)
        {
            super(withSelection ? DefaultEditorKit.selectionNextWordAction : DefaultEditorKit.nextWordAction, withSelection);
        }

        @Override
        public void actionPerformed()
        {
            MoeEditorPane c = getTextComponent();
            int origPos = c.getCaretDot();
            int end = findWordLimit(c, origPos, true);
            if (Character.isWhitespace(c.getText(end, 1).charAt(0))) {
                // Whitespace region follows, find the end of it:
                int endOfWS = findWordLimit(c, end, true);
                moveCaret(c, endOfWS);
            }
            else {
                // A different "word" follows immediately, stay where we are:
                moveCaret(c, end);
            }
        }
    }

    // ===================== ACTION IMPLEMENTATION ======================

    class PrevWordAction extends MoeActionWithOrWithoutSelection
    {
        public PrevWordAction(boolean withSelection)
        {
            super(withSelection ? DefaultEditorKit.selectionPreviousWordAction : DefaultEditorKit.previousWordAction, withSelection);
        }

        @Override
        public void actionPerformed()
        {
            MoeEditorPane c = getTextComponent();
            int origPos = c.getCaretDot();
            if (origPos == 0) return;
            if (Character.isWhitespace(c.getText(origPos - 1, 1).charAt(0))) {
                // Whitespace region precedes, find the beginning of it:
                int startOfWS = findWordLimit(c, origPos - 1, false);
                int startOfPrevWord = findWordLimit(c, startOfWS - 1, false);
                moveCaret(c, startOfPrevWord);
            }
            else {
                // We're in the middle of a word already, find the start:
                int startOfWord = findWordLimit(c, origPos - 1, false);
                moveCaret(c, startOfWord);
            }
        }
    }

    class EndWordAction extends MoeActionWithOrWithoutSelection
    {
        public EndWordAction(boolean withSelection)
        {
            super(withSelection ? DefaultEditorKit.selectionEndWordAction : DefaultEditorKit.endWordAction, withSelection);
        }

        @Override
        public void actionPerformed()
        {
            MoeEditorPane c = getTextComponent();
            int origPos = c.getCaretDot();
            int end = findWordLimit(c, origPos, true);
            moveCaret(c, end);
        }
    }

    class BeginWordAction extends MoeActionWithOrWithoutSelection
    {
        public BeginWordAction(boolean withSelection)
        {
            super(withSelection ? DefaultEditorKit.selectionBeginWordAction : DefaultEditorKit.beginWordAction, withSelection);
        }

        @Override
        public void actionPerformed()
        {
            MoeEditorPane c = getTextComponent();
            int origPos = c.getCaretDot();
            int start = findWordLimit(c, origPos, false);
            moveCaret(c, start);
        }
    }

    // --------------------------------------------------------------------
    private MoeAbstractAction deleteWordAction()
    {
        return action("delete-previous-word", () -> {
            MoeEditorPane c = getTextComponent();
            MoeAbstractAction prevWordAct = actions.get(DefaultEditorKit.previousWordAction);
            int end = c.getCaretDot();
            prevWordAct.actionPerformed();
            int begin = c.getCaretDot();
            c.replaceText(begin, end - begin, "");
        });
    }

    private MoeAbstractAction selectWordAction()
    {
        return action(DefaultEditorKit.selectWordAction, () -> {
            MoeEditorPane c = getTextComponent();
            int origPos = c.getCaretDot();
            int newStart = findWordLimit(c, origPos, false);
            int newEnd = findWordLimit(c, origPos, true);
            c.setCaretPosition(newStart);
            c.moveCaretPosition(newEnd);
        });
    }
    //MOEFX
    /*
    class FindAction extends MoeAbstractAction
    {
        public FindAction(MoeEditor editor)
        {
            super("find", editor);
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            //getEditor(e).find();
            MoeEditor editor=getEditor();
            editor.initFindPanel();
        }
    }

    public class FindNextAction extends MoeAbstractAction
    {
        public FindNextAction(MoeEditor editor)
        {
            super("find-next", editor);
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            getEditor().findNext(false);
        }
    }

    public class FindNextBackwardAction extends MoeAbstractAction
    {
        public FindNextBackwardAction(MoeEditor editor)
        {
            super("find-next-backward", editor);
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            getEditor().findNext(true);
        }
    }
    */

    private MoeAbstractAction replaceAction()
    {
        return action("replace",() ->
        {
            MoeEditor editor = getEditor();
            editor.setFindPanelVisible();
            editor.setReplacePanelVisible(true);
            if (editor.getSourcePane().getSelectedText() != null)
            {
                editor.setFindTextfield(editor.getSourcePane().getSelectedText());
            }
        });
    }

    private MoeAbstractAction compileOrNextErrorAction()
    {
        return action("compile", () -> getEditor().compileOrShowNextError());
    }

    private MoeAbstractAction toggleInterfaceAction()
    {
        return action("toggle-interface-view", () -> {
            /*MOEFX
            Object source = e.getSource();
            if (source instanceof JComboBox) {
                getEditor().toggleInterface();
            }
            else {
                getEditor().toggleInterfaceMenu();
            }
            */
        });
    }

    private MoeAbstractAction toggleBreakPointAction()
    {
        return action("toggle-breakpoint", () -> getEditor().toggleBreakpoint());
    }

    private MoeAbstractAction keyBindingsAction()
    {
        return action("key-bindings", () -> PrefMgrDialog.showDialog(1)); // 1 is the index of the key bindings pane in the pref dialog
    }

    private MoeAbstractAction preferencesAction()
    {
        return action("preferences", () -> PrefMgrDialog.showDialog(0)); // 0 is the index of the editor pane in the pref dialog
    }

    // --------------------------------------------------------------------

    private MoeAbstractAction describeKeyAction()
    {
        return action("describe-key", () -> {
            /*MOEFX
            JTextComponent textComponent = getTextComponent(e);
            textComponent.addKeyListener(keyCatcher);
            MoeEditor ed = getEditor();
            keyCatcher.setEditor(ed);
            ed.writeMessage("Describe key: ");
            */
        });
    }

    private MoeAbstractAction goToLineAction()
    {
        return action("go-to-line", () -> getEditor().goToLine());
    }

    private MoeAbstractAction doNothingAction()
    {
        return action("", () -> {});
    }

    /**
     * Class CommentLineAction - add a comment symbol to the given line.
     */
    class CommentLineAction
    implements LineAction
    {
        /**
         * Comment the given line
         */
        @Override
        public void apply(Element line, MoeSyntaxDocument doc)
        {
            int lineStart = line.getStartOffset();
            int lineEnd = line.getEndOffset();
                String lineText = doc.getText(lineStart, lineEnd - lineStart);
                if (lineText.trim().length() > 0) {
                    int textStart = MoeIndent.findFirstNonIndentChar(lineText, true);
                    doc.insertString(lineStart+textStart, "// ", null);
                }
        }
    }

    /**
     * Class UncommentLineAction - remove the comment symbol (if any) from the
     * given line.
     */
    class UncommentLineAction implements LineAction
    {
        @Override
        public void apply(Element line, MoeSyntaxDocument doc)
        {
            int lineStart = line.getStartOffset();
            int lineEnd = line.getEndOffset();
            try {
                String lineText = doc.getText(lineStart, lineEnd - lineStart);
                if (lineText.trim().startsWith("//")) {
                    int cnt = 0;
                    while (lineText.charAt(cnt) != '/') {
                        // whitespace chars
                        cnt++;
                    }
                    if (lineText.charAt(cnt + 2) == ' ') {
                        doc.remove(lineStart+cnt, 3);
                    }
                    else {
                        doc.remove(lineStart+cnt, 2);
                    }
                }
            }
            catch (Exception exc) {}
        }
    }

    /**
     * Class IndentLineAction - add one level of indentation to the given line.
     */
    class IndentLineAction implements LineAction
    {
        @Override
        public void apply(Element line, MoeSyntaxDocument doc)
        {
            int lineStart = line.getStartOffset();
            doc.insertString(lineStart, spaces.substring(0, tabSize), null);
        }
    }

    /**
     * Class DeindentLineAction - remove one indentation level from the given
     * line.
     */
    class DeindentLineAction implements LineAction
    {
        @Override
        public void apply(Element line, MoeSyntaxDocument doc)
        {
            int lineStart = line.getStartOffset();
            int lineEnd = line.getEndOffset();
            try {
                String lineText = doc.getText(lineStart, lineEnd - lineStart);
                String spacedTab = spaces.substring(0, tabSize);
                if (lineText.startsWith(spacedTab)) {
                    doc.remove(lineStart, tabSize); // remove spaced tab
                }
                else if (lineText.charAt(0) == TAB_CHAR) {
                    doc.remove(lineStart, 1); // remove hard tab
                }
                else {
                    int cnt = 0;
                    while (lineText.charAt(cnt) == ' ') {
                        // remove spaces
                        cnt++;
                    }
                    doc.remove(lineStart, cnt);
                }
            }
            catch (Exception exc) {}
        }
    }

    /**
     * Class KeyCatcher - used for implementation of "describe-key" command to
     * catch the next key press so that we can see what it does.
     */
    /*MOEFX
    class KeyCatcher extends KeyAdapter
    {
        MoeEditor editor;

        @Override
        public void keyPressed(KeyEvent e)
        {
            int keyCode = e.getKeyCode();

            if (keyCode == KeyEvent.VK_CAPS_LOCK || // the keys we want to
                    // ignore...
                    keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL || keyCode == KeyEvent.VK_META
                    || keyCode == KeyEvent.VK_ALT || keyCode == KeyEvent.VK_ALT_GRAPH || keyCode == KeyEvent.VK_COMPOSE
                    || keyCode == KeyEvent.VK_NUM_LOCK || keyCode == KeyEvent.VK_SCROLL_LOCK
                    || keyCode == KeyEvent.VK_UNDEFINED)
                return;

            KeyStroke key = KeyStroke.getKeyStrokeForEvent(e);
            String modifierName = KeyEvent.getKeyModifiersText(key.getModifiers());
            String keyName = KeyEvent.getKeyText(keyCode);
            if (modifierName.length() > 0)
                keyName = modifierName + "+" + keyName;

            Keymap map = keymap;
            Action action = null;

            while (map != null && action == null) {
                action = map.getAction(key);
                map = map.getResolveParent();
            }

            if (action == null) {
                // BUG workaround: bindings inhertited from component are not
                // found
                // through the keymap. we search for them explicitly here...
                Object binding = componentInputMap.get(key);
                if (binding == null){
                    editor.writeMessage(keyName + " " + Config.getString("editor.keypressed.keyIsNotBound").trim());
                }
                else {
                    editor.writeMessage(keyName + " " +Config.getString("editor.keypressed.callsTheFunction").trim() + binding + "\"");
                }
            }
            else {
                String name = (String) action.getValue(Action.NAME);
                editor.writeMessage(keyName + Config.getString("editor.keypressed.callsTheFunction") + name + "\"");
            }
            e.getComponent().removeKeyListener(keyCatcher);
            e.consume();
        }

        public void setEditor(MoeEditor ed)
        {
            editor = ed;
        }

    }
    */
}
