/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2010,2011,2012,2013,2014,2015,2016,2017,2018,2019  Michael Kolling and John Rosenberg
 
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
package bluej;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * This class is the BlueJ boot loader. bluej.Boot is the class that should be 
 * started to execute BlueJ. No other external classpath settings are necessary. 
 *
 * This loader finds and loads the known BlueJ classes and sets up the classpath.
 * While doing this, it displays a splash screen.
 *
 * @author  Andrew Patterson
 * @author  Damiano Bolla
 * @author  Michael Kolling
 * @author  Bruce Quig
 */
public class Boot
{

    // The version numbers for BlueJ are changed in the BlueJ build.xml
    // and then the update-version target should be executed.
    public static final int BLUEJ_VERSION_MAJOR = 4;
    public static final int BLUEJ_VERSION_MINOR = 2;
    public static final int BLUEJ_VERSION_RELEASE = 1;
    public static final String BLUEJ_VERSION_SUFFIX = "";

    // public static final int BLUEJ_VERSION_NUMBER = BLUEJ_VERSION_MAJOR * 1000 +
    //                                                BLUEJ_VERSION_MINOR * 100 +
    //                                                BLUEJ_VERSION_RELEASE;

    public static final String BLUEJ_VERSION = BLUEJ_VERSION_MAJOR
                                         + "." + BLUEJ_VERSION_MINOR
                                         + "." + BLUEJ_VERSION_RELEASE
                                         + BLUEJ_VERSION_SUFFIX;

    public static final String BLUEJ_VERSION_TITLE = "BlueJ " + BLUEJ_VERSION;
    
    // The version numbers for Greenfoot are changed in the Greenfoot build.xml
    // and then the update-version target should be executed.
    // Number of jars in above list generated by the BlueJ build process.
    // These can be ignored during development runs (they must be the first
    // jar files  listed in the array).
    private static final int bluejBuildJars = 3;
    // The second group are available to user code (and to bluej)
    // bluejcore.jar is necessary as it contains the support runtime
    // (bluej.runtime.* classes).
    private static final String[] bluejUserJars = { "bluejcore.jar", "junit-4.11.jar", "hamcrest-core-1.3.jar", "lang-stride.jar" };
    // The number of jar files in the user jars which are built from the
    // BlueJ classes directory
    private static final int bluejUserBuildJars = 1;
    // In greenfoot we need access to the BlueJ classes.
    // When running from eclipse, the first jar files will be excluded as explained above at the bluejBuildJars field.
    private static final String JLAYER_MP3_JAR = "jl1.0.1.jar";
    // Jars that should be included with exported scenarios
    public static final String[] GREENFOOT_EXPORT_JARS = {JLAYER_MP3_JAR, "lang-stride.jar"};
    private static final String[] greenfootUserJars = {"extensions" + File.separatorChar + "greenfoot.jar", 
        "bluejcore.jar", "bluejeditor.jar", "bluejext.jar",
        "junit-4.11.jar", "hamcrest-core-1.3.jar", "bluej.jar",
        "classgraph-4.2.6.jar",
        "diffutils-1.2.1.jar", "commons-logging-api-1.1.2.jar",
        JLAYER_MP3_JAR, "opencsv-2.3.jar", "xom-1.2.9.jar",
        "lang-stride.jar",
        "nsmenufx-2.1.4.jar", "richtextfx-fat-0.9.0.jar",
        "guava-17.0.jar",
        "httpclient-4.1.1.jar", "httpcore-4.1.jar", "httpmime-4.1.1.jar"};
    private static final int greenfootUserBuildJars = 4;
    public static String GREENFOOT_VERSION = "3.6.0";
    public static String GREENFOOT_API_VERSION = "3.0.0";
    // A singleton boot object so the rest of BlueJ can pick up args etc.
    private static Boot instance;
    // The jar files we expect in the BlueJ lib directory
    // The first lot are the ones to run BlueJ itself
    private static final String[] bluejJars = { "bluejcore.jar", "bluejeditor.jar", "bluejext.jar",
        "classgraph-4.2.6.jar",
        "commons-logging-api-1.1.2.jar",
        "diffutils-1.2.1.jar",
        "guava-17.0.jar",
        "hamcrest-core-1.3.jar",
        "httpclient-4.1.1.jar",
        "httpcore-4.1.jar",
        "httpmime-4.1.1.jar",
        "jsch-0.1.53.jar",
        "junit-4.11.jar",
        "lang-stride.jar",
        "nsmenufx-2.1.4.jar",
        "org.eclipse.jgit-4.9.0.jar",
        "richtextfx-fat-0.9.0.jar",
        "slf4j-api-1.7.2.jar",
        "slf4j-jdk14-1.7.2.jar",
        "trilead-ssh2-build-217-jenkins-11.jar",
        "xom-1.2.9.jar" };
    // The variable form of the above
    private static String [] runtimeJars = bluejJars;
    private static String [] userJars = bluejUserJars;
    private static int numBuildJars = bluejBuildJars;
    private static int numUserBuildJars = bluejUserBuildJars;
    
    private static String[] javafxJars = new String[] {
        "javafx.base.jar",
        "javafx.controls.jar",
        "javafx.fxml.jar",
        "javafx.graphics.jar",
        "javafx.media.jar",
        "javafx.properties.jar",
        "javafx.swing.jar",
        "javafx.web.jar"
    };
    
    private static boolean isGreenfoot = false;
    private static File bluejLibDir;
    private static final ArrayList<File> macInitialProjects = new ArrayList<>();

    @OnThread(Tag.FXPlatform)
    private SplashWindow splashWindow;
    
    public static String[] cmdLineArgs;      // Command line arguments

    // ---- instance part ----
    private final Properties commandLineProps; //Properties specified a the command line (-....)
    private File javaHomeDir;   // The value returned by System.getProperty
    private ClassLoader bootLoader; // The loader this class is loaded with
    private URL[] runtimeUserClassPath; // The initial class path used to run code within BlueJ
    private URL[] runtimeClassPath;     // The class path containing all the BlueJ classes

    /**
     * Constructor for the singleton Boot object.
     * 
     * @param props the properties (created from the args)
     */
    private Boot(Properties props, final FXPlatformSupplier<Image> image)
    {
        CompletableFuture<Boolean> shown = new CompletableFuture<>();
        // Display the splash window:
        Platform.runLater(() -> {
            splashWindow = new SplashWindow(image.get());
            shown.complete(true);
        });
        try
        {
            shown.get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            // Just ignore it and continue, I guess...
        }

        this.commandLineProps = props;
    }

    /**
     * Entry point for booting BlueJ
     *
     * @param  args  The command line arguments
     */
    public static void main(String[] args)
    {
        cmdLineArgs = args;
        Application.launch(App.class, args);
    }

    public static List<File> getMacInitialProjects()
    {
        return macInitialProjects;
    }

    /**
     * Gets the URLs for JavaFX JARs to put on the classpath
     */
    public URL[] getJavaFXClassPath()
    {
        // Ubuntu names its JARs differently, so the entire set of paths is passed in as a command-line argument:
        String javafxJarsProp = commandLineProps.getProperty("javafxjars", null);
        if (javafxJarsProp != null)
        {
            return Arrays.stream(javafxJarsProp.split(":")).map(s -> {
                try
                {
                    return new File(s).toURI().toURL();
                }
                catch (MalformedURLException e)
                {
                    throw new RuntimeException(e);
                }
            }).toArray(URL[]::new);
        }

        File javafxLibPath = getJavaFXLibDir();

        URL[] urls = new URL[javafxJars.length];
        for (int i = 0; i < javafxJars.length; i++)
        {
            try
            {
                urls[i] = new File(javafxLibPath, javafxJars[i]).toURI().toURL();
            }
            catch (MalformedURLException e)
            {
                throw new RuntimeException(e);
            }
        }
        return urls;
    }

    public File getJavaFXLibDir()
    {
        String javafxPathProp = commandLineProps.getProperty("javafxpath", null);
        File javafxPath;
        if (javafxPathProp != null)
        {
            javafxPath = new File(javafxPathProp);
        } else
        {
            // If no javafxpath property passed, assume JavaFX is bundled
            javafxPath = new File(getBluejLibDir(), "javafx");
        }

        return new File(javafxPath, "lib");
    }

    /**
     * Gets the path to the JavaFX src zip, which may or may not exist.
     * @return
     */
    public File getJavaFXSourcePath()
    {
        File javafxLibPath = getJavaFXLibDir();
        File javafxSrcPath = new File(javafxLibPath, "src.zip");
        return javafxSrcPath;
    }

    @FunctionalInterface
    private static interface FXPlatformSupplier<T>
    {
        @OnThread(Tag.FXPlatform)
        public T get();
    }

    @OnThread(Tag.Any)
    public static void subMain()
    {
        Properties commandLineProps = processCommandLineProperties(cmdLineArgs);
        isGreenfoot = commandLineProps.getProperty("greenfoot", "false").equals("true");
        
        FXPlatformSupplier<Image> image = new FXPlatformSupplier<Image>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public Image get()
            {
                URL url = getClass().getResource(isGreenfoot ? "gen-greenfoot-splash.png" : "gen-bluej-splash.png");
                if (url != null)
                    return new Image(url.toString());
                else
                {
                    // Just use blank
                    WritableImage writableImage = new WritableImage(500, 300);
                    for (int y = 0; y < writableImage.getHeight(); y++)
                    {
                        for (int x = 0; x < writableImage.getWidth(); x++)
                        {
                            writableImage.getPixelWriter().setColor(x, y, javafx.scene.paint.Color.WHITE);
                        }
                    }
                    return writableImage;
                }
            }
        };
        if(isGreenfoot) {
            runtimeJars = greenfootUserJars;
            userJars = greenfootUserJars;
            numBuildJars = greenfootUserBuildJars;
            numUserBuildJars = greenfootUserBuildJars;
        }
        
        try {
            instance = new Boot(commandLineProps, image);
            instance.bootBluej();
        }
        catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
        
        // Make sure we don't return until the VM is exited
        synchronized (instance) {
            while (true) {
                try {
                    instance.wait();
                }
                catch (InterruptedException ie) {}
            }
        }
    }

    /**
     * Returns the singleton Boot instance, so the rest of BlueJ can find paths, args, etc.
     *
     * @return    the singleton Boot object instance
     */
    public static Boot getInstance()
    {
        return instance;
    }

    /**
     * Returns the BlueJ library directory.
     *
     * @return    The bluejLibDir value
     */
    public static File getBluejLibDir()
    {
        if(bluejLibDir == null) {
            bluejLibDir = calculateBluejLibDir();
        }
        return bluejLibDir;
    }

    /**
     * Calculate the bluejLibDir value by doing some reasoning on a resource 
     * we know we have: the .class file for the Boot class.
     * For example:
     * bootUrl=jar:file:/C:/home/bluej/bluej/lib/bluej.jar!/bluej/Boot.class
     * bootFullName=file:/C:/home/bluej/bluej/lib/bluej.jar!/bluej/Boot.class
     * bootName=file:/C:/home/bluej/bluej/lib/bluej.jar
     * finalName=/C:/home/bluej/bluej/lib/bluej.jar
     * Parent=C:\home\bluej\bluej\lib
     *
     * @return    the path of the BlueJ lib directory
     */
    private static File calculateBluejLibDir()
    {
        File bluejDir = null;
        String bootFullName = Boot.class.getResource("Boot.class").toString();

        try {
            if (! bootFullName.startsWith("jar:")) {
                // Boot.class is not in a jar-file. Find a lib directory somewhere
                // above us to use
                File startingDir = (new File(new URI(bootFullName)).getParentFile());
                while((startingDir != null) &&
                        !(new File(startingDir.getParentFile(), "lib").isDirectory())) {
                    startingDir = startingDir.getParentFile();
                }
                
                if (startingDir == null) {
                    bluejDir = null;
                }
                else {
                    bluejDir = new File(startingDir.getParentFile(), "lib");
                }
            }
            else {
                // The class is in a jar file, '!' separates the jar file name
                // from the class name. Cut off the class name and the "jar:" prefix.
                int classIndex = bootFullName.indexOf("!");
                String bootName = bootFullName.substring(4, classIndex);
                
                File finalFile = new File(new URI(bootName));
                bluejDir = finalFile.getParentFile();
            }   
        } 
        catch (URISyntaxException use) { }
        
        return bluejDir;
    }

    /**
     * Looks for a given JAR in the lib folder of the JRE
     * @param name The name of the jar file to look for
     * @return The URL to the jar
     * @throws MalformedURLException 
     */
    public static URL getJREJar(String name) throws MalformedURLException
    {
        String jrePathName = System.getProperty("java.home");

        if (jrePathName != null)
        {
            File jrePath = new File(jrePathName);
            if (jrePath.canRead())
            {
                File jarPath = new File(jrePath, "lib/" + name);
                if (jarPath.canRead())
                {
                    return jarPath.toURI().toURL();
                }
                else
                {
                    System.err.println("Could not find " + name + " at: " + jarPath.getAbsolutePath());
                }
            }
            else
            {
                System.err.println("Could not find JRE at: " + jrePath.getAbsolutePath());
            }
        }
        else
        {
            System.err.println("Could not find Java path");
        }
        return null;
    }

    /**
     * Analyse and process command line specified properties.
     * Properties can be specified with -... command line options. For example: -bluej.debug=true
     * 
     * @param args The command line parameters
     * @return The property object
     */
    private static Properties processCommandLineProperties(String[] args)
    {
        Properties props = new Properties();

        for (String arg : args) {
            if (!arg.startsWith("-")) {
                continue;
            }
            String definition = arg.substring(1);
            int definitionEquals = definition.indexOf('=');
            if (definitionEquals < 0)
                continue;
            String propName = definition.substring(0, definitionEquals); 
            String propValue = definition.substring(definitionEquals+1);
            if (!propName.equals("") && !propValue.equals(""))
                props.put(propName, propValue);
        }
        return props;
    }

    /**
     * Are we a trial (experiment) recording for a Greenfoot user trial?  This should
     * always be hardcoded to false in mainstream Greenfoot releases, and only changed
     * in special releases for running Greenfoot user trials.
     */
    public static boolean isTrialRecording()
    {
        return false;
    }

    /**
     * Hide (and dispose) the splash window
     */
    public void disposeSplashWindow()
    {
        Platform.runLater(() ->
        {
            if (splashWindow != null)
            {
                splashWindow.hide();
                splashWindow = null;
            }
        });
    }

    /**
     * @return True is we're in Greenfoot.
     */
    public boolean isGreenfoot()
    {
        return isGreenfoot;
    }
    
    /**
     * Returns the home directory of the java we have been started with
     *
     * @return    The javaHome value
     */
    public File getJavaHome()
    {
        return javaHomeDir;
    }

    /**
     * Returns the runtime classpath. This contains all the classes for BlueJ.
     *
     * @return    The runtimeClassPath value.
     */
    public URL[] getRuntimeClassPath()
    {
        return runtimeClassPath;
    }

    /**
     * Returns the runtime user classpath. This is available to code within BlueJ.
     *
     * @return    The runtimeUserClassPath value.
     */
    public URL[] getRuntimeUserClassPath()
    {
        return runtimeUserClassPath;
    }
    
    /**
     * Returns the boot class loader, the one that is used to load this class.
     *
     * @return The bootClassLoader value.
     */
    public ClassLoader getBootClassLoader ()
    {
        return bootLoader;
    }

    /**
     * Calculate the various path values, create a new classloader and
     * construct a bluej.Main. This needs to be outside the constructor to
     * ensure that the singleton instance is valid by the time
     * bluej.Main is run.
     */
    @OnThread(Tag.Any)
    private void bootBluej()
    {
        initializeBoot();
        try {
            URLClassLoader runtimeLoader = new URLClassLoader(runtimeClassPath, bootLoader);
 
            // Construct a bluej.Main object. This starts BlueJ "proper".
            Class<?> mainClass = Class.forName("bluej.Main", true, runtimeLoader);
            mainClass.getDeclaredConstructor().newInstance();
            
        } catch (ClassNotFoundException | InstantiationException | NoSuchMethodException 
                | InvocationTargetException | IllegalAccessException exc) {
            throw new RuntimeException(exc);
        }
    }

    private void initializeBoot()
    {
        // Retrieve the current classLoader, this is the boot loader.
        bootLoader = getClass().getClassLoader();

        // Get the home directory of the Java implementation we're being run by
        javaHomeDir = new File(System.getProperty("java.home"));

        try {
            runtimeClassPath = getKnownJars(getBluejLibDir(), runtimeJars, false, numBuildJars);
            runtimeUserClassPath = getKnownJars(getBluejLibDir(), userJars, true, numUserBuildJars);
        }
        catch (Exception exc) {
            exc.printStackTrace();
        }
    }

    /**
     * Returns an array of URLs for all the required BlueJ jars
     *
     * @param libDir  the BlueJ "lib" dir (where the jars are stored)
     * @param jars    the names of the jar files whose urls to add in the
     *                returned list
     * @param isForUserVM  True if any jar files required for the user VM should be included.
     * @param numBuildJars  The number of jar files in the jars array which
     *                  are built from the BlueJ source. If running from eclipse
     *                  these can be replaced with a single entry - the classes
     *                  directory.
     * 
     * @return  URLs of the required JAR files
     * @exception  MalformedURLException  for any problems with the URLs
     */
    private URL[] getKnownJars(File libDir, String[] jars, boolean isForUserVM, int numBuildJars) 
        throws MalformedURLException
    {
        boolean useClassesDir = commandLineProps.getProperty("useclassesdir", "false").equals("true");
        
        // by default, we require all our known jars to be present
        int startJar = 0;
        ArrayList<URL> urlList = new ArrayList<>();

        // a hack to let BlueJ run from within Eclipse.
        // If specified on command line, lets add a ../classes
        // directory to the classpath (where Eclipse stores the
        // .class files)
        if (numBuildJars != 0 && useClassesDir) {
            File classesDir = new File(libDir.getParentFile(), "classes");
            
            if (classesDir.isDirectory()) {
                urlList.add(classesDir.toURI().toURL());
                urlList.add(new File(libDir.getParentFile(), "threadchecker/classes").toURI().toURL());
                if (isGreenfoot) {
                    String gfClassesDir = commandLineProps.getProperty("greenfootclassesdir");
                    if (gfClassesDir != null) {
                        classesDir = new File(gfClassesDir);
                        urlList.add(classesDir.toURI().toURL());
                    }
                }
                
                // skip over requiring bluejcore.jar, bluejeditor.jar etc.
                startJar = numBuildJars;
            }
        }

        for (int i=startJar; i < jars.length; i++) {
            File toAdd = new File(libDir, jars[i]);
            
            // No need to throw exception at this point; we will get
            // a ClassNotFoundException or similar if there is really a
            // problem.
            //if (!toAdd.canRead())
            //    throw new IllegalStateException("required jar is missing or unreadable: " + toAdd);

            if (toAdd.canRead())
                urlList.add(toAdd.toURI().toURL());
        }
    
        if (isForUserVM)
        {
            // Only need to specially add JavaFX for the user VM, it will
            // already be on classpath for server VM:
            urlList.addAll(Arrays.asList(getJavaFXClassPath()));
        }
        return (URL[]) urlList.toArray(new URL[0]);
    }
    
    /**
     * Returns command line specified properties.
     * 
     * <p>Properties can be specified with -... command line options. For example: -bluej.debug=true
     * @return The command line specified properties
     */
    public Properties getCommandLineProperties()
    {
        return commandLineProps;
    }

    public static class App extends javafx.application.Application
    {
        public App()
        {
            if (System.getProperty("os.name").contains("OS X"))
            {
                // Previously, we used this code before launching the application,
                // but it led to AWT being the primary toolkit, not FX:
                /*
                com.apple.eawt.Application macApp = com.apple.eawt.Application.getApplication();
                macApp.setOpenFileHandler(e -> {
                    macInitialProjects.addAll(e.getFiles());
                });
                */
                
                // For now, we use this code to set the event handler, but I think it will
                // stop working come JDK 9.
                // Note: this handler is only used during BlueJ load.  After the load, the open-files
                // events still gets passed back to the com.eawt/AppleJavaExtensions handler, so this
                // won't receive anything after load.  (At some point, the JDK developers are going to have
                // to sort this mess out.)
                com.sun.glass.ui.Application glassApp = com.sun.glass.ui.Application.GetApplication();
                glassApp.setEventHandler(new com.sun.glass.ui.Application.EventHandler() {
                    @Override
                    public void handleOpenFilesAction(com.sun.glass.ui.Application app, long time, String[] files)
                    {
                        // It turns out that we can get a spurious file open event for the Java
                        // classpath.  We spot this and ignore it by looking for colons in the
                        // file path
                        for (String f : files)
                        {
                            if (!f.contains(":") && !f.equals("bluej.Boot") && !f.startsWith("-"))
                                macInitialProjects.add(new File(f));
                        }
                        super.handleOpenFilesAction(app, time, files);
                    }

                    @Override
                    public void handleQuitAction(com.sun.glass.ui.Application app, long time)
                    {
                        getInstance().quitAction.run();
                        super.handleQuitAction(app, time);
                    }
                });
            }
        }
        
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void start(Stage s) throws Exception {
            Platform.setImplicitExit(false);
            s.setTitle("BlueJ");
            new Thread(() -> subMain()).start();
        }
        
    }

    /**
     * We don't want this Boot class to depend on further BlueJ classes, so although
     * Boot needs to know how to quit, we don't want to introduce a compile-time
     * dependency on the classes needed to quit.  So this lambda/Runnable is a late
     * binding for the same purpose
     */
    private Runnable quitAction;

    /**
     * Sets the code to be run (on an arbitrary thread; caller's responsibility to
     * switch threads/avoid deadlocks if needed) once the user triggers the Quit
     * menu command in the editors.
     */
    public void setQuitHandler(Runnable quitAction)
    {
        this.quitAction = quitAction;
    }

}
