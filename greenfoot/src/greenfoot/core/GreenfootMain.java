package greenfoot.core;

import greenfoot.ObjectTracker;
import greenfoot.event.ActorInstantiationListener;
import greenfoot.event.CompileListener;
import greenfoot.event.CompileListenerForwarder;
import greenfoot.gui.GreenfootFrame;
import greenfoot.gui.MessageDialog;
import greenfoot.platforms.ide.ActorDelegateIDE;
import greenfoot.util.GreenfootUtil;
import greenfoot.util.Version;

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Field;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JButton;

import rmiextension.wrappers.RBlueJ;
import rmiextension.wrappers.RPackage;
import rmiextension.wrappers.RProject;
import rmiextension.wrappers.event.RCompileEvent;
import rmiextension.wrappers.event.RInvocationListener;
import rmiextension.wrappers.event.RProjectListener;
import bluej.Config;
import bluej.debugmgr.CallHistory;
import bluej.extensions.ProjectNotOpenException;
import bluej.runtime.ExecServer;
import bluej.utility.Debug;
import bluej.utility.Utility;
import bluej.views.View;

/**
 * The main class for greenfoot. This is a singelton (in the JVM). Since each
 * project is opened in its own JVM there can be several Greenfoot instances,
 * but each will be in its own JVM so it is effectively a singleton.
 * 
 * @author Poul Henriksen <polle@mip.sdu.dk>
 * @version $Id: GreenfootMain.java 5650 2008-03-23 17:44:03Z polle $
 */
public class GreenfootMain extends Thread implements CompileListener, RProjectListener
{
    /* Constants for return from updateApi method */
    /** The project API version matches the greenfoot API version */
    public static final int VERSION_OK = 0;
    /** The project API version was different, and has been updated */
    public static final int VERSION_UPDATED = 1;
    /** The project was not a greenfoot project, or the user chose to cancel the open */
    public static final int VERSION_BAD = 2;
    
    /** Greenfoot is a singleton - this is the instance. */
    private static GreenfootMain instance;

    /** The connection to BlueJ via RMI */
    private RBlueJ rBlueJ;

    /** The main frame of greenfoot. */
    private GreenfootFrame frame;

    /** The project this Greenfoot singelton refers to. */
    private GProject project;

    /** The package this Greenfoot singelton refers to. */
    private GPackage pkg;

    /** The path to the dummy startup project */
    private File startupProject;
    
    /**
     * Forwards compile events to all the compileListeners that has registered
     * to reccieve compile events.
     */
    private CompileListenerForwarder compileListenerForwarder;
    private List<CompileListener> compileListeners = new LinkedList<CompileListener>();

    /** The class state manager notifies GClass objects when their compilation state changes */
    private ClassStateManager classStateManager;

    /** Listens for instantiations of Actor objects. */
    private ActorInstantiationListener instantiationListener;

    /** List of invocation listeners that has been registered. */
    private List<RInvocationListener> invocationListeners = new ArrayList<RInvocationListener>();

    /** History of parameters passed to methods. */
    private CallHistory callHistory = new CallHistory();

    /** Filter that matches class files */
    private static FilenameFilter classFilter = new FilenameFilter() {
        public boolean accept(File dir, String name)
        {
            return name.toLowerCase().endsWith(".class");
        }
    };
    
    private ClassLoader currentLoader;
    
  
    // ----------- static methods ------------

    /**
     * Initializes the singleton. This can only be done once - subsequent calls
     * will have no effect.
     */
    public static void initialize(RBlueJ rBlueJ, RPackage pkg)
    {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        if (instance == null) {
            try {
                instance = new GreenfootMain(rBlueJ, pkg.getProject());
            }
            catch (ProjectNotOpenException pnoe) {
                // can't happen
                pnoe.printStackTrace();
            }
            catch (RemoteException re) {
                // shouldn't happen
                re.printStackTrace();
            }
        }
    }

    /**
     * Gets the singleton.
     */
    public static GreenfootMain getInstance()
    {
        return instance;
    }

    // ----------- instance methods ------------

    /**
     * Contructor is private. This class is initialised via the 'initialize'
     * method (above).
     */
    private GreenfootMain(final RBlueJ rBlueJ, final RProject proj)
    {
        instance = this;
        this.rBlueJ = rBlueJ;
        currentLoader = ExecServer.getCurrentClassLoader();
        addCompileListener(this);
        try {
            // determine the path of the startup project
            File startupProj = rBlueJ.getSystemLibDir();
            startupProj = new File(startupProj, "greenfoot");
            startupProject = new File(startupProj, "startupProject");

            this.project = new GProject(proj);
            addCompileListener(project);
            this.pkg = project.getDefaultPackage();
            ActorDelegateIDE.setupAsActorDelegate(project);

            EventQueue.invokeLater(new Runnable() {
            	public void run() {
                    frame = GreenfootFrame.getGreenfootFrame(rBlueJ);

                    // Config is initialized in GreenfootLauncherDebugVM

                    if(!isStartupProject()) {
                        try {
                            instantiationListener = new ActorInstantiationListener(WorldHandler.getInstance());

                            frame.openProject(project);
                            Utility.bringToFront();

                            compileListenerForwarder = new CompileListenerForwarder(compileListeners);
                            GreenfootMain.this.rBlueJ.addCompileListener(compileListenerForwarder, pkg.getProject().getDir());
                            
                            classStateManager = new ClassStateManager(project);
                            rBlueJ.addClassListener(classStateManager);
                        }
                        catch (Exception exc) {
                            Debug.reportError("Error when opening scenario", exc);
                        }
                    }
                    else {
                        Utility.bringToFront();
                    }
            	}
            });
        }
        catch (Exception exc) {
            Debug.reportError("could not create greenfoot main", exc);
        }

    }
    
    
    /**
     * Check whether this instance of greenfoot is running the dummy
     * startup project.
     * @return  true if this is the startup project
     */
    private boolean isStartupProject()
    {
        try {
            return project.getDir().equals(startupProject);
        }
        catch (ProjectNotOpenException pnoe) {
            return false;
        }
        catch (RemoteException re) {
            return false;
        }
    }
    
    /**
     * Opens the project in the given directory. The project launches in a
     * new VM.
     */
    public void openProject(String projectDir)
        throws RemoteException
    {
    	File projectDirFile = new File(projectDir);
    	
    	// Display msg dialog of project does not exist.
    	if (!projectDirFile.exists()) {
            JButton[] buttons = new JButton[]{new JButton(Config.getString("greenfoot.continue"))};
            MessageDialog confirmRemove = new MessageDialog(frame, Config.getString("noproject.dialog.msg")
                    + System.getProperty("line.separator") + projectDir, Config.getString("noproject.dialog.title"),
                    200, buttons);
            confirmRemove.display();
    	    return;
    	}
    	
        try {
    		// It's possible that the user re-opened a project which they previously closed,
    		// resulting in an empty frame (because no other open projects). In that case the
    		// project is actually still running, behind the scenes; so just re-display it.
    		if (project.getDir().equals(projectDirFile)) {
    			frame.openProject(project);
    			return;
    		}
    	}
    	catch (ProjectNotOpenException pnoe) {}
    	
        int versionStatus = GreenfootMain.updateApi(projectDirFile, frame);
        boolean doOpen = versionStatus != VERSION_BAD;
        if (doOpen) {
            rBlueJ.openProject(projectDir);

            // if this is the dummy startup project, close it now.
            if(frame.getProject() == null) {
                project.close();
            }
        }
    }
    
    /**
     * Opens a file browser to find a greenfoot project
     */
    public void openProjectBrowser()
    {
        File dirName = GreenfootUtil.getScenarioFromFileBrowser(frame);
    
        if (dirName != null) {
            try {
                openProject(dirName.getAbsolutePath());
            }
            catch (Exception exc) {
                Debug.reportError("Could not open scenario", exc);
            }
        }
    }

    /**
     * Get the project for this greenfoot instance.
     * @return
     */
    public GProject getProject()
    {
        return project;
    }

    /**
     * Closes this greenfoot frame, or handle it closing.
     * 
     * If this is called with the windowClosing parameter false, and there is only one project open,
     * then the frame won't be closed but will instead be turned into an empty frame.
     */
    private void closeThisInstance(boolean windowClosing)
    {
        try {
            if (rBlueJ.getOpenProjects().length <= 1) {
                if (windowClosing) {
                	// This happens to be the only way the startup project can be closed
                    rBlueJ.exit();
                } else {
                    frame.closeProject();
                    //getInstance().openProject(startupProject.getPath());
                    //project.close();
                }
            } else {
                project.close();
            }
        } catch (RemoteException re) {
            re.printStackTrace();
        }
    }
    
    /**
     * Close the project in the given frame. This will also close the frame, or (if
     * the windowClosing parameter is false, and no other projects are open) make it
     * empty.
     */
    public static void closeProject(GreenfootFrame frame, boolean windowClosing)
    {
    	instance.closeThisInstance(windowClosing);
    }
    
    /* (non-Javadoc)
     * @see rmiextension.wrappers.event.RProjectListener#projectClosing()
     */
    public void projectClosing()
    {
        try {
            if(!isStartupProject()) {
                rBlueJ.removeCompileListener(compileListenerForwarder);
                rBlueJ.removeClassListener(classStateManager);
                storeFrameState();
                for (RInvocationListener element : invocationListeners) {
                    rBlueJ.removeInvocationListener(element);
                }
            }
        }
        catch (RemoteException re) {
            re.printStackTrace();
        }
    }
        
    /**
     * Close all open Greenfoot project instances, i.e. exit the application.
     */
    public static void closeAll()
    {
        try {
            getInstance().rBlueJ.exit();
        }
        catch (RemoteException re) {
            re.printStackTrace();
        }
    }
    
    /**
     * Store the current main window size to the project properties.
     */
    private void storeFrameState()
    {
        ProjectProperties projectProperties = project.getProjectProperties();
        
        projectProperties.setInt("mainWindow.width", frame.getWidth());
        projectProperties.setInt("mainWindow.height", frame.getHeight());
        Point loc = frame.getLocation();
        projectProperties.setInt("mainWindow.x", loc.x);
        projectProperties.setInt("mainWindow.y", loc.y);

        projectProperties.setInt("simulation.speed", Simulation.getInstance().getSpeed());
        
        Class cls = WorldHandler.getInstance().getLastWorldClass();
        if(cls != null) {
            projectProperties.setString("world.lastInstantiated", WorldHandler.getInstance().getLastWorldClass().getName());
        }
        
        projectProperties.save();
    }

    /**
     * Adds a listener for compile events
     * 
     * @param listener
     */
    private void addCompileListener(CompileListener listener)
    {
        synchronized (compileListeners) {
            compileListeners.add(0, listener);            
        }
    }
    
    /**
     * Adds a listener for invocation events
     * 
     * @param listener
     */
    public void addInvocationListener(RInvocationListener listener)
        throws RemoteException
    {
        invocationListeners.add(listener);
        rBlueJ.addInvocationListener(listener);
    }

    /**
     * Creates a new project
     */
    public void newProject()
    {
        String newname = GreenfootUtil.getNewProjectName(frame);
        if (newname != null) {
            try {
                File f = new File(newname);
                rBlueJ.newProject(f);
                // The rest of the project preparation will be done by the
                // ProjectManager on the BlueJ VM.

                // if the project that is already open is the dummy startup project, close it now.
                if(isStartupProject()) {
                    project.close();
                }
            }
            catch (Exception exc) {
                Debug.reportError("Problems when trying to create new scenario...", exc);
            }
        }
    }

    /**
     * Get a reference to the CallHistory instance.
     */
    public CallHistory getCallHistory()
    {
        return callHistory;
    }

    /**
     * Get a reference to the invocation listener.
     */
    public ActorInstantiationListener getInvocationListener()
    {
        return instantiationListener;
    }

    /**
     * Get a reference to the greenfoot frame.
     */
    public GreenfootFrame getFrame()
    {
        return frame;
    }

    /**
     * Makes a project a greenfoot project. That is, copy the system classes to
     * the users library.
     * 
     * @param projectDir absolute path to the project
     */
    private static void prepareGreenfootProject(File greenfootLibDir, File projectDir, ProjectProperties p)
    {
        if (isStartupProject(greenfootLibDir, projectDir)) {
            return;
        }
        File src = new File(greenfootLibDir, "skeletonProject");
        File dst = projectDir;

        deleteAllClassFiles(dst);
        
        // Since Greenfoot 1.3.0 we no longer use the bluej.pkg file, so if it
        // exists it should now be deleted.
        try {
            File pkgFile = new File(dst, bluej.pkgmgr.Package.pkgfileName);
            if (pkgFile.exists()) {
                pkgFile.delete();
            }
            File pkhFile = new File(dst, "bluej.pkh");
            if (pkhFile.exists()) {
                pkhFile.delete();
            }
        }
        catch (SecurityException e) {
            // If we don't have permission to delete, just leave them there.
        }
        
        GreenfootUtil.copyDir(src, dst);
        
        touchApiClasses(dst);
        
        p.setApiVersion(getAPIVersion().toString());
        p.save();
    }
    
    /**
     * "Touch" the actor and world class files to ensure that BlueJ/
     * Greenfoot think they are compiled.
     * 
     * @param projectDir  The Greenfoot project directory
     */
    private static void touchApiClasses(File projectDir)
    {
        // touch the Actor and World classes to ensure that they show
        // as being compiled
        File greenfootPkgDir = new File(projectDir, "greenfoot");
        File actorClassFile = new File(greenfootPkgDir, "Actor.class");
        File worldClassFile = new File(greenfootPkgDir, "World.class");
        long currentTime = System.currentTimeMillis();
        actorClassFile.setLastModified(currentTime);
        worldClassFile.setLastModified(currentTime);
    }

    /**
     * Checks whether the API version this project was created with is
     * compatible with the current API version. If it is not, it will attempt to
     * update the project to the current version of the API and present the user
     * with a dialog with instructions on what to do if there is a changes in
     * API version that requires manual modifications of the API.
     * <p>
     * If is considered safe to open this project with the current API version
     * the method will return true.
     * 
     * @param project The project in question.
     * @param parent Frame that should be used to place dialogs.
     * @return One of VERSION_OK, VERSION_UPDATED or VERSION_BAD
     * @throws RemoteException
     */
    public static int updateApi(File projectDir, Frame parent)
    {
        File greenfootLibDir = Config.getGreenfootLibDir();
        ProjectProperties newProperties = new ProjectProperties(projectDir);
        Version projectVersion = newProperties.getAPIVersion();

        Version apiVersion = GreenfootMain.getAPIVersion();

        if (projectVersion.equals(apiVersion)) {
            // If the version number matches everything should be ok.
            //
            // Just to be sure, we check that the greenfoot subdirectory is
            // actually there. This makes it easier to work with, since it will
            // then reinstall the classes after cleaning the scenarios with the
            // ant script.
            File greenfootDir = new File(projectDir, "greenfoot");
            if(! greenfootDir.exists()) {
                GreenfootMain.prepareGreenfootProject(greenfootLibDir, projectDir, newProperties);
            }
            return VERSION_OK;
        }

        if (projectVersion == Version.NO_VERSION) {
            String message = Config.getString("project.version.none");
            JButton continueButton = new JButton(Config.getString("greenfoot.continue"));
            MessageDialog dialog = new MessageDialog(parent, message, Config.getString("project.version.mismatch"), 50,
                    new JButton[]{continueButton});
            dialog.displayModal();
            System.out.println(message);
            GreenfootMain.prepareGreenfootProject(greenfootLibDir, projectDir, newProperties);
            return VERSION_UPDATED;
        }
        else if (projectVersion.compareTo(apiVersion) < 0) {
            String message = Config.getString("project.version.older.part1") + projectVersion
                    + Config.getString("project.version.older.part2") + apiVersion
                    + Config.getString("project.version.older.part3");
            JButton continueButton = new JButton(Config.getString("greenfoot.continue"));
            MessageDialog dialog = new MessageDialog(parent, message, Config.getString("project.version.mismatch"), 50,
                    new JButton[]{continueButton});
            dialog.displayModal();
            GreenfootMain.prepareGreenfootProject(greenfootLibDir, projectDir, newProperties);
            return VERSION_UPDATED;
        }
        else if (projectVersion.compareTo(apiVersion) > 0) { //
            String message = Config.getString("project.version.newer.part1") + projectVersion 
                + Config.getString("project.version.newer.part2");

            JButton cancelButton = new JButton(Config.getString("greenfoot.cancel"));
            JButton continueButton = new JButton(Config.getString("greenfoot.continue"));
            MessageDialog dialog = new MessageDialog(parent, message, Config.getString("project.version.mismatch"), 50, new JButton[]{
                    continueButton, cancelButton});
            JButton pressed = dialog.displayModal();
            if (pressed == cancelButton) {
                return VERSION_BAD;
            }
            else {
                prepareGreenfootProject(greenfootLibDir, projectDir, newProperties);
                return VERSION_UPDATED;
            }
        }
        else {
            String message = Config.getString("project.version.notGreenfoot") + projectDir;
            JButton continueButton = new JButton(Config.getString("greenfoot.continue"));
            MessageDialog dialog = new MessageDialog(parent, message, Config.getString("project.version.mismatch"), 50,
                    new JButton[]{continueButton});
            dialog.displayModal();
            return VERSION_BAD;
        }

    }

    /**
     * Deletes all class files in the directory, including the greenfoot subdirectory.
     */
    public static void deleteAllClassFiles(File dst) 
    {
        deleteClassFiles(dst);
        
        File greenfootDir = new File(dst, "greenfoot");
        // the greenfoot dir does not necessarily exist
        if (greenfootDir.canRead()) {
            deleteClassFiles(greenfootDir);
        }
    }

    /**
     * Deletes all class files in the given directory.
     * 
     * @param dir The directory MUST exist
     */
    private static void deleteClassFiles(File dir)
    {
        String[] classFiles = dir.list(classFilter);
        if(classFiles == null) return;
        
        for (int i = 0; i < classFiles.length; i++) {
            String fileName = classFiles[i];
            File file = new File(dir, fileName);
            file.delete();
        }
    }

   
    /**
     * Checks if the project is the default startup project that is used when no
     * other project is open. It is necessary to have this dummy project,
     * becuase we must have a project in order to launch the DebugVM.
     * 
     */
    public static boolean isStartupProject(File blueJLibDir, File projectDir)
    {
        File startupProject = new File(blueJLibDir, "startupProject");
        if (startupProject.equals(projectDir)) {
            return true;
        }

        return false;
    }

    private static Version VERSION = null;
    
    /**
     * Gets the version number of the greenfoot API.
     * 
     * @return
     */
    public static Version getAPIVersion()
    {
        if (VERSION == null) {
            try{
                Class bootCls = Class.forName("bluej.Boot");
                Field field = bootCls.getField("GREENFOOT_API_VERSION");
                String versionStr = (String) field.get(null);
                VERSION = new Version(versionStr);
            }
            catch (ClassNotFoundException e) {
                VERSION = new Version("0");
                //It's fine - running in standalone.
            }
            catch (SecurityException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch (NoSuchFieldException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch (IllegalArgumentException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch (IllegalAccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return VERSION;
    }
    
    public static Class loadAndInitClass(String name)
    {
        return null;
    }
    
    /**
     * See if there is a new class loader in place. If so, we want to
     * clear all views (BlueJ views) which refer to classes loaded by the previous
     * loader.
     */
    private void checkClassLoader()
    {
        ClassLoader newLoader = ExecServer.getCurrentClassLoader();
        if (newLoader != currentLoader) {
            View.removeAll(currentLoader);
            currentLoader = newLoader;
            ObjectTracker.clearRObjectCache();
        }
    }
    
    // ------------ CompileListener interface -------------
        
    public void compileStarted(RCompileEvent event)
    {
        checkClassLoader();
    }
        
    public void compileSucceeded(RCompileEvent event)
    {
        checkClassLoader();
        
    }
    
    public void compileFailed(RCompileEvent event)
    {
        checkClassLoader();
    }
        
    public void compileError(RCompileEvent event) {}

    public void compileWarning(RCompileEvent event){}

}
