package com.qiniu.android;

import android.test.AndroidTestCase;

import com.qiniu.android.collect.ReportConfig;
import com.qiniu.android.collect.ReportItem;
import com.qiniu.android.collect.UploadInfoReporter;
import com.qiniu.android.utils.LogUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by yangsen on 2020/5/25
 */
public class UploadInfoReporterTest extends AndroidTestCase {

    private CountDownLatch signal = null;

    private class TestParam{
        int totalCount = 50;
        int completeCount = 0;
    }
    public void testSave(){

        UploadInfoReporter.getInstance().clean();
        signal = new CountDownLatch(1);
        final String time = new Date().toString();
        for (int i = 0; i < 50; i++) {
            final int iP = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ReportItem item = new ReportItem();
                    item.setReport(ReportItem.LogTypeQuality, ReportItem.QualityKeyLogType);
                    item.setReport((new Date().getTime() / 1000), ReportItem.QualityKeyUpTime);
                    item.setReport("ok", ReportItem.QualityKeyResult);
                    item.setReport(1, ReportItem.QualityKeyTotalElapsedTime);
                    item.setReport(1, ReportItem.QualityKeyRequestsCount);
                    item.setReport(1, ReportItem.QualityKeyRegionsCount);
                    item.setReport(1, ReportItem.QualityKeyBytesSent);
                    report(item);
                }
            }).start();
        }

        try {
            signal.await(5, TimeUnit.SECONDS); // wait for callback
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        File logFile = new File(ReportConfig.getInstance().recordDirectory + "/qiniu.log");
        showContent(logFile);

        File logTempFile = new File(ReportConfig.getInstance().recordDirectory + "/qiniuTemp.log");
        showContent(logTempFile);

    }

    private void report(ReportItem item){
        UploadInfoReporter reporter = UploadInfoReporter.getInstance();
        reporter.report(item, TestConfig.token_z0);
    }

    private static void showContent(File recordFile) {
        FileReader fileReader = null;
        BufferedReader br = null;
        try {
            fileReader = new FileReader(recordFile);
            br = new BufferedReader(fileReader);
            String line = null;
            LogUtil.d("== " + recordFile.getPath() + ": start");
            while ((line = br.readLine()) != null) {
                LogUtil.d(("== " + line));
            }
            LogUtil.d("== " + recordFile.getPath() + ": end");
        } catch (Exception e) {

        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
