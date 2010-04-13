/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009  Michael Kolling and John Rosenberg 
 
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
package bluej.parser.nodes;

import java.util.List;
import java.util.Map;

import javax.swing.text.Document;

import bluej.debugger.gentype.Reflective;
import bluej.editor.moe.MoeSyntaxDocument;
import bluej.parser.CodeSuggestions;
import bluej.parser.EditorParser;
import bluej.parser.JavaParser;
import bluej.parser.entity.JavaEntity;
import bluej.parser.entity.PackageOrClass;
import bluej.parser.entity.ParsedReflective;
import bluej.parser.entity.TypeEntity;
import bluej.parser.lexer.JavaTokenTypes;
import bluej.parser.lexer.LocatableToken;
import bluej.parser.nodes.NodeTree.NodeAndPosition;



/**
 * A node representing a parsed type (class, interface, enum)
 * 
 * @author Davin McCall
 */
public class ParsedTypeNode extends IncrementalParsingNode
{
    private String name;
    private String prefix;
    private TypeInnerNode inner;
    private Map<String,JavaEntity> typeParams;
    private List<JavaEntity> extendedTypes;
    private List<JavaEntity> implementedTypes;
    
    private int type; // one of JavaParser.TYPEDEF_CLASS, INTERFACE, ENUM, ANNOTATION
    
    /**
     * Construct a new ParsedTypeNode
     * @param parent  The parent node
     * @param name    The base name of the type
     * @param prefix  The prefix of the name, including the final ".", to make this a full
     *                type name
     */
    public ParsedTypeNode(ParsedNode parent, int type, String prefix)
    {
        super(parent);
        stateMarkers = new int[2];
        marksEnd = new boolean[2];
        stateMarkers[0] = -1;
        stateMarkers[1] = -1;
        this.type = type;
        this.prefix = prefix;
    }
    
    public void setTypeParams(Map<String, JavaEntity> typeParams)
    {
        this.typeParams = typeParams;
    }
    
    public void setImplementedTypes(List<JavaEntity> implementedTypes)
    {
        this.implementedTypes = implementedTypes;
    }
    
    public List<JavaEntity> getImplementedTypes()
    {
        return implementedTypes;
    }
    
    public void setExtendedTypes(List<JavaEntity> extendedTypes)
    {
        this.extendedTypes = extendedTypes;
    }
    
    public List<JavaEntity> getExtendedTypes()
    {
        return extendedTypes;
    }
    
    @Override
    public int getNodeType()
    {
        return NODETYPE_TYPEDEF;
    }
    
    public boolean isContainer()
    {
        return true;
    }
    
    public void setName(String name)
    {
        String oldName = this.name;
        this.name = name;
        getParentNode().childChangedName(this, oldName);
    }
    
    @Override
    public String getName()
    {
        return name;
    }
    
    public String getPrefix()
    {
        return prefix;
    }
    
    /**
     * Insert the inner node for the type definition.
     * The inner node will hold the field definitions etc.
     */
    public void insertInner(TypeInnerNode child, int position, int size)
    {
        super.insertNode(child, position, size);
        inner = child;
    }
    
    public TypeInnerNode getInner()
    {
        return inner;
    }
    
    @Override
    protected int doPartialParse(EditorParser parser, int state)
    {
        if (state == 0) {
            // [modifiers] {class|interface|enum|@interface} name [<type params>] [extends...] {
            int r = parser.parseTypeDefBegin();
            if (r == JavaParser.TYPEDEF_EPIC_FAIL) {
                return PP_EPIC_FAIL;
            }
            
            type = r;
            parser.initializeTypeExtras();
            
            LocatableToken token = parser.getTokenStream().nextToken();
            if (token.getType() != JavaTokenTypes.IDENT) {
                last = token;
                return PP_ENDS_NODE;
            }
            setName(token.getText());
            
            token = parser.parseTypeDefPart2();
            if (token == null) {
                last = parser.getTokenStream().LA(1);
                return PP_ENDS_NODE;
            }
            last = token;
            parser.getTokenStream().pushBack(token);
            return PP_BEGINS_NEXT_STATE;
        }
        else if (state == 1) {
            // '{' class body '}'
            // We only get into this state rarely
            last = parser.parseTypeBody(type, parser.getTokenStream().nextToken());
            if (last.getType() == JavaTokenTypes.RCURLY) {
                return PP_ENDS_STATE;
            }
            return PP_INCOMPLETE;
        }
        else if (state == 2) {
            last = parser.getTokenStream().LA(1);
            complete = true;
            return PP_ENDS_NODE;
        }
        
        return PP_EPIC_FAIL;
    }
    
    @Override
    protected boolean lastPartialCompleted(EditorParser parser,
            LocatableToken token)
    {
        // TODO Auto-generated method stub
        return super.lastPartialCompleted(parser, token);
    }
    
    @Override
    protected boolean isDelimitingNode(NodeAndPosition<ParsedNode> nap)
    {
        return false;
    }

    private boolean handleTextChange(Document document, int nodePos, int changePos, int length,
            NodeStructureListener listener)
    {
        // If a change occurs after the inner node, there is a risk that the '}' closing
        // the inner node has moved or been removed.
        if (inner != null) {
            int innerPos = inner.getOffsetFromParent() + nodePos;
            if (changePos > innerPos) {
                inner.setComplete(false);
                int newInnerSize = getSize() + nodePos - innerPos;
                inner.setSize(newInnerSize);
                ((MoeSyntaxDocument) document).scheduleReparse(changePos, length);
                return true;
            }
        }
        return false;
    }
    
    @Override
    protected int handleInsertion(Document document, int nodePos, int insPos,
            int length, NodeStructureListener listener)
    {
        if (handleTextChange(document, nodePos, insPos, length, listener)) {
            return ALL_OK;
        }
        return super.handleInsertion(document, nodePos, insPos, length, listener);
    }

    @Override
    protected int handleDeletion(Document document, int nodePos, int dpos,
            NodeStructureListener listener)
    {
        if (handleTextChange(document, nodePos, dpos, 1, listener)) {
            return ALL_OK;
        }
        return super.handleDeletion(document, nodePos, dpos, listener);
    }
    
    @Override
    public CodeSuggestions getExpressionType(int pos, int nodePos, TypeEntity defaultType, Document document)
    {
        // The default type if the expression is not know should be this type
        TypeEntity myType = new TypeEntity(new ParsedReflective(this));
        NodeAndPosition<ParsedNode> child = getNodeTree().findNode(pos, nodePos);
        if (child != null) {
            return child.getNode().getExpressionType(pos, child.getPosition(), myType, document);
        }
        
        // We don't return the specified default type (which must be an outer type). There
        // can be no completions because no completions can occur except in the context
        // of child nodes.
        return null;
    }
    
    @Override
    public PackageOrClass resolvePackageOrClass(String name, Reflective querySource)
    {
        if (typeParams != null) {
            JavaEntity ent = typeParams.get(name);
            if (ent != null) {
                TypeEntity tent = ent.resolveAsType();
                if (tent != null) {
                    return tent;
                }
            }
        }
        return super.resolvePackageOrClass(name, querySource);
    }
}
