package com.alon.vuze.plexautodelete;

import org.gudy.azureus2.plugins.Plugin;
import org.gudy.azureus2.plugins.PluginException;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.disk.DiskManagerFileInfo;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.download.DownloadException;
import org.gudy.azureus2.plugins.download.DownloadListener;
import org.gudy.azureus2.plugins.download.DownloadManager;
import org.gudy.azureus2.plugins.download.DownloadManagerListener;
import org.gudy.azureus2.plugins.download.DownloadRemovalVetoException;
import org.gudy.azureus2.plugins.logging.LoggerChannel;
import org.gudy.azureus2.plugins.logging.LoggerChannelListener;
import org.gudy.azureus2.plugins.ui.components.UITextArea;
import org.gudy.azureus2.plugins.ui.config.BooleanParameter;
import org.gudy.azureus2.plugins.ui.config.IntParameter;
import org.gudy.azureus2.plugins.ui.config.StringParameter;
import org.gudy.azureus2.plugins.ui.model.BasicPluginConfigModel;
import org.gudy.azureus2.plugins.ui.model.BasicPluginViewModel;
import org.gudy.azureus2.plugins.utils.Utilities;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class PlexAutoDeletePlugin implements Plugin, DownloadManagerListener,
        LoggerChannelListener {

  private static final long DAY_MS = TimeUnit.DAYS.toMillis(1);
  private static final int LOG_DAYS = 30;

  private UITextArea logArea;

  private LoggerChannel logger;

  private StringParameter server;

  private StringParameter port;

  private DownloadManager downloadManager;

  private StringParameter sections;

  private StringParameter vuzeRoot;

  private StringParameter plexRoot;

  private IntParameter duration;

  private BooleanParameter enable;

  private BasicPluginViewModel viewModel;
  private Timer timer = new Timer(true);
  private Utilities utilities;

  public void initialize(PluginInterface pluginInterface) throws PluginException {
    downloadManager = pluginInterface.getDownloadManager();
    createConfigModule(pluginInterface);
    logger = pluginInterface.getLogger().getTimeStampedChannel("Plex Auto Delete");
    viewModel = pluginInterface.getUIManager()
            .createBasicPluginViewModel("Plex Auto Delete");
    logArea = viewModel.getLogArea();
    logger.addListener(this);
    utilities = pluginInterface.getUtilities();
  }

  private void createConfigModule(PluginInterface pluginInterface) {
    final BasicPluginConfigModel configModel = pluginInterface.getUIManager()
            .createBasicPluginConfigModel("plexautodelete");
    configModel.addLabelParameter2("config.title");
    enable = configModel.addBooleanParameter2("enable", "config.enable", false);
    server = configModel.addStringParameter2("server", "config.server", "localhost");
    port = configModel.addStringParameter2("port", "config.port", "32400");
    sections = configModel.addStringParameter2("sections", "config.sections", "");
    duration = configModel.addIntParameter2("duration", "config.duration", 30);
    vuzeRoot = configModel.addStringParameter2("vuze-root", "config.vuze-root", "");
    plexRoot = configModel.addStringParameter2("plex-root", "config.plex-root", "");
    configModel.addActionParameter2(null, "config.delete_now_button")
        .addListener(param -> timer.schedule(new TimerTask() {
          @Override
          public void run() {
            deleteWatchedDownloads();
          }
        }, 0));

    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        deleteWatchedDownloads();
      }
    }, 0, DAY_MS);
  }

  @Override
  public void downloadAdded(Download download) {
    utilities.createDelayedTask(this::deleteWatchedDownloads);
  }

  @Override
  public void downloadRemoved(Download download) {
    // nop
  }

  @Override
  public void messageLogged(int type, String content) {
    logArea.appendText(content + "\n");
  }

  @Override
  public void messageLogged(String str, Throwable error) {
    logArea.appendText(str + "\n");
    StringWriter writer = new StringWriter();
    error.printStackTrace(new PrintWriter(writer));
    logArea.appendText(writer.toString() + "\n");
  }

  private void deleteWatchedDownloads() {
    try {
      final PlexClient client = new PlexClient(server.getValue(), Integer.valueOf(port.getValue()));

      logger.log("Fetching show sections from Plex: " + client);
      viewModel.getActivity().setText("Fetching sections from Plex");
      final String mSectionsValue = sections.getValue();
      final HashSet<String> sectionNames =
          mSectionsValue.length() == 0 ? null : Sets.newHashSet(mSectionsValue.split(","));
      final Collection<Directory> sections = client.getShowSections();
      logger.log("Found " + sections.size() + " sections");

      long cutoff = new Date().getTime() - duration.getValue() * DAY_MS;
      final Set<String> filesToDelete = new HashSet<>();
      final Set<String> allFiles = new HashSet<>();
      final String plexRoot = this.plexRoot.getValue();
      final String vuzeRoot = this.vuzeRoot.getValue();

      viewModel.getActivity().setText("Fetching episodes from Plex");
      final List<Video> watchedVideos = new ArrayList<>();
      for (Directory section : sections) {
        if (sectionNames == null || sectionNames.contains(section.getTitle())) {
          logger.log("Checking section " + section.getTitle());
          final List<Video> videos = client.getEpisodes(section);
          for (Video video : videos) {
            final ArrayList<String> normalizedFiles = new ArrayList<>();
            for (String file : video.getFiles()) {
              normalizedFiles.add(normalizeFilename(file, plexRoot));
            }
            allFiles.addAll(normalizedFiles);
            if (video.getViewCount() > 0) {
              watchedVideos.add(video);
              final long lastViewedAt = video.getLastViewedAt();
              if (lastViewedAt < cutoff) {
                filesToDelete.addAll(normalizedFiles);
              }
            }
          }
        }
      }
      if (watchedVideos.size() == 0) {
        logger.log("No watched files found");
        return;
      }

      watchedVideos.sort(Comparator.comparingLong(Video::getLastViewedAt));
      logger.log(String.format("%d watched episodes found", watchedVideos.size()));
      final int[] byDay = new int[LOG_DAYS];
      for (Video video : watchedVideos) {
        final long lastViewedAt = video.getLastViewedAt();
        final long diff = lastViewedAt - cutoff;
        if (diff > 0) {
          int daysTillDelete = (int) (diff / DAY_MS);
          if (daysTillDelete < 0) {
            daysTillDelete = 0;
          }
          if (daysTillDelete < LOG_DAYS) {
            byDay[daysTillDelete]++;
          }
        }
      }

      if (filesToDelete.size() > 0) {
        logger.log(String.format("%d episodes will be deleted now", filesToDelete.size()));
      }
      if (byDay[0] > 0) {
        logger.log(String.format("%d episodes will be deleted in 1 day", byDay[0]));
      }
      for (int i = 1; i < LOG_DAYS; i++) {
        if (byDay[i] > 0) {
          logger.log(String.format("%d episodes will be deleted in %d days", byDay[i], i + 1));
        }
      }

      checkTorrents(filesToDelete, allFiles, vuzeRoot);
      checkOrphans(filesToDelete, vuzeRoot);
      logger.log("Done!!!");
    } catch (ParserConfigurationException | SAXException | XPathExpressionException | IOException e) {
      logger.log("Error", e);
    } finally {
      viewModel.getActivity().setText("Idle");
    }
  }

  private void checkTorrents(Set<String> filesToDelete, Set<String> allFiles, String vuzeRoot) {
    viewModel.getActivity().setText("Checking torrents");
    final Download[] downloads = downloadManager.getDownloads();
    for (Download download : downloads) {
      boolean isWatched = false;
      boolean servedByPlex = false;
      for (DiskManagerFileInfo info : download.getDiskManagerFileInfo()) {
        final String file = normalizeFilename(info.getFile(true).getPath(), vuzeRoot);
        if (allFiles.contains(file)) {
          servedByPlex = true;
          if (!filesToDelete.contains(file)) {
            isWatched = false;
            break;
          }
          filesToDelete.remove(file);
          isWatched = true;
        }
      }
      if (servedByPlex && isWatched) {
        if (download.getState() == Download.ST_STOPPED) {
          logger.log("    Deleting " + download.getTorrentFileName());
          removeDownload(download);
        } else {
          download.addListener(new DownloadListener() {
            @Override
            public void stateChanged(Download download, int oldState, int newState) {
              if (newState == Download.ST_STOPPED) {
                logger.log("    Deleting " + download.getTorrentFileName());
                removeDownload(download);
              }
            }

            @Override
            public void positionChanged(Download download, int oldPosition, int newPosition) {
              // noop
            }
          });
          try {
            logger.log("    Stopping " + download.getTorrentFileName());
            download.stop();
          } catch (DownloadException e) {
            logger.log("Error", e);
          }
        }
      }
    }
  }

  private void checkOrphans(Set<String> filesToDelete, String vuzeRoot) {
    viewModel.getActivity().setText("Checking orphans");
    if (!filesToDelete.isEmpty()) {
      logger.log("Deleting orphans");
      for (String filename : filesToDelete) {
        final File file = new File(vuzeRoot + filename);
        logger.log("    Deleting " + file);
        if (enable.getValue()) {
          boolean deleted = file.delete();
          logger.log("    Deleted: " + deleted);
        }
      }
    }
  }

  private void removeDownload(Download download) {
    try {
      if (enable.getValue()) {
        download.remove(true, true);
      }
    } catch (DownloadException | DownloadRemovalVetoException e) {
      logger.log("Error", e);
    }
  }

  private String normalizeFilename(String file, String root) {
    return file.replace('\\', '/').replace(root, "");
  }
}
