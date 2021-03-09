package io.cosmosoftware.kite.ftplets;
import io.cosmosoftware.kite.Utils;
import org.apache.ftpserver.ftplet.*;

import java.io.File;
import java.io.IOException;
import static io.cosmosoftware.kite.Utils.generateAllureReport;
public class UploadFtplet extends DefaultFtplet {

  @Override
  public FtpletResult onUploadStart(FtpSession session, FtpRequest request)
          throws FtpException, IOException {
    String size = request.getArgument().split("-size.")[1];
    size = size.split(".zip")[0];
    if (!Utils.storageCheck(size)) {
      return FtpletResult.DISCONNECT;
    }
    return super.onUploadStart(session, request);
  }


  @Override
  public FtpletResult onUploadEnd(FtpSession session, FtpRequest request)
          throws FtpException, IOException {

    String path = session.getUser().getHomeDirectory();
    String filename = request.getArgument();
    String unzipDirectory;
    String folderName;
    if(filename.contains("-allure")) {
      folderName = filename.split("-allure")[0];
      unzipDirectory = path + "/tmp/" + folderName;
    } else {
      folderName = filename.split("-logs")[0];
      unzipDirectory = path + "/kite-logs/" + folderName;
    }
    Utils.unzip(path + "/" + filename , unzipDirectory);
    try {
      if(filename.contains("-allure")) {
        generateAllureReport(folderName, unzipDirectory);
      }
    } catch (Exception e) {
      System.out.println("Could not process " + filename + ": ");
      e.printStackTrace();
    } finally{
      try {
        new File(path + "/" + filename).delete();
        Utils.deleteDirectory(new File(unzipDirectory));
      } catch (Exception ex) {
        System.out.println("Could not delete uploaded file " + filename + ": " + ex.getLocalizedMessage());
      }
    }
    return super.onUploadEnd(session, request);
  }

}