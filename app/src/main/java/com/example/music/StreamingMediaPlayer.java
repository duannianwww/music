package com.example.music;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
public class StreamingMediaPlayer {
    private static final int INTIAL_KB_BUFFER = 120;
    private ImageButton playButton;//播放按钮
    private SeekBar progressBar;//进度条
    private TextView playTime;//播放时间
    private long mediaLengthInKb;
    private long mediaLengthInSeconds;
    private int totalKbRead = 0;
    private final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    };
    private MediaPlayer mediaPlayer;//播放器
    private File downloadingMediaFile;//下载的音乐文件
    private boolean isInterrupted;//判断是否缓冲结束，false =未结束，true =结束
    private Context context;
    private int counter = 0;

    public StreamingMediaPlayer(Context context, ImageButton playButton, Button streamButton, SeekBar progressBar, TextView playTime) {//初始化，对象设置
        this.context = context;
        this.playButton = playButton;
        this.playTime = playTime;
        this.progressBar = progressBar;
    }

    public void startStreaming(final String mediaUrl, long mediaLengthInKb, long mediaLengthInSeconds) throws IOException {//开始缓冲
        this.mediaLengthInKb = mediaLengthInKb;
        this.mediaLengthInSeconds = mediaLengthInSeconds;
        Runnable r = new Runnable() {
            public void run() {
                try {
                    StreamingMediaPlayer.this.downloadAudioIncrement(mediaUrl);
                } catch (IOException var2) {
                    Log.e(this.getClass().getName(), "Unable to initialize the MediaPlayer for fileUrl=" + mediaUrl, var2);
                }
            }
        };
        (new Thread(r)).start();
    }

    public void downloadAudioIncrement(String mediaUrl) throws IOException {//下载网络音乐
        URLConnection cn = (new URL(mediaUrl)).openConnection();
        cn.connect();
        InputStream stream = cn.getInputStream();
        if (stream == null) {
            Log.e(this.getClass().getName(), "Unable to create InputStream for mediaUrl:" + mediaUrl);
        }

        this.downloadingMediaFile = new File(this.context.getCacheDir(), "downloadingMedia.dat");
        if (this.downloadingMediaFile.exists()) {
            this.downloadingMediaFile.delete();
        }

        FileOutputStream out = new FileOutputStream(this.downloadingMediaFile);
        byte[] buf = new byte[16384];
        int totalBytesRead = 0;
        int incrementalBytesRead = 0;

        do {
            int numread = stream.read(buf);
            if (numread <= 0) {
                break;
            }

            out.write(buf, 0, numread);
            totalBytesRead += numread;
            incrementalBytesRead += numread;
            this.totalKbRead = totalBytesRead / 1000;
            this.testMediaBuffer();
            this.fireDataLoadUpdate();
        } while(this.validateNotInterrupted());

        stream.close();
        if (this.validateNotInterrupted()) {
            this.fireDataFullyLoaded();
        }

    }

    private boolean validateNotInterrupted() {//缓冲未完成
        if (this.isInterrupted) {
            if (this.mediaPlayer != null) {
                this.mediaPlayer.pause();
            }

            return false;
        } else {
            return true;
        }
    }

    private void testMediaBuffer() {//开始播放任务
        Runnable updater = new Runnable() {
            public void run() {
                if (StreamingMediaPlayer.this.mediaPlayer == null) {
                    if (StreamingMediaPlayer.this.totalKbRead >= 120) {
                        try {
                            StreamingMediaPlayer.this.startMediaPlayer();
                        } catch (Exception var2) {
                            Log.e(this.getClass().getName(), "Error copying buffered conent.", var2);
                        }
                    }
                } else if (StreamingMediaPlayer.this.mediaPlayer.getDuration() - StreamingMediaPlayer.this.mediaPlayer.getCurrentPosition() <= 1000) {
                    StreamingMediaPlayer.this.transferBufferToMediaPlayer();
                }

            }
        };
        this.handler.post(updater);
    }

    private void startMediaPlayer() {//播放器开始播放
        try {
            File bufferedFile = new File(this.context.getCacheDir(), "playingMedia" + this.counter++ + ".dat");
            this.moveFile(this.downloadingMediaFile, bufferedFile);
            Log.e(this.getClass().getName(), "Buffered File path: " + bufferedFile.getAbsolutePath());
            Log.e(this.getClass().getName(), "Buffered File length: " + bufferedFile.length());
            this.mediaPlayer = this.createMediaPlayer(bufferedFile);
            this.mediaPlayer.setAudioStreamType(3);
            this.mediaPlayer.start();
            this.startPlayProgressUpdater();
            Toast.makeText(context,"缓冲完成！",Toast.LENGTH_SHORT).show();
            this.playButton.setEnabled(true);
        } catch (IOException var2) {
            Log.e(this.getClass().getName(), "Error initializing the MediaPlayer.", var2);
        }

    }

    private MediaPlayer createMediaPlayer(File mediaFile) throws IOException {//创建播放器
        MediaPlayer mPlayer = new MediaPlayer();
        mPlayer.setOnErrorListener(new OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(this.getClass().getName(), "Error in MediaPlayer: (" + what + ") with extra (" + extra + ")");
                return false;
            }
        });
        FileInputStream fis = new FileInputStream(mediaFile);
        mPlayer.setDataSource(fis.getFD());
        mPlayer.prepare();
        return mPlayer;
    }

    private void transferBufferToMediaPlayer() {
        try {
            boolean wasPlaying = this.mediaPlayer.isPlaying();
            int curPosition = this.mediaPlayer.getCurrentPosition();
            File oldBufferedFile = new File(this.context.getCacheDir(), "playingMedia" + this.counter + ".dat");
            File bufferedFile = new File(this.context.getCacheDir(), "playingMedia" + this.counter++ + ".dat");
            bufferedFile.deleteOnExit();
            this.moveFile(this.downloadingMediaFile, bufferedFile);
            this.mediaPlayer.pause();
            this.mediaPlayer = this.createMediaPlayer(bufferedFile);
            this.mediaPlayer.seekTo(curPosition);
            boolean atEndOfFile = this.mediaPlayer.getDuration() - this.mediaPlayer.getCurrentPosition() <= 1000;
            if (wasPlaying || atEndOfFile) {
                this.mediaPlayer.start();
            }

            oldBufferedFile.delete();
        } catch (Exception var6) {
            Log.e(this.getClass().getName(), "Error updating to newly loaded content.", var6);
        }

    }

    private void fireDataLoadUpdate() {
        Runnable updater = new Runnable() {
            public void run() {
                float loadProgress = (float)StreamingMediaPlayer.this.totalKbRead / (float)StreamingMediaPlayer.this.mediaLengthInKb;
                StreamingMediaPlayer.this.progressBar.setSecondaryProgress((int)(loadProgress * 100.0F));
            }
        };
        this.handler.post(updater);
    }

    private void fireDataFullyLoaded() {
        Runnable updater = new Runnable() {
            public void run() {
                StreamingMediaPlayer.this.transferBufferToMediaPlayer();
                StreamingMediaPlayer.this.downloadingMediaFile.delete();
            }
        };
        this.handler.post(updater);
    }

    public MediaPlayer getMediaPlayer() {
        return this.mediaPlayer;
    }

    public void startPlayProgressUpdater() {//开始进度条更新
        float progress = (float)this.mediaPlayer.getCurrentPosition() / 1000.0F / (float)this.mediaLengthInSeconds;
        this.progressBar.setProgress((int)(progress * 100.0F));
        int pos = this.mediaPlayer.getCurrentPosition();
        int min = pos / 1000 / 60;
        int sec = pos / 1000 % 60;
        if (sec < 10) {
            this.playTime.setText(min + ":0" + sec);
        } else {
            this.playTime.setText(min + ":" + sec);
        }

        if (this.mediaPlayer.isPlaying()) {
            Runnable notification = new Runnable() {
                public void run() {
                    StreamingMediaPlayer.this.startPlayProgressUpdater();
                }
            };
            this.handler.postDelayed(notification, 1000L);
        }

    }

    public void interrupt() {//缓冲完成
        this.playButton.setEnabled(false);
        this.isInterrupted = true;
        this.validateNotInterrupted();
    }

    public void moveFile(File oldLocation, File newLocation) throws IOException {//移动文件
        if (oldLocation.exists()) {
            BufferedInputStream reader = new BufferedInputStream(new FileInputStream(oldLocation));
            BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(newLocation, false));

            try {
                byte[] buff = new byte[8192];

                int numChars;
                while((numChars = reader.read(buff, 0, buff.length)) != -1) {
                    writer.write(buff, 0, numChars);
                }
            } catch (IOException var14) {
                throw new IOException("IOException when transferring " + oldLocation.getPath() + " to " + newLocation.getPath());
            } finally {
                try {
                    if (reader != null) {
                        writer.close();
                        reader.close();
                    }
                } catch (IOException var13) {
                    Log.e(this.getClass().getName(), "Error closing files when transferring " + oldLocation.getPath() + " to " + newLocation.getPath());
                }

            }

        } else {
            throw new IOException("Old location does not exist when transferring " + oldLocation.getPath() + " to " + newLocation.getPath());
        }
    }
}
