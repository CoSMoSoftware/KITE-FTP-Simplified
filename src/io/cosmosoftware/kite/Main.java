package io.cosmosoftware.kite;

import static io.cosmosoftware.kite.Utils.readJsonFile;

import io.cosmosoftware.kite.ftplets.UploadFtplet;
import javax.json.JsonObject;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Main {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      throw new Exception("No config file could be found! Please run with one (java -jar KITE-FTP.jar <path-to-config-file>)");
    }
    JsonObject config = readJsonFile(args[0]);
    // main config for ftp server
    String username = config.getString("username", "cosmouser");
    String password = config.getString("password", "CSmu1;'_");
    String portRange = config.getString("portRange", "60000-60100");
    int port = config.getInt("port", 2221);

    FtpServer server = createServer(port, username, password , portRange);
    server.start();
  }

  private static FtpServer createServer(int port, String username, String password, String ports) throws Exception {
    String homeDirectory = System.getProperty("user.home") + "/ftp";
    File directory = new File(homeDirectory);
    if (!directory.exists()) {
      directory.mkdirs();
    }

    ListenerFactory factory = new ListenerFactory();
    factory.setPort( port );
    DataConnectionConfigurationFactory dataConnectionConfigurationFactory = new DataConnectionConfigurationFactory();
    dataConnectionConfigurationFactory.setPassivePorts(ports);
    factory.setDataConnectionConfiguration(dataConnectionConfigurationFactory.createDataConnectionConfiguration());

    FtpServerFactory serverFactory = new FtpServerFactory();
    serverFactory.addListener( "default", factory.createListener() );

    PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();

    UserManager userManager = userManagerFactory.createUserManager();
    if ( !userManager.doesExist(username) ) {
      BaseUser user = new BaseUser();
      user.setName(username);
      user.setPassword(password);
      user.setEnabled(true);
      user.setHomeDirectory(homeDirectory);
      user.setAuthorities( Collections.<Authority>singletonList(new WritePermission()));
      userManager.save(user);
    }
    Map<String, Ftplet> ftpLets = new HashMap();
    ftpLets.put("ftpService", new UploadFtplet());
    serverFactory.setFtplets(ftpLets);

    serverFactory.setUserManager( userManager );
    return serverFactory.createServer();
  }
}
