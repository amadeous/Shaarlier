package com.dimtion.shaarlier.network;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.util.Log;
import android.webkit.URLUtil;

import com.dimtion.shaarlier.models.ShaarliAccount;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public abstract class NetworkUtils {
    protected static final int TIME_OUT = 60_000; // Better for mobile connections

    private final static String LOGGER_NAME = NetworkUtils.class.getSimpleName();

    private static final String[] DESCRIPTION_SELECTORS = {
            "meta[property=og:description]",
            "meta[name=description]",
            "meta[name=twitter:description]",
            "meta[name=mastodon:description]",
    };

    /**
     * Check if a string is an url
     * TODO : unit test on this, I'm not quite sure it is perfect...
     */
    public static boolean isUrl(String url) {
        return URLUtil.isValidUrl(url) && !"http://".equals(url);
    }

    /**
     * Change something which is close to a url to something that is really one
     */
    public static String toUrl(String givenUrl) {
        String finalUrl = givenUrl;
        String protocol = "http://";  // Default value
        if ("".equals(givenUrl)) {
            return givenUrl;  // Edge case, maybe need some discussion
        }

        if (!finalUrl.endsWith("/")) {
            finalUrl += '/';
        }

        if (!(finalUrl.startsWith("http://") || finalUrl.startsWith("https://"))) {
            finalUrl = protocol + finalUrl;
        }

        return finalUrl;
    }

    /**
     * Method to test the network connection
     *
     * @return true if the device is connected to the network
     */
    public static boolean testNetwork(@NonNull Activity parentActivity) {
        ConnectivityManager connMgr = (ConnectivityManager) parentActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Static method to load the title of a web page
     *
     * @param url the url of the web page
     * @return "" if there is an error, the page title in other cases
     */
    public static String[] loadTitleAndDescription(@NonNull String url) {
        String title = "";
        String description = "";
        final Document pageResp;
        try {
            Log.i(LOGGER_NAME, "Loading url: " + url);
            pageResp = Jsoup.connect(url)
                    .followRedirects(true)
                    .execute()
                    .parse();
            title = pageResp.title();
        } catch (final Exception e) {
            // Just abandon the task if there is a problem
            Log.e(LOGGER_NAME, "Failed to load title: " + e);
            return new String[]{title, description};
        }

        // Many ways to get the description
        for (String selector : NetworkUtils.DESCRIPTION_SELECTORS) {
            try {
                description = pageResp.head().select(selector).first().attr("content");
            } catch (final Exception e) {
                Log.e(LOGGER_NAME, "Failed to load description: " + e);
            }
            if (!"".equals(description)) {
                break;
            }
        }
        return new String[]{title, description};
    }

    /**
     * Select the correct network manager based on the passed account
     */
    public static NetworkManager getNetworkManager(ShaarliAccount account) {
        switch (account.getAuthMethod()) {
            case ShaarliAccount.AUTH_METHOD_MOCK:
                Log.i(LOGGER_NAME, "Selected MockNetworkManager (forced)");
                return new MockNetworkManager();
            case ShaarliAccount.AUTH_METHOD_PASSWORD:
                Log.i(LOGGER_NAME, "Selected PasswordNetworkManager (forced)");
                return new PasswordNetworkManager(account);
            case ShaarliAccount.AUTH_METHOD_RESTAPI:
                Log.i(LOGGER_NAME, "Selected RestAPiNetworkManager (forced)");
                return new RestAPINetworkManager(account);
            case ShaarliAccount.AUTH_METHOD_AUTO:
                if (1 == 0) { // Enabled only for debugging purposes
                    Log.i(LOGGER_NAME, "Selected MockNetworkManager (auto)");
                    return new MockNetworkManager();
                }

                if (account.getRestAPIKey() != null && account.getRestAPIKey().length() > 0) {
                    Log.i(LOGGER_NAME, "Selected RestAPiNetworkManager (auto)");
                    return new RestAPINetworkManager(account);
                } else {
                    Log.i(LOGGER_NAME, "Selected PasswordNetworkManager (auto)");
                    return new PasswordNetworkManager(account);
                }
            default:
                throw new RuntimeException("Invalid shaarli auth method");
        }
    }
}
