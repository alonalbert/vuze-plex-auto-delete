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
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public void initialize(PluginInterface pluginInterface) throws PluginException {
        mDownloadManager = pluginInterface.getDownloadManager();
        mDownloadManager.addListener(this);
        createConfigModule(pluginInterface);
        mLogger = pluginInterface.getLogger().getTimeStampedChannel("Plex Auto Delete");
        mLogArea = pluginInterface.getUIManager().createBasicPluginViewModel("Plex Auto Delete")
                .getLogArea();
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
    }

    private void deleteWatchedDownloads() {
        try {
            final PlexClient client = new PlexClient(mServer.getValue(), mPort.getValue());
            final Collection<Directory> sections = client.getShowSections();
            final long now = new Date().getTime();
            final Set<String> watchedFiles = new HashSet<String>();
            final Set<String> allFiles = new HashSet<String>();
            for (Directory section : sections) {
                final List<Episode> episodes = client.getWatchedEpisodes(section);
                for (Episode episode : episodes) {
                    for (String file : episode.getFiles()) {
                        final String normalizedFilename = normalizeFilename(file, mPlexRoot.getValue());
                        allFiles.add(normalizedFilename);
                        if (episode.getViewCount() > 0 &&  episode.getLastViewedAt() + mDuration.getValue() * DAY_MS < now) {
                            watchedFiles.add(normalizedFilename);
                        }
                    }
                }
            }

            final Download[] downloads = mDownloadManager.getDownloads();
            for (Download download : downloads) {
                mLogger.log("Checking " + download.getTorrentFileName());
                boolean isWatched = false;
                boolean servedByPlex = false;
                for (DiskManagerFileInfo info : download.getDiskManagerFileInfo()) {
                    final String file = normalizeFilename(info.getFile().getPath(), mVuzeRoot.getValue());
                    if (allFiles.contains(file)) {
                        servedByPlex = true;
                        if (!watchedFiles.contains(file)) {
                            isWatched = false;
                            break;
                        }
                        isWatched = true;
                    }
                }
                mLogger.log("    Served by Plex: " + servedByPlex);
                mLogger.log("    Is watched    : " + isWatched);

                if (servedByPlex && isWatched) {
                    if (download.getState() == Download.ST_STOPPED) {
                        mLogger.log("    Deleting");
                        removeDownload(download);
                    } else {
                        download.addListener(new DownloadListener() {
                            public void stateChanged(Download download, int oldState, int newState) {
                                if (newState == Download.ST_STOPPED) {
                                    mLogger.log("    Deleting");
                                    removeDownload(download);
                                }
                            }

                            public void positionChanged(Download download, int oldPosition, int newPosition) {
                                // noop
                            }
                        });
                        try {
                            mLogger.log("    Stopping");
                            download.stop();
                        } catch (DownloadException e) {
                            mLogger.log("Error", e);
                        }
                    }
                }
            }
        } catch (ParserConfigurationException e) {
            mLogger.log("Error", e);
        } catch (SAXException e) {
            mLogger.log("Error", e);
        } catch (XPathExpressionException e) {
            mLogger.log("Error", e);
        } catch (IOException e) {
            mLogger.log("Error", e);
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
