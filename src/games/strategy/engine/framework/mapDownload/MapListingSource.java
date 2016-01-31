package games.strategy.engine.framework.mapDownload;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import games.strategy.debug.ClientLogger;


/**
 * Immutable data class that reads a configuration file and stores the location of where
 *  to find the "triplea_map.xml" file, whether that be a URL, or a local file path relative to
 *  the game root folder.
 *
 * Can be used to create a <code>MapDownloadAction</code>
 */
public class MapListingSource {

  public static final String MAP_LIST_DOWNLOAD_SITE_PROPERTY_KEY = "Map_List_File";
  private final String mapListDownloadSite;


  public MapListingSource(File mapDownloadPropertiesFile) {
    checkState(checkNotNull(mapDownloadPropertiesFile).isFile());
    mapListDownloadSite = readMapListDownloadSitePropertyValue(mapDownloadPropertiesFile);
  }

  private static String readMapListDownloadSitePropertyValue(File mapDownloadPropertiesFile) {
    String propertyValue = null;
    try (FileInputStream inputStream = new FileInputStream(mapDownloadPropertiesFile)) {
      Properties props = new Properties();
      props.load(inputStream);
      propertyValue = props.getProperty(MAP_LIST_DOWNLOAD_SITE_PROPERTY_KEY);
    } catch (FileNotFoundException e) {
      // Exception should not happen, already checked by the Checkstate call during construction
    } catch (IOException e) {
      ClientLogger.logError("failed to load property file: " + mapDownloadPropertiesFile.getAbsolutePath(), e);
    }
    return propertyValue;
  }


  /** Return the URL where we can download a file that lists each map that is available */
  protected String getMapListDownloadSite() {
    return mapListDownloadSite;
  }

}