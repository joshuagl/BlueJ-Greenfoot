package bluej.utility;

import bluej.Config;
import bluej.pkgmgr.Package;

import java.awt.Component;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.io.*;
import java.util.*;
import java.lang.reflect.Array;

/**
 * A file utility for various file related actions.
 *
 * @author  Markus Ostman
 * @author  Michael Kolling
 * @version $Id: FileUtility.java 734 2000-12-19 04:49:28Z ajp $
 */
public class FileUtility
{
    private static final String sourceSuffix = ".java";
    private static final String contextSuffix = ".ctxt";
    private static final String packageFilePrefix = "bluej.pk";

    private static JFileChooser pkgChooser = null;
    private static JFileChooser fileChooser = null;

    //========================= STATIC METHODS ============================

    public static File getPackageName(Component parent)
    {
        JFileChooser chooser = getPackageChooser();

        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile();
    }


    /**
     *  Get a file name from the user, using a file selection dialogue.
     *  If cancelled or an invalid name was specified, return null.
     */
    public static String getFileName(Component parent, String title,
                                     String buttonLabel, boolean directoryOnly,
                                     FileFilter filter)
    {
        JFileChooser newChooser = getFileChooser();

        newChooser.setDialogTitle(title);

        if (directoryOnly)
            newChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        else
            newChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        if(filter == null)
            filter = newChooser.getAcceptAllFileFilter();
        newChooser.setFileFilter(filter);

        int result = newChooser.showDialog(parent, buttonLabel);

        if (result == JFileChooser.APPROVE_OPTION)
            return newChooser.getSelectedFile().getPath();
        else if (result == JFileChooser.CANCEL_OPTION)
            return null;
        else {
            DialogManager.showError(parent, "error-no-name");
            return null;
        }
    }


    public static String getFileName(Component parent, String title,
                                     String buttonLabel, boolean directoryOnly)
    {
        return getFileName(parent, title, buttonLabel, directoryOnly, null);
    }


    public static FileFilter getJavaSourceFilter()
    {
        return new JavaSourceFilter();
    }


    /**
     * Return a BlueJ package chooser, i.e. a file chooser which
     * recognises BlueJ packages and treats them differently.
     */
    private static JFileChooser getPackageChooser()
    {
        if(pkgChooser == null)
            pkgChooser = new PackageChooser(
                           Config.getPropString("bluej.defaultProjectPath",
                                                "."));
        return pkgChooser;
    }


    /**
     * return a file chooser for choosing any directory (default behaviour)
     */
    public static JFileChooser getFileChooser()
    {
        if(fileChooser == null) {
            fileChooser = new BlueJFileChooser(
                            Config.getPropString("bluej.defaultProjectPath",
                            "."));
        }

        return fileChooser;
    }


    private static class JavaSourceFilter extends FileFilter
    {
        /**
         * This method only accepts files that are Java source files.
         * Whether a file is a Java source file is determined by the fact that
         * its filename ends with ".java".
         */
        public boolean accept(File pathname)
        {
            if (pathname.isDirectory() ||
                pathname.getName().endsWith(sourceSuffix))
                   return true;
            else
                return false;
        }

        public String getDescription()
        {
            return "Java Source";
        }
    }


    /**
     * Copy file 'source' to file 'dest'. The source file must exist,
     * the destination file will be created. Returns true if successful.
     */
    public static boolean copyFile(String source, String dest)
    {
        File srcFile = new File(source);
        File destFile = new File(dest);

        return copyFile(srcFile, destFile);
    }


    /**
     * Copy file 'srcFile' to file 'destFile'. The source file must exist,
     * the destination file will be created. Returns true if successful.
     */
    public static boolean copyFile(File srcFile, File destFile)
    {
        // check whether source and dest are the same
        if(srcFile.equals(destFile)) {
            return true;  // don't bother - they are the same
        }

        InputStream in = null;
        OutputStream out = null;
        try {
            in = new BufferedInputStream(new FileInputStream(srcFile));
            out = new BufferedOutputStream(new FileOutputStream(destFile));
            copyStream(in, out);
            return true;
        } catch(IOException e) {
            return false;
        } finally {
            try {
                if(in != null)
                    in.close();
                if(out != null)
                    out.close();
            } catch (IOException e) {}
        }
    }


    /**
     * Copy stream 'in' to stream 'out'.
     */
    public static void copyStream(InputStream in, OutputStream out)
        throws IOException
    {
        for(int c; (c = in.read()) != -1; )
            out.write(c);
    }


    /**
     * Copy (recursively) a whole directory.
     */
    public static final int NO_ERROR = 0;
    public static final int DEST_EXISTS = 1;
    public static final int SRC_NOT_DIRECTORY = 2;
    public static final int COPY_ERROR = 3;

    public static int copyDirectory(String source, String dest,
                                    boolean excludeBlueJ,
                                    boolean excludeSource)
    {
        File srcFile = new File(source);
        File destFile = new File(dest);

        if(!srcFile.isDirectory())
            return SRC_NOT_DIRECTORY;

        if(destFile.exists())
            return DEST_EXISTS;

        if(!destFile.mkdir())
            return COPY_ERROR;

        String[] dir = srcFile.list();
        for(int i=0; i<dir.length; i++) {
            String srcName = source + File.separator + dir[i];
            File file = new File(srcName);
            if(file.isDirectory()) {
                if(copyDirectory(srcName, dest + File.separator + dir[i],
                                 excludeBlueJ, excludeSource) != NO_ERROR)
                    return COPY_ERROR;
            }
            else {
                if(!skipFile(dir[i], excludeBlueJ, excludeSource)) {
                    File file2 = new File(dest, dir[i]);
                    if(!copyFile(file, file2))
                        return COPY_ERROR;
                }
            }
        }
        return NO_ERROR;
    }


    /**
     * Checks whether a file should be skipped during a copy operation.
     * You can specify to skip BlueJ specific files and/or Java source
     * files.
     */
    public static boolean skipFile(String fileName,
                            boolean skipBlueJ, boolean skipSource)
    {
        if(skipBlueJ)
            if(fileName.startsWith(packageFilePrefix) ||
               fileName.endsWith(contextSuffix))
                return true;

        if(skipSource)
            if(fileName.endsWith(sourceSuffix))
                return true;

        return false;
    }


    /**
     * Recursively copy all files from one directory to another.
     *
     * @return An array contained each source file which was
     *         not successfully copied or null if everything went well
     */
    public static Object[] recursiveCopyFile(File srcDir, File destDir)
    {
        // remember every file which we don't successfully copy
        List failed = new ArrayList();

        // check whether source and dest are the same
        if(srcDir.getAbsolutePath().equals(destDir.getAbsolutePath()))
            return null;

        if (!srcDir.isDirectory() || !destDir.isDirectory())
            throw new IllegalArgumentException();

        // get all entities in the source directory
        File[] files = srcDir.listFiles();

        for (int i=0; i < files.length; i++) {
            // handle directories by recursively copying
            if (files[i].isDirectory()) {

                File newDir = new File(destDir, files[i].getName());

                newDir.mkdir();

                if (newDir.isDirectory()) {
                    recursiveCopyFile(files[i], newDir);
                }
                else {
                    failed.add(files[i]);
                }
            }
            else if(files[i].isFile()) {
                // handle all other files
                File newFile = new File(destDir, files[i].getName());

                if(newFile.exists() || !copyFile(files[i], newFile))
                    failed.add(files[i]);
            }
        }

        if (failed.size() > 0)
            return failed.toArray();
        else
            return null;
    }


    /**
     * Find a file with a given extension in a given directory or any
     * subdirectory. Returns just one randomly selected file with that
     * extension.
     *
     * @return   a file with the given extension in the given directory,
     *           or 'null' if such a file cannot be found.
     */
    public static File findFile(File startDir, String suffix)
    {
        File[] files = startDir.listFiles();

        // look for files here
        for (int i=0; i < files.length; i++) {
            if(files[i].isFile()) {
                if(files[i].getName().endsWith(suffix))
                    return files[i];
            }
        }

        // if we didn't find one, search subdirectories
        for (int i=0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                File found = findFile(files[i], suffix);
                if(found != null)
                    return found;
            }
        }
        return null;
    }


    /**
     * Check whether a given directory contains a file with a given suffix.
     * The search is NOT recursive.
     *
     * @return  true if a file with the given suffix exists in the given
     *          directory.
     */
    public static boolean containsFile(File dir, String suffix)
    {
        File[] files = dir.listFiles();

        for (int i=0; i < files.length; i++) {
            if(files[i].isFile() && files[i].getName().endsWith(suffix))
                return true;
        }

        return false;
    }


    /**
     * Delete a directory recursively.
     * This method will delete all files and subdirectories in any
     * directory without asking questions. Use with care.
     *
     * @param directory   The directory that will be deleted.
     *
     */
    public static void deleteDir(File directory)
    {
        File[] fileList = directory.listFiles();

        //If it is a file or an empty directory, delete
        if(fileList == null || Array.getLength(fileList) == 0){
            try{
                directory.delete();
            }catch (SecurityException se){
                Debug.message("Trouble deleting: "+directory+se);
            }
        }
        else{
            //delete all subdirectories
            for(int i=0;i<Array.getLength(fileList);i++){
                deleteDir(fileList[i]);
            }
            //then delete the directory (when it is empty)
            try{
                directory.delete();
            }catch (SecurityException se){
                Debug.message("Trouble deleting: "+directory+se);
            }
        }
    }
}
