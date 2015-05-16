package com.oh1a2b.diaperpie;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

public class HttpUtils {
    private static final String TAG = "HttpUtils";

    private static final int BUFFER_SIZE = 2048;

    public static final int HTTP_METHOD_GET = 0;
    public static final int HTTP_METHOD_PUT = 1;
    public static final int HTTP_METHOD_POST = 2;
    public static final int HTTP_METHOD_DELETE = 3;

    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_WWW_FORM = "application/x-www-form-urlencoded";

    public static int lastResponseCode = -1;

    public static ByteArrayOutputStream httpRequest(int httpMethod, String url) {
        return httpRequest(httpMethod, url, null, null, null);
    }

    public static ByteArrayOutputStream httpRequest(int httpMethod, String url, String contentType, String data) {
        return httpRequest(httpMethod, url, contentType, data, null);
    }

    public static ByteArrayOutputStream httpRequest(int httpMethod, String url, String contentType, String data, Map<String, String> headers) {
        if (url == null || url.length() <= 0) {
            Log.w(TAG, "Failed on http request, url is null");
            return null;
        }

        URL urlObject;
        try {
            urlObject = new URL(url);
        } catch (MalformedURLException e) {
            Log.d(TAG, "Failed to parsing url, error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }

        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) urlObject.openConnection();
            switch (httpMethod) {
            case HTTP_METHOD_GET:
                // default is get
                break;
            case HTTP_METHOD_POST:
                urlConnection.setRequestMethod("POST");
                break;
            case HTTP_METHOD_PUT:
                urlConnection.setRequestMethod("PUT");
                break;
            case HTTP_METHOD_DELETE:
                urlConnection.setRequestMethod("DELETE");
                break;
            default:
                Log.d(TAG, "Failed on http request, unknown http method: " + httpMethod);
                return null;
            }

            // set content type if needed
            if (contentType != null) {
                urlConnection.setRequestProperty("Content-Type", contentType);
            }
            // set custom headers
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    urlConnection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            // set timeout
            urlConnection.setConnectTimeout(10000);
            urlConnection.setReadTimeout(20000);

            // write post/put data if needed
            if (data != null) {
                urlConnection.setDoOutput(true);
                OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                out.write(data.getBytes());
                out.flush();
                out.close();
            }

            lastResponseCode = urlConnection.getResponseCode();
            if (urlConnection.getResponseCode() < 200 || urlConnection.getResponseCode() >= 300) {
                Log.d(TAG, "Failed on http request, response code: " + urlConnection.getResponseCode());
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                InputStream in = new BufferedInputStream(urlConnection.getErrorStream());
                byte[] buffer = new byte [BUFFER_SIZE];
                int readBytes = 0;
                while ((readBytes = in.read(buffer)) != -1) {
                    output.write(buffer, 0, readBytes);
                }
                in.close();
                Log.d(TAG, "Error string: " + output.toString());
                return null;
            } else {
                //Log.d(TAG, "Http request success, response code: " + urlConnection.getResponseCode());
                //Log.d(TAG, "Get content length: " + urlConnection.getContentLength());
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                byte[] buffer = new byte [BUFFER_SIZE];
                int readBytes = 0;
                while ((readBytes = in.read(buffer)) != -1) {
                    output.write(buffer, 0, readBytes);
                }
                in.close();
                if (output.size() <= 0) {
                    return null;
                }
                return output;
            }
        } catch (IOException e) {
            Log.d(TAG, "Failed on reading http response, error: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
