package bluej.groupwork.actions;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.util.Set;

import javax.swing.AbstractAction;

import bluej.Config;
import bluej.groupwork.Repository;
import bluej.groupwork.TeamUtils;
import bluej.groupwork.TeamworkCommand;
import bluej.groupwork.TeamworkCommandResult;
import bluej.groupwork.ui.CommitCommentsFrame;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;
import bluej.utility.SwingWorker;


/**
 * An action to do an actual commit. By this stage we know what we are
 * committing and have the commit comments.
 * 
 * @author Kasper
 * @version $Id: CommitAction.java 5067 2007-05-28 04:15:57Z bquig $
 */
public class CommitAction extends AbstractAction
{
    private Set newFiles; // which files are new files
    private Set deletedFiles; // which files are to be removed
    private Set files; // files to commit (includes both of above)
    private CommitCommentsFrame commitCommentsFrame;
    
    private CommitWorker worker;
    
    public CommitAction(CommitCommentsFrame frame)
    {
        super(Config.getString("team.commit"));
        commitCommentsFrame = frame; 
    }
    
    /**
     * Set the files which are new, that is, which aren't presently under
     * version management and which need to be added.
     */
    public void setNewFiles(Set newFiles)
    {
        this.newFiles = newFiles;
    }
    
    /**
     * Set the files which have been deleted locally, and the deletion
     * needs to be propagated to the repository.
     */
    public void setDeletedFiles(Set deletedFiles)
    {
        this.deletedFiles = deletedFiles;
    }
    
    /**
     * Set all files which are to be committed. This should include both
     * the new files and the deleted files, as well as any other files
     * which have been locally modified and need to be committed.
     */
    public void setFiles(Set files)
    {
        this.files = files;
    }
    
    /**
     * accessor for combined list of new, deleted and modified files
     */
    public Set getFiles()
    {
        return files;
    }
    
    /* (non-Javadoc)
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    public void actionPerformed(ActionEvent event) 
    {
        Project project = commitCommentsFrame.getProject();
        
        if (project != null) {
            commitCommentsFrame.startProgress();
            PkgMgrFrame.displayMessage(project, Config.getString("team.commit.statusMessage"));
            project.saveAllEditors();
            setEnabled(false);
            
            //doCommit(project);
            worker = new CommitWorker(project);
            worker.start();
        }
    }
    
    /**
     * Cancel the commit, if it is running.
     */
    public void cancel()
    {
        setEnabled(true);
        if(worker != null) {
            worker.abort();
            worker = null;
        }
    }

    /**
     * Worker thread to perform commit operation
     * 
     * @author Davin McCall
     */
    private class CommitWorker extends SwingWorker
    {
        private Repository repository;
        private TeamworkCommand command;
        private TeamworkCommandResult result;
        private boolean aborted;
        
        public CommitWorker(Project project)
        {
            repository = project.getRepository();
            String comment = commitCommentsFrame.getComment();

            //last step before committing is to add in modified diagram 
            //layouts if selected in commit comments dialog
            if(commitCommentsFrame.includeLayout()) {
                files.addAll(commitCommentsFrame.getChangedLayoutFiles());
            }

            Set binFiles = TeamUtils.extractBinaryFilesFromSet(newFiles);

            // Note, getRepository() cannot return null here - otherwise
            // the commit dialog was cancelled (and we'd never get here)
            command = project.getRepository().commitAll(newFiles, binFiles, 
                    deletedFiles, files, comment);
        }
        
        public Object construct()
        {
            result = command.getResult();
            return result;
        }
        
        public void abort()
        {
            command.cancel();
            aborted = true;
        }
        
        public void finished()
        {
            final Project project = commitCommentsFrame.getProject();
            
            if (! aborted) {
                commitCommentsFrame.stopProgress();
                if (! result.isError() && ! result.wasAborted()) {
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            PkgMgrFrame.displayMessage(project, Config.getString("team.commit.statusDone"));
                        }
                    });
                }
            }

            TeamUtils.handleServerResponse(result, commitCommentsFrame);
            
            if (! aborted) {
                setEnabled(true);
                commitCommentsFrame.setVisible(false);
            }
        }
    }
}
