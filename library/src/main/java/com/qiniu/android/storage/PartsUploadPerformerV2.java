package com.qiniu.android.storage;

import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.http.metrics.UploadRegionRequestMetrics;
import com.qiniu.android.http.request.RequestTransaction;
import com.qiniu.android.http.request.handler.RequestProgressHandler;
import com.qiniu.android.utils.LogUtil;
import com.qiniu.android.utils.StringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

class PartsUploadPerformerV2 extends PartsUploadPerformer {

    PartsUploadPerformerV2(UploadSource uploadSource,
                           String fileName,
                           String key,
                           UpToken token,
                           UploadOptions options,
                           Configuration config,
                           String recorderKey) {
        super(uploadSource, fileName, key, token, options, config, recorderKey);
    }

    @Override
    UploadInfo getUploadInfoFromJson(UploadSource source, JSONObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }
        return UploadInfoV2.infoFromJson(source, jsonObject);
    }

    @Override
    UploadInfo getDefaultUploadInfo() {
        return new UploadInfoV2(uploadSource, config);
    }

    @Override
    void serverInit(final PartsUploadPerformerCompleteHandler completeHandler) {
        final UploadInfoV2 info = (UploadInfoV2) uploadInfo;
        if (info != null && info.isValid()) {
            LogUtil.i("key:" + StringUtils.toNonnullString(key) + " serverInit success");
            ResponseInfo responseInfo = ResponseInfo.successResponse();
            completeHandler.complete(responseInfo, null, null);
            return;
        }

        final RequestTransaction transaction = createUploadRequestTransaction();
        transaction.initPart(true, new RequestTransaction.RequestCompleteHandler() {
            @Override
            public void complete(ResponseInfo responseInfo, UploadRegionRequestMetrics requestMetrics, JSONObject response) {

                destroyUploadRequestTransaction(transaction);

                String uploadId = null;
                Long expireAt = null;
                if (response != null) {
                    try {
                        uploadId = response.getString("uploadId");
                        expireAt = response.getLong("expireAt");
                    } catch (JSONException e) {
                    }
                }
                if (responseInfo.isOK() && uploadId != null && expireAt != null) {
                    info.uploadId = uploadId;
                    info.expireAt = expireAt;
                    recordUploadInfo();
                }
                completeHandler.complete(responseInfo, requestMetrics, response);
            }
        });
    }

    @Override
    void uploadNextData(final PartsUploadPerformerDataCompleteHandler completeHandler) {
        final UploadInfoV2 info = (UploadInfoV2) uploadInfo;

        UploadData data = null;
        IOException readException = null;
        synchronized (this) {
            try {
                data = info.nextUploadData();
            } catch (IOException e) {
                // 此处可能无法恢复
                readException = e;
            }

            if (data != null) {
                if (data.data == null) {
                    readException = new IOException("get data error");
                    data = null;
                } else {
                    data.isUploading = true;
                    data.isCompleted = false;
                }
            }
        }

        if (readException != null) {
            LogUtil.i("key:" + StringUtils.toNonnullString(key) + " " + readException.getMessage());

            ResponseInfo responseInfo = ResponseInfo.localIOError(readException.getMessage());
            completeHandler.complete(true, responseInfo, null, responseInfo.response);
            return;
        }

        if (data == null) {
            LogUtil.i("key:" + StringUtils.toNonnullString(key) + " no data left");

            ResponseInfo responseInfo = ResponseInfo.sdkInteriorError("no data left");
            completeHandler.complete(true, responseInfo, null, null);
            return;
        }

        final UploadData uploadData = data;
        RequestProgressHandler progressHandler = new RequestProgressHandler() {
            @Override
            public void progress(long totalBytesWritten, long totalBytesExpectedToWrite) {
                uploadData.setUploadSize(totalBytesWritten);
                notifyProgress();
            }
        };

        final RequestTransaction transaction = createUploadRequestTransaction();
        transaction.uploadPart(true, info.uploadId, data.index, data.data, progressHandler, new RequestTransaction.RequestCompleteHandler() {
            @Override
            public void complete(ResponseInfo responseInfo, UploadRegionRequestMetrics requestMetrics, JSONObject response) {

                destroyUploadRequestTransaction(transaction);

                String etag = null;
                String md5 = null;
                if (response != null) {
                    try {
                        etag = response.getString("etag");
                        md5 = response.getString("md5");
                    } catch (JSONException e) {
                    }
                }
                if (responseInfo.isOK() && etag != null && md5 != null) {
                    uploadData.etag = etag;
                    uploadData.isUploading = false;
                    uploadData.isCompleted = true;
                    uploadData.data = null;
                    recordUploadInfo();
                    notifyProgress();
                } else {
                    uploadData.isUploading = false;
                    uploadData.isCompleted = false;
                }
                completeHandler.complete(false, responseInfo, requestMetrics, response);
            }
        });
    }

    @Override
    void completeUpload(final PartsUploadPerformerCompleteHandler completeHandler) {
        final UploadInfoV2 info = (UploadInfoV2) uploadInfo;

        List<Map<String, Object>> partInfoArray = info.getPartInfoArray();
        final RequestTransaction transaction = createUploadRequestTransaction();

        transaction.completeParts(true, fileName, info.uploadId, partInfoArray, new RequestTransaction.RequestCompleteHandler() {
            @Override
            public void complete(ResponseInfo responseInfo, UploadRegionRequestMetrics requestMetrics, JSONObject response) {

                destroyUploadRequestTransaction(transaction);
                completeHandler.complete(responseInfo, requestMetrics, response);
            }
        });
    }

//    private byte[] getUploadDataWithRetry(UploadData data) {
//        byte[] uploadData = null;
//
//        int maxTime = 3;
//        int index = 0;
//        while (index < maxTime) {
//            uploadData = getUploadData(data);
//            if (uploadData != null) {
//                break;
//            }
//            index ++;
//        }
//
//        return uploadData;
//    }
//
//    private synchronized byte[] getUploadData(UploadData data) {
//        if (randomAccessFile == null || data == null) {
//            return null;
//        }
//
//        int readSize = 0;
//        byte[] uploadData = new byte[data.size];
//        try {
//            randomAccessFile.seek(data.offset);
//            while (readSize < data.size) {
//                int ret = randomAccessFile.read(uploadData, readSize, data.size - readSize);
//                if (ret < 0) {
//                    break;
//                }
//                readSize += ret;
//            }
//            // 读数据非预期
//            if (readSize != data.size) {
//                uploadData = null;
//            }
//        } catch (IOException e) {
//            uploadData = null;
//        }
//        return uploadData;
//    }
}
