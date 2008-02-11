package bluej.groupwork;

import java.io.*;
import java.util.*;

import bluej.Config;
import bluej.groupwork.ui.TeamSettingsDialog;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;
import bluej.utility.Debug;


/**
 * This class is responsible for reading and writing the configuration files
 * regarding teamwork settings. The files are team.defs, which is located in
 * the top-level folder of a team project, and the bluej.properties
 *
 * @author fisker
 * @version $Id: TeamSettingsController.java 5547 2008-02-11 11:53:24Z davmac $
 */
public class TeamSettingsController
{
    private static ArrayList teamProviders;
    static {
        teamProviders = new ArrayList(2);
        try {
            teamProviders.add(new CvsProvider());
        }
        catch (Throwable e) {
            Debug.message("Failed to initialize Cvs: " + e.getClass().getName()
                    + ": "+ e.getLocalizedMessage());
        }
        try {
            teamProviders.add(new SubversionProvider());
        }
        catch (Throwable e) {
            Debug.message("Failed to initialize Subversion: " + e.getClass().getName()
                    + ": "+ e.getLocalizedMessage());
        }
    }
    
    private Project project;
    private File projectDir;
    private Properties teamProperties;
    private TeamSettingsDialog teamSettingsDialog;
    private TeamSettings settings;
    
    //general
    private String password;

    private File teamdefs;
    
    // repository
    private Repository repository;
    
    /**
     * Construct a team settings controller for the given project.
     */
    public TeamSettingsController(Project project)
    {
        this.project = project;
        this.projectDir = project.getProjectDir();
        teamProperties = new Properties();
        readSetupFile();
    }

    /**
     * Construct a team settings controller, not associated with
     * any project initially. The supplied projectDir need not be the
     * final project directory - it is just used as a working location
     * until the project is set.
     */
    public TeamSettingsController(File projectDir)
    {
        this.projectDir = projectDir;
        teamProperties = new Properties();
    }

    /**
     * Assign this team settings controller to a particular project.
     * Once this is done, the repository settings can no longer be
     * changed.
     */
    public void setProject(Project proj)
    {
        project = proj;
        projectDir = proj.getProjectDir();
        repository = null;
        checkTeamSettingsDialog();
    }
    
    /**
     * Get a list of the teamwork providers (CVS, Subversion).
     */
    public List getTeamworkProviders()
    {
        return teamProviders;
    }
    
    /**
     * Get the repository. Returns null if user credentials are required
     * but the user chooses to cancel.
     */
    public Repository getRepository()
    {
        if (password == null) {
            // If we don't yet know the password, prompt the user
            getTeamSettingsDialog().doTeamSettings();

            // If we still don't know it, user cancelled
            if (password == null) {
                return null;
            }
            
            TeamSettings settings = teamSettingsDialog.getSettings();
            if (repository == null) {
                repository = settings.getProvider().getRepository(projectDir, settings);
            }
            else {
                repository.setPassword(settings);
            }
        }
        else {
            // We might have the password, but not yet have created
            // the repository
            if (repository == null) {
                repository = settings.getProvider().getRepository(projectDir, settings);
            }
        }
        
        return repository;
    }
    
    /**
     * Initialize the repository. This doesn't require the password to be entered
     * or the team settings dialog to be displayed.
     */
    private void initRepository()
    {
        if (repository == null) {
            TeamworkProvider provider = settings.getProvider();
            repository = provider.getRepository(projectDir, settings);
        }
    }
    
    /**
     * Get a list of files (and possibly directories) in the project which should be
     * under version control management. This includes files which have been locally
     * deleted since the last commit.
     * 
     * @param includeLayout  indicates whether to include the layout (bluej.pkg) files.
     * (Note that locally deleted bluej.pkg files are always included).
     */
    public Set getProjectFiles(boolean includeLayout)
    {
        initRepository(); // make sure the repository is constructed
        
        boolean versionsDirs = false;
        if (repository != null) {
            versionsDirs = repository.versionsDirectories();
        }
        
        // Get a list of files to commit
        Set files = project.getFilesInProject(includeLayout, versionsDirs);
        
        if (repository != null) {
            repository.getAllLocallyDeletedFiles(files);
        }
        
        return files;
    }
    
    /**
     * Get a filename filter suitable for filtering out files which we don't want
     * to be under version control.
     */
    public FileFilter getFileFilter(boolean includeLayout)
    {
        initRepository();
        FileFilter repositoryFilter = null;
        if (repository != null) {
            repositoryFilter = repository.getMetadataFilter();
        }
        return new CodeFileFilter(getIgnoreFiles(), includeLayout, repositoryFilter);
    }
    
    /**
     * Read the team setup file in the top level folder of the project
     */
    private void readSetupFile()
    {
        teamdefs = new File(projectDir, "team.defs");

        try {
            teamProperties.load(new FileInputStream(teamdefs));
            if (teamProperties.getProperty("bluej.teamsettings.vcs") == null) {
                // old project from before Subversion support, was using CVS
                teamProperties.setProperty("bluej.teamsettings.vcs", "cvs");
            }
            
            initSettings();
        }
        catch (FileNotFoundException e) {
            // e.printStackTrace();
            // This is allowed to happen - if a non-shared project becomes
            // shared
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initSettings()
    {
        String user = getPropString("bluej.teamsettings.user");
        if (user == null) {
            user = "";
        }
        String group = getPropString("bluej.teamsettings.groupname");
        if(group == null) {
            group = "";
        }
//        String useAsDefault = teamSettingsController.getPropString("bluej.teamsettings.useAsDefault");
//        if (useAsDefault != null) {
//            setUseAsDefault(Boolean.getBoolean(useAsDefault));
//        }
        
        TeamworkProvider provider = null;
        String providerName = getPropString("bluej.teamsettings.vcs");
        if (providerName != null) {
            for (int index = 0; index < teamProviders.size(); index++) {
                TeamworkProvider prov = (TeamworkProvider) teamProviders.get(index);
                if (prov.getProviderName().equalsIgnoreCase(providerName)) {
                    provider = prov;
                }
            }
        }
        
        if (provider != null) {
            settings = initProviderSettings(user, group, password, provider);
        }
    }
    
    public TeamSettings initProviderSettings(String user, String group, String password,
            TeamworkProvider provider) {
        
        String keyBase = "bluej.teamsettings."
            + provider.getProviderName().toLowerCase() + "."; 
        
        String prefix = getPropString(keyBase + "repositoryPrefix");
        String server = getPropString(keyBase + "server");
        
        String protocol = getPropString(keyBase + "protocol");

        return new TeamSettings(provider, protocol, server, prefix, group, user, password);
    }
    
    /**
     * Prepare for the deletion of a directory. For CVS, this involves moving
     * the metadata elsewhere. Returns true if the directory should actually
     * be deleted, or false if the version control system will delete it either
     * immediately or at commit time.
     */
    public boolean  prepareDeleteDir(File dir)
    {
        return getRepository().prepareDeleteDir(dir);
    }
    
    /**
     * Prepare a newly created directory for version control.
     */
    public void prepareCreateDir(File dir)
    {
        getRepository().prepareCreateDir(dir);
    }

    /**
     * Get the team settings dialog to edit these team settings.
     */
    public TeamSettingsDialog getTeamSettingsDialog()
    {
        if (teamSettingsDialog == null) {
            teamSettingsDialog = new TeamSettingsDialog(this);
            teamSettingsDialog.setLocationRelativeTo(PkgMgrFrame.getMostRecent());
            checkTeamSettingsDialog();
        }
        
        return teamSettingsDialog;
    }
    
    /**
     * Disable the repository fields in the team settings dialog if
     * we have a project attached.
     */
    private void checkTeamSettingsDialog()
    {
        if (teamSettingsDialog != null && project != null) {
            // We have a project, which means we have an established
            // repository. It shouldn't be changed now.
            teamSettingsDialog.disableRepositorySettings();
        }
    }
    
    /**
     * Write the settings to team.defs in the project. It no project is known,
     * nothing happens. Note that nothing is written to bluej.properties. That
     * is handled by the Config class.
     *
     */
    public void writeToProject()
    {
        if (projectDir == null) {
            return;
        }

        File cfgFile = new File(projectDir + "/team.defs");

        if (!cfgFile.exists()) {
            addIgnoreFilePatterns(teamProperties);
        }

        try {
            teamProperties.store(new FileOutputStream(cfgFile), null);
            repository = null;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add to the team properties the files we wish to ignore, like class files
     * and ctxt files
     * @param teamProperties
     */
    private void addIgnoreFilePatterns(Properties teamProperties)
    {
        teamProperties.put("bluej.teamsettings.ignore1", ".*\\.class");
        teamProperties.put("bluej.teamsettings.ignore2", "bluej\\.pkh");
        teamProperties.put("bluej.teamsettings.ignore3", "team\\.defs");
        teamProperties.put("bluej.teamsettings.ignore4", ".*\\.ctxt");
        teamProperties.put("bluej.teamsettings.ignore5", ".*\\~");
        teamProperties.put("bluej.teamsettings.ignore6", ".*\\#");
        teamProperties.put("bluej.teamsettings.ignore7", ".*\\#backup");
        teamProperties.put("bluej.teamsettings.ignore8", "\\.DS_Store");
    }

    /**
     * get the property by the name strname. If the property is present in
     * the project, that value is returned. If not, bluej.properties and then
     * bluej.defs are searched. If not found, null is returned.
     * @param strname
     * @return
     */
    public String getPropString(String strname)
    {
        String result = teamProperties.getProperty(strname);

        if (result != null) {
            return result;
        }

        result = Config.getPropString(strname, null);

        return result;
    }

    public void setPropString(String key, String value)
    {
        teamProperties.setProperty(key, value);
    }
    
    public void updateSettings(TeamSettings newSettings, boolean useAsDefault)
    {
        settings = newSettings;
        
        String userKey = "bluej.teamsettings.user";
        String userValue = settings.getUserName();
        setPropString(userKey, userValue);

        String providerKey = "bluej.teamsettings.vcs";
        
        String providerName = newSettings.getProvider()
                .getProviderName().toLowerCase();
        setPropString(providerKey, providerName);
        
        String keyBase = "bluej.teamsettings."
                + providerName + ".";
        String serverKey = keyBase + "server";
        String serverValue = settings.getServer();
        setPropString(serverKey, serverValue);

        String prefixKey = keyBase + "repositoryPrefix";
        String prefixValue = settings.getPrefix();
        setPropString(prefixKey, prefixValue);

        String protocolKey = keyBase + "protocol";
        String protocolValue = settings.getProtocol();
        setPropString(protocolKey, protocolValue);

        String groupKey = "bluej.teamsettings.groupname";
        String groupValue = settings.getGroup();
        setPropString(groupKey,  groupValue);

        String useAsDefaultKey = "bluej.teamsettings.useAsDefault";
        Config.putPropString(useAsDefaultKey,
            Boolean.toString(useAsDefault));

        // passwords are handled differently for security reasons,
        // we don't at present store them on disk
        String passValue = settings.getPassword();
        setPasswordString(passValue);
        
        if (repository != null) {
            TeamSettings settings = getTeamSettingsDialog().getSettings();
            repository.setPassword(settings);
        }
        
        if (useAsDefault) {
            Config.putPropString(providerKey, providerName);
            Config.putPropString(userKey, userValue);
            Config.putPropString(serverKey, serverValue);
            Config.putPropString(prefixKey, prefixValue);
            Config.putPropString(groupKey, groupValue);
            Config.putPropString(protocolKey, protocolValue);
        }
    }

    /**
     * In the first instance we don't want to store password.
     * We want to ask the first time they want to try and perform operation
     * We then store for the rest of the session. Over time we may want to provide
     * some way of storing with relative security.
     */
    public String getPasswordString()
    {
        return password;
    }

    private void setPasswordString(String password)
    {
        this.password = password;
    }

    public boolean hasPasswordString()
    {
        return password != null;
    }

    /**
     * gets the regular expressions in string form for the files we should ignore
     * @return List containing Strings
     */
    public List getIgnoreFiles()
    {
        Enumeration keys = teamProperties.keys();
        List patterns = new LinkedList();

        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();

            // legacy settings
            if (key.startsWith("bluej.teamsettings.cvs.ignore")) {
                patterns.add(teamProperties.getProperty(key));
            }
            
            // new settings
            if (key.startsWith("bluej.teamsettings.ignore")) {
                patterns.add(teamProperties.getProperty(key));
            }
        }

        return patterns;
    }

    public boolean hasProject()
    {
        return project != null;
    }
    
    public Project getProject()
    {
        return project;
    }
}
