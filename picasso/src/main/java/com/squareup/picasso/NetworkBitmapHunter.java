/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import net.frakbot.imageviewex.ImageViewEx;

import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Downloader.Response;
import static com.squareup.picasso.Picasso.LoadedFrom.DISK;
import static com.squareup.picasso.Picasso.LoadedFrom.NETWORK;

class NetworkBitmapHunter extends BitmapHunter {
    private final Downloader downloader;
    private final boolean airplaneMode;
    private Picasso.LoadedFrom loadedFrom;

    public NetworkBitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache, Request request,
                               Downloader downloader, boolean airplaneMode) {
        super(picasso, dispatcher, cache, request);
        this.downloader = downloader;
        this.airplaneMode = airplaneMode;
    }

    @Override Image decode(Uri uri, PicassoBitmapOptions options, int retryCount)
            throws IOException {
        InputStream is = null;
        try {
            Response response = getNetworkResponse(retryCount == 0 || airplaneMode);
            is = response.stream;

            if (response.contentLength < picasso.getDownloadSizeMax()) {

                try {
                    byte[] imgData = ImageViewEx.Converters.inputStreamToByteArray(is, response.contentLength);
                    Bitmap bitmap = decodeArray(imgData, options);

                    if (bitmap == null) {
                        throw new IOException("Error decoding image stream.");
                    } else {
                        return new Image(bitmap, imgData, response.isGif);
                    }
                } catch (OutOfMemoryError e) {
                    Log.e(StatsSnapshot.TAG, "Out of memory trying to download or decode image.", e);
                    throw new IOException("Out of memory downloading or decoding image.");
                }
            } else {
                throw new IOException("Image file too big. > 2M");
            }

        } finally {
            Utils.closeQuietly(is);
        }
    }

    @Override Picasso.LoadedFrom getLoadedFrom() {
        return loadedFrom;
    }

    private Response getNetworkResponse(boolean localCacheOnly) throws IOException {
        Response response = downloader.load(uri, localCacheOnly);
        loadedFrom = response.cached ? DISK : NETWORK;
        return response;
    }

    private Bitmap decodeArray(byte[] imgBytes, PicassoBitmapOptions options) throws IOException {
        if (imgBytes == null) {
            return null;
        }
        if (options != null && options.inJustDecodeBounds) {
            BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length, options);
            calculateInSampleSize(options);
        }
        return BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length, options);
    }

    private Bitmap decodeStream(InputStream stream, PicassoBitmapOptions options) throws IOException {
        if (stream == null) {
            return null;
        }
        if (options != null && options.inJustDecodeBounds) {
            MarkableInputStream markStream = new MarkableInputStream(stream);
            stream = markStream;

            long mark = markStream.savePosition(1024); // Mirrors BitmapFactory.cpp value.
            BitmapFactory.decodeStream(stream, null, options);
            calculateInSampleSize(options);

            markStream.reset(mark);
        }
        return BitmapFactory.decodeStream(stream, null, options);
    }
}
