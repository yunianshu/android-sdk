package com.qiniu.android.storage;

import java.io.IOException;
import java.io.InputStream;

class UploadSourceStream implements UploadSource {

    private long readOffset = 0;

    private InputStream inputStream;
    private long size = UploadSource.UnknownSourceSize;
    private String fileName;

    UploadSourceStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    protected InputStream getInputStream() {
        return inputStream;
    }

    protected void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public String getId() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public long getSize() {
        if (size > UnknownSourceSize) {
            return size;
        } else {
            return UnknownSourceSize;
        }
    }

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public boolean couldReloadInfo() {
        return false;
    }

    @Override
    public boolean reloadInfo() {
        return false;
    }

    @Override
    public byte[] readData(int dataSize, long dataOffset) throws IOException {
        if (inputStream == null) {
            throw new IOException("inputStream is empty");
        }

        byte[] buffer = null;
        synchronized (this) {
            boolean isEOF = false;
            while (true) {
                if (readOffset == dataOffset) {
                    int readSize = 0;
                    buffer = new byte[dataSize];
                    while (readSize < dataSize) {
                        int ret = inputStream.read(buffer, readSize, dataSize - readSize);
                        if (ret < 0) {
                            isEOF = true;
                            break;
                        }
                        readSize += ret;
                    }

                    if (dataSize != readSize) {
                        byte[] newBuffer = new byte[readSize];
                        System.arraycopy(buffer, 0, newBuffer, 0, readSize);
                        buffer = newBuffer;
                    }

                    readOffset += readSize;
                    if (isEOF) {
                        size = readOffset;
                    }
                    break;
                } else if (readOffset < dataOffset) {
                    readOffset += inputStream.skip(dataOffset - readOffset);
                } else {
                    throw new IOException("read data error");
                }
            }
        }
        return buffer;
    }

    @Override
    public void close() {
    }
}
