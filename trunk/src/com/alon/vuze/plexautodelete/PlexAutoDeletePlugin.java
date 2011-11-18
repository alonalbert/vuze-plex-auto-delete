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
import org.gudy.azureus2.plugins.ui.config.Parameter;
import org.gudy.azureus2.plugins.ui.config.ParameterListener;
import org.gudy.azureus2.plugins.ui.config.StringParameter;
import org.gudy.azureus2.plugins.ui.model.BasicPluginConfigModel;
import org.gudy.azureus2.plugins.ui.model.BasicPluginViewModel;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

public class PlexAutoDeletePlugin implements Plugin, DownloadManagerListener,
        LoggerChannelListener {

    private static final long DAY_MS = 24 * 3600 * 1000;

    private UITextArea mLogArea;

    private LoggerChannel mLogger;

    private StringParameter mServer;

    private IntParameter mPort;

    private DownloadManager mDownloadManager;

    private StringParameter mVuzeRoot;

    private StringParameter mPlexRoot;

    private IntParameter mDuration;

    private BooleanParameter mEnable;

    private BasicPluginViewModel mViewModel;

    private Timer mTimer = new Timer(true);

    public void initialize(PluginInterface pluginInterface) throws PluginException {
        mDownloadManager = pluginInterface.getDownloadManager();
        createConfigModule(pluginInterface);
        mLogger = pluginInterface.getLogger().getTimeStampedChannel("Plex Auto Delete");
        mViewModel = pluginInterface.getUIManager()
                .createBasicPluginViewModel("Plex Auto Delete");
        mLogArea = mViewModel.getLogArea();
        mLogger.addListener(this);
    }

    private void createConfigModule(PluginInterface pluginInterface) {
        final BasicPluginConfigModel configModel = pluginInterface.getUIManager()
                .createBasicPluginConfigModel("plexautodelete");
        configModel.addLabelParameter2("config.title");
        mEnable = configModel.addBooleanParameter2("enable", "config.enable", false);
        mServer = configModel.addStringParameter2("server", "config.server", "localhost");
        mPort = configModel.addIntParameter2("port", "config.port", 32400);
        mDuration = configModel.addIntParameter2("duration", "config.duration", 30);
        mVuzeRoot = configModel.addStringParameter2("vuze-root", "config.vuze-root", "");
        mPlexRoot = configModel.addStringParameter2("plex-root", "config.plex-root", "");
        configModel.addActionParameter2(null, "config.delete_now_button").addListener(new ParameterListener() {
            public void parameterChanged(Parameter param) {
                deleteWatchedDownloads();
            }
        });

        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                deleteWatchedDownloads();
            }
        }, 0, DAY_MS);
    }

    private void deleteWatchedDownloads() {
        try {
            final PlexClient client = new PlexClient(mServer.getValue(), mPort.getValue());

            mLogger.log("Fetching show sections from Plex: " + client);
            mViewModel.getActivity().setText("Fetching sections from Plex");
            final Collection<Directory> sections = client.getShowSections();
            mLogger.log("Found " + sections.size() + " sections");

            long cutoff = new Date().getTime() - mDuration.getValue() * DAY_MS;
            final Set<String> filesToDelete = new HashSet<String>();
            final Set<String> allFiles = new HashSet<String>();
            final String plexRoot = mPlexRoot.getValue();
            final String vuzeRoot = mVuzeRoot.getValue();

            mViewModel.getActivity().setText("Fetching episodes from Plex");
            final List<Episode> watchedEpisodes = new ArrayList<Episode>();
            for (Directory section : sections) {
                final List<Episode> episodes = client.getEpisodes(section);
                for (Episode episode : episodes) {
                    final ArrayList<String> normalizedFiles = new ArrayList<String>();
                    for (String file : episode.getFiles()) {
                        normalizedFiles.add(normalizeFilename(file, plexRoot));
                    }
                    allFiles.addAll(normalizedFiles);
                    if (episode.getViewCount() > 0) {
                        watchedEpisodes.add(episode);
                        final long lastViewedAt = episode.getLastViewedAt();
                        if (lastViewedAt  < cutoff) {
                            filesToDelete.addAll(normalizedFiles);
                        } else {
                        }
                    }
                }
            }
            if (watchedEpisodes.size() == 0) {
                mLogger.log("No watched files found");
                return;
            }

            mLogger.log(String.format("%d watched episodes found", watchedEpisodes.size()));
            mLogger.log(String.format("    %10s  %8s   %s", "Watched on", "Delete", "Filename"));
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (Episode episode : watchedEpisodes) {
                for (String file : episode.getFiles()) {
                    long lastViewedAt = episode.getLastViewedAt();
                    mLogger.log(String.format("    %10s  %8s   %s",
                            dateFormat.format(new Date(lastViewedAt)),
                            lastViewedAt < cutoff ? "YES" : "NO",
                            file));
                }
            }

            checkTorrents(filesToDelete, allFiles, vuzeRoot);
            checkOrphans(filesToDelete, vuzeRoot);
        } catch (ParserConfigurationException e) {
            mLogger.log("Error", e);
        } catch (SAXException e) {
            mLogger.log("Error", e);
        } catch (XPathExpressionException e) {
            mLogger.log("Error", e);
        } catch (IOException e) {
            mLogger.log("Error", e);
        } finally {
            mViewModel.getActivity().setText("Idle");
        }
    }

    private void checkOrphans(Set<String> filesToDelete, String vuzeRoot) {
        mViewModel.getActivity().setText("Checking orphans");
        if (!filesToDelete.isEmpty()) {
            mLogger.log("Deleting orphans");
            for (String filename : filesToDelete) {
                final File file = new File(vuzeRoot, filename);
                mLogger.log("    Deleting " + file);
                if (mEnable.getValue()) {
                    file.delete();
                }
            }
        }
    }

    private void checkTorrents(Set<String> filesToDelete, Set<String> allFiles, String vuzeRoot) {
        mViewModel.getActivity().setText("Checking torrents");
        final Download[] downloads = mDownloadManager.getDownloads();
        for (Download download : downloads) {
            boolean isWatched = false;
            boolean servedByPlex = false;
            for (DiskManagerFileInfo info : download.getDiskManagerFileInfo()) {
                final String file = normalizeFilename(info.getFile().getPath(), vuzeRoot);
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
                    mLogger.log("    Deleting " + download.getTorrentFileName());
                    removeDownload(download);
                } else {
                    download.addListener(new DownloadListener() {
                        public void stateChanged(Download download, int oldState, int newState) {
                            if (newState == Download.ST_STOPPED) {
                                mLogger.log("    Deleting " + download.getTorrentFileName());
                                removeDownload(download);
                            }
                        }

                        public void positionChanged(Download download, int oldPosition, int newPosition) {
                            // noop
                        }
                    });
                    try {
                        mLogger.log("    Stopping " + download.getTorrentFileName());
                        download.stop();
                    } catch (DownloadException e) {
                        mLogger.log("Error", e);
                    }
                }
            }
        }
    }

    private void removeDownload(Download download) {
        try {
            if (mEnable.getValue()) {
                download.remove(true, true);
            }
        } catch (DownloadException e) {
            mLogger.log("Error", e);
        } catch (DownloadRemovalVetoException e) {
            mLogger.log("Error", e);
        }
    }

    private String normalizeFilename(String file, String root) {
        return file.replace('\\', '/').replace(root, "");
    }

    public void downloadAdded(Download download) {
        deleteWatchedDownloads();
    }

    public void downloadRemoved(Download download) {
        // nop
    }

    public void messageLogged(int type, String content) {
        mLogArea.appendText(content + "\n");
    }

    public void messageLogged(String str, Throwable error) {
      mLogArea.appendText(str + "\n");
      StringWriter writer = new StringWriter();
      error.printStackTrace(new PrintWriter(writer));
      mLogArea.appendText(writer.toString() + "\n");
    }
}
