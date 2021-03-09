package io.cosmosoftware.kite;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {
  public static final int SIZE_MULTIPLIER = 5;
  public static final long FIVE_GB = 5368709120L;
  public Utils() {

  }

  public static boolean deleteDirectory(File directoryToBeDeleted) {
    File[] allContents = directoryToBeDeleted.listFiles();
    if (allContents != null) {
      for (File file : allContents) {
        deleteDirectory(file);
      }
    }
    return directoryToBeDeleted.delete();
  }


  public static void unzip(String filename, String outputDirectory) throws IOException {
    byte[] buffer = new byte[2048];
    File testFile = new File(outputDirectory);
    if (!testFile.exists()) {
      testFile.mkdirs();
    }
    File theFile = new File(filename);
    ZipInputStream stream = new ZipInputStream(new FileInputStream(theFile));
    try {
      ZipEntry entry;
      while ((entry = stream.getNextEntry()) != null) {
        String outpath = outputDirectory + "/" + entry.getName();
        FileOutputStream output = null;
        if (entry.getName().endsWith("/")) {
          File x = new File(outpath);
          if (x.exists()) {
            FileUtils.forceDelete(x);
          }
          x.mkdir();
        } else {
          try {
            output = new FileOutputStream(outpath);
            int len = 0;
            while ((len = stream.read(buffer)) > 0) {
              output.write(buffer, 0, len);
            }
          } finally {
            if (output != null) output.close();
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      stream.close();
    }
  }

  public static String executeCommand(String[] command)
          throws IOException, InterruptedException, IllegalArgumentException {
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.redirectErrorStream(true);
    System.out.println(new Date() + "*** Executing: ");
    for (String component : command) {
      System.out.print(component + " ");
    }
    Process process = processBuilder.start();
    String line;
    BufferedReader stdInput = null;
    boolean error = false;
    try {

      if (process.getInputStream().available() > 0) {
        stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
      } else {
        stdInput = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      }
      while ((line = stdInput.readLine()) != null) {
        line = String.format(command[0] + " stdout: %s\n", line);
//        System.out.println(line);
        if (line.toLowerCase().contains("fail")
                || line.toLowerCase().contains("fatal")
                || line.toLowerCase().contains("error")) {
          error = true;
        }
      }
    } catch (IOException e) {
       System.out.println("Exception while reading the Input Stream: " +  e.getLocalizedMessage());
    } finally {
      if (stdInput != null) {
        try {
          stdInput.close();
        } catch (IOException e) {
        }
      }
    }
    if (error) {
      throw new IllegalArgumentException(
              "Something is wrong with the command execution, please check the log file for more details");
    }
    String output = buildOutput(process, null, null);
    return output;
  }

  public static String buildOutput(Process process, List<String> stringList, String filter) {
    Scanner scanner = new Scanner(process.getInputStream());

    StringBuilder builder = new StringBuilder();
    System.out.println("*** BEGIN OUTPUT ***");
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      System.out.println(line);
      builder.append(line);
      if (stringList != null) {
        if (filter == null) {
          stringList.add(line);
        } else if (line.startsWith(filter)) {
          stringList.add(line);
        }
      }
    }
    System.out.println("*** END OUTPUT ***");
    scanner.close();

    return builder.toString();
  }

  public static String generateAllureReport(String testId, String unzipDirectory)
          throws IOException, InterruptedException {
    String[] command;
    String[] chmodCommand = null;
    String allureDirectory = isWindowsBased() ? ("C:\\nginx\\html\\allure\\" + testId) : ("/var/www/allure/" + testId);
    if (isWindowsBased()) {
      command = new String[]{"cmd.exe", "/C", "allure", "generate", unzipDirectory, "--output", allureDirectory, "--clean"};
    } else if (isLinuxBased()) {
      command = new String[]{"allure", "generate", unzipDirectory, "--output", allureDirectory, "--clean"};
      // give nginx access
      chmodCommand = new String[]{"chmod", "-R", "777" , allureDirectory};
    } else {
      throw new UnsupportedOperationException("Only Windows and Linux are supported");
    }
    Utils.executeCommand(command);
    if (chmodCommand != null) {
      Utils.executeCommand(chmodCommand);
    }
    return allureDirectory;
  }

  public static void archiveRecord(String testId) {
    try {
      String allureDirectory = isWindowsBased() ? ("C:\\nginx\\html\\allure\\" + testId) : ("/var/www/allure/" + testId);
      String logFilePath =
          isWindowsBased()
              ? ("C:\\nginx\\html\\kite-logs\\" + testId + "\\" + testId + ".log")
              : ("/var/www/kite-logs/" + testId + "/" + testId + ".log");


      String archivedDirectory =
          isWindowsBased() ? ("C:\\nginx\\html\\allure\\archives\\" + testId) : ("/var/www/allure/archives/" + testId);

      // clean up archive folder if exist, keep 5 most recent records
      File archive = new File(archivedDirectory);
      if (archive.exists()) {
        File[] archivedRecords = archive.listFiles();
        if (archivedRecords != null) {
          Arrays.sort(archivedRecords, Comparator.comparingLong(File::lastModified));
          int toDelete = archivedRecords.length - 5;
          while (toDelete > 0) {
            System.out.println("Cleaning up old record " + archivedRecords[toDelete].getPath());
            FileUtils.deleteDirectory(archivedRecords[toDelete]);
            toDelete --;
          }
        }
      }
      // moving record to archive with timestamp
      FileTime creationDate = getFileCreationDate(allureDirectory);
      String pattern = "yyyy-MM-dd_hh-mm-ss";
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
      String lastModifiedTimeStamp = simpleDateFormat.format(new Date(creationDate.toMillis()));
      File destDir = new File(archivedDirectory + "/" + (testId + " " + lastModifiedTimeStamp));
      System.out.println("Moving " + testId + " to archives -> " + destDir.getPath());
      FileUtils.moveDirectory(new File(allureDirectory), destDir);
      // move the log to the new archived folder as well
      if (new File(logFilePath).exists()) {
        System.out.println("Moving log of " + testId + " to archives -> " + destDir.getPath());
        FileUtils.copyFileToDirectory(new File(logFilePath),destDir);
      }
    } catch (Exception e) {
      System.out.println("Could not archive result record for " + testId + ":" + e.getMessage());
    }
  }

  public static boolean storageCheck(String size) {
    long fileSize = Long.parseLong(size);
    File root = new File("/");
    if (root.getFreeSpace() < fileSize * SIZE_MULTIPLIER + FIVE_GB) {
      return cleanupFolders(fileSize);
    }
    return true;
  }

  public static boolean cleanupFolders(long size) {
    File allureDirectory;
    if (isWindowsBased()) {
      allureDirectory = new File("C:\\nginx\\html\\allure\\");
    } else if (isLinuxBased()) {
      allureDirectory = new File("/var/www/allure/");
    } else {
      throw new UnsupportedOperationException("Only Windows and Linux are supported");
    }

    File root = new File("/");

    if (root.getTotalSpace() < size * SIZE_MULTIPLIER + FIVE_GB) {
      return false;
    }

    File[] files = allureDirectory.listFiles();
    Arrays.sort(files, Comparator.comparingLong(File::lastModified));

    int c=0;

    //1st check removes old reports until there's space to generate the new report
    while (root.getFreeSpace() < size * SIZE_MULTIPLIER + FIVE_GB && c < files.length) {
      System.out.println("Deleting folder:" + files[c].getName());
      Utils.deleteDirectory(files[c]);
      c += 1;
    }

    if (root.getFreeSpace() < size * SIZE_MULTIPLIER + FIVE_GB) {
      return false;
    }
    return true;
  }


  public static boolean isWindowsBased() {
    String osName = System.getProperty("os.name").toLowerCase();
    return osName.contains("win");
  }

  public static boolean isLinuxBased() {
    String osName = System.getProperty("os.name").toLowerCase();
    return osName.contains("nix") || osName.contains("nux") || osName.contains("aix");
  }

  public static JsonObject readJsonFile(String jsonFile) {
    FileReader fileReader = null;
    JsonReader jsonReader = null;
    JsonObject jsonObject = null;

    try {
      System.out.println("Reading '" + jsonFile + "' ...");
      fileReader = new FileReader(new File(jsonFile));
      jsonReader = Json.createReader(fileReader);
      jsonObject = jsonReader.readObject();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (fileReader != null) {
        try {
          fileReader.close();
        } catch (IOException e1) {
          e1.printStackTrace();
        }
      }

      if (jsonReader != null) {
        jsonReader.close();
      }

    }

    return jsonObject;
  }

  public static FileTime getFileCreationDate(String path) {
    try {
    BasicFileAttributes attr =
        Files.readAttributes(Paths.get(path), BasicFileAttributes.class);
    return attr.creationTime();
    } catch (Exception e) {
      return null;
    }
  }
}
