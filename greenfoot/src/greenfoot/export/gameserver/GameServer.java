/* Copyright(c) 2005 Sun Microsystems, Inc.  All Rights Reserved. */

package greenfoot.export.gameserver;
import java.io.*;
import java.net.UnknownHostException;

/**
 * ease-of-use class to use to submit games to the game server.
 * Overload the error and status methods to catch messages coming back from the server.
 * The strings from error&status should probably be put into a status line in the
 * application window.  It's pretty easy to make this API stay "hot" and provide continuous
 * status feedback as votes come in.<p>
 * Invoke submit to submit a game to the server.
 * @author James Gosling
 * @created 17 April 2007
 */
public class GameServer {
    
    private ClientEnd server; // = new ClientEnd("bluej.org"); //"mygame.java.sun.com");
    
    /**
     * Submit a file to the server.
     * 
     * @throws UnknownHostException If the host can't be found.
     * @throws IOException If there was an error connecting to the host.
     */
    public final GameServer submit(String host, String uid, String password, String gameName, String fileName) throws UnknownHostException, IOException {
        server = new ClientEnd(host);
        server.getOut().writeln("t", "submit", uid, password, gameName, getfile(fileName)).flush();        
        return this;
    }
    public final void close() {
        if(server!=null) {
            server.setState(null,"closed");
            server = null;
        }
    }
    public final GameServer requestStatus(String uid, String gameName) {
        try {
            server.getOut()
                .writeln("t","getstatus",uid,gameName)
                .flush();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
        return this;
    }
    public class ClientEnd extends Endpoint {
        public ClientEnd(String host) throws UnknownHostException {
            super(host,4235,false,null);
        }
        public void hello(UTCL utcl) {
            //System.err.println("***Hello from the client!  "+utcl);
        }
        public void answer(UTCL utcl) {
            //System.err.println("***Answer received by the client!  "+utcl);
        }
        public void status(UTCL utcl) {
            GameServer.this.status(utcl.getArg(0,"status reply without message"));
        }
        public void error(UTCL utcl) {
            GameServer.this.error(utcl.getArg(0,"error without message"));
        }
    }
    /** Override this to get error messages */
    public void error(String s) {
        System.err.println("Error: "+s);
    }
    /** Override this to get status messages */
    public void status(String s) {
        System.err.println("Status: "+s);
    }
    private static byte[] getfile(File fn) throws IOException {
        InputStream in = new FileInputStream(fn);
        byte[] ret = new byte[in.available()];
        in.read(ret);
        in.close();
        return ret;
    }
    private static byte[] getfile(String fn) throws IOException {
        return getfile(new File(fn));
    }
    
}
