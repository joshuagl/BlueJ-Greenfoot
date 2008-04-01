package greenfoot.platforms.standalone;

import greenfoot.World;
import greenfoot.core.WorldHandler;
import greenfoot.export.GreenfootScenarioViewer;
import greenfoot.gui.DragGlassPane;
import greenfoot.gui.InputManager;
import greenfoot.platforms.WorldHandlerDelegate;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;


/**
 * Implementation for running scenarios in a standalone application or applet.
 * 
 * @author Poul Henriksen
 *
 */
public class WorldHandlerDelegateStandAlone implements WorldHandlerDelegate
{    
    private WorldHandler worldHandler;
    private GreenfootScenarioViewer viewer;
    private boolean lockScenario;
    
    public WorldHandlerDelegateStandAlone (GreenfootScenarioViewer viewer, boolean lockScenario) 
    {
        this.viewer = viewer;
        this.lockScenario = lockScenario;
    }
    
    public void dragFinished(Object o)
    {
        worldHandler.finishDrag(o);
    }

    public void keyReleased(KeyEvent e)
    {
        // Not used in standalone
    }

    public boolean maybeShowPopup(MouseEvent e)
    {
        // Not used in standalone
        return false;
    }

    public void mouseClicked(MouseEvent e)
    {
        // Not used in standalone
    }

    public void processKeyEvent(KeyEvent e)
    {
        // Not used in standalone
    }

    public void setQuickAddActive(boolean b)
    {
        // Not used in standalone
    }

    public void setWorld(final World oldWorld, final World newWorld)
    {
        ActorDelegateStandAlone.initWorld(newWorld);
    }

    public void setWorldHandler(WorldHandler handler)
    {
        this.worldHandler = handler;
    }

    public void instantiateNewWorld()
    {
        viewer.instantiateNewWorld();
    }

    public Class getLastWorldClass()
    {
        // Not used in standalone
        return null;
    }
    
    public InputManager getInputManager()
    {
        InputManager inputManager = new InputManager();       
        DragGlassPane.getInstance().addMouseListener(inputManager);
        DragGlassPane.getInstance().addMouseMotionListener(inputManager);
        DragGlassPane.getInstance().addKeyListener(inputManager);       
        if (lockScenario) {
            inputManager.setIdleListeners(new KeyAdapter() {}, new MouseAdapter() {}, new MouseMotionAdapter() {});
            inputManager.setDragListeners(new KeyAdapter() {}, new MouseAdapter() {}, new MouseMotionAdapter() {});
            inputManager.setMoveListeners(new KeyAdapter() {}, new MouseAdapter() {}, new MouseMotionAdapter() {});
        }
        else {
            inputManager.setIdleListeners(worldHandler, worldHandler, worldHandler);
            inputManager.setDragListeners(DragGlassPane.getInstance(), DragGlassPane.getInstance(), DragGlassPane
                    .getInstance());
            inputManager.setMoveListeners(worldHandler, worldHandler, worldHandler);
        }
        return inputManager;
    }
}
