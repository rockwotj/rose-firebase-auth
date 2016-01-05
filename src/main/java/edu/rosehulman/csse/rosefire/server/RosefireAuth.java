package edu.rosehulman.csse.rosefire.server;


import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;

/**
 * <p>The class that authenticates a Rose-Hulman User with Firebase for you.</p>
 * <p/>
 * <code>
 * RosefireAuth roseAuth = new RosefireAuth(fb, "REGISTRY_TOKEN");
 * String authToken = roseAuth.getToken("rockwotj@rose-hulman.ed", "Pa$sW0rd");
 * // Now use the token with the Verifier
 *
 * <p/>
 * <p/>
 * </code>
 */
public class RosefireAuth {

    public static boolean DEBUG = false;

    private final String mRoseAuthServiceUrl;
    private final String mRegistryToken;

    /**
     * <p>
     * Create a RoseFirebaseAuthenticator with https://rosefire.csse.rose-hulman.edu as the
     * the url that the server is running on.
     * </p>
     *
     * @param registryToken The registryToken for your app; generated from
     *                      the server's registration page.
     */
    public RosefireAuth(String registryToken) {
        this(registryToken, "https://rosefire.csse.rose-hulman.edu");
    }

    /**
     * <p>
     * Create a RoseFirebaseAuthenticator with a custom url that the server is running on.
     * </p>
     *
     * @param registryToken  The registryToken for your app; generated from
     *                       the server's registration page.
     * @param authServiceUrl The url that the Rose authentication token is running at.
     */
    public RosefireAuth(String registryToken, String authServiceUrl) {
        mRegistryToken = registryToken;
        mRoseAuthServiceUrl = authServiceUrl + "/api/";
        if (DEBUG) {
            System.out.println("URL base endpoint: " + mRoseAuthServiceUrl);
        }
    }

    /**
     * <p>
     * Authenticate the user with Rose-Hulman credentials given a Rose-Hulman email and password.
     * </p>
     * <p>
     * This method is async and the result will be handled in the handler's callbacks
     * </p>
     *
     * @param email    A valid Rose-Hulman email.
     * @param password A valid Rose-Hulman password for the email.
     */
    public String getToken(String email, String password) throws RosefireError {
        return getToken(email, password, null);
    }

    /**
     * <p>
     * Authenticate the user with Rose-Hulman credentials given a Rose-Hulman email and password,
     * with custom options for the auth token.
     * </p>
     * <p>
     * This method is async and the result will be handled in the handler's callbacks
     * </p>
     *
     * @param email    A valid Rose-Hulman email.
     * @param password A valid Rose-Hulman password for the email.
     * @param options  The options for the auth token that is generated on the server.
     */
    public String getToken(String email, String password, TokenOptions options) throws RosefireError {
        if (DEBUG) {
            System.out.println("Authenticating user " + email);
        }
        JSONObject params = new JSONObject();
        try {
            params.put("email", email);
            params.put("password", password);
            params.put("registryToken", mRegistryToken);
            if (options != null) {
                JSONObject rosefireOptions = new JSONObject();
                if (options.isAdmin() != null) {
                    rosefireOptions.put("admin", options.isAdmin().booleanValue());
                }
                if (options.getExpires() != null) {
                    rosefireOptions.put("expires", options.getExpires().intValue());
                }
                if (options.getNotBefore() != null) {
                    rosefireOptions.put("notBefore", options.getNotBefore().intValue());
                }
                if (options.queryForGroup() != null) {
                    rosefireOptions.put("group", options.queryForGroup().booleanValue());
                }
                params.put("options", rosefireOptions);
            }
        } catch (JSONException e) {
            throw new RosefireError("Unable to create json parameters", e);
        }
        String response;

        response = makeRequest("auth", params.toString());

        if (DEBUG) {
            System.out.println("Request response: " + response);
        }
        if (response == null || response.isEmpty()) {
            return null;
        }
        JSONObject data;
        try {
            data = new JSONObject(response);
        } catch (JSONException e) {
            throw new RosefireError("Unable to parse result!", e);
        }
        try {
            if (DEBUG) {
                System.out.println("Authentation for " + data.getString("username"));
            }
            return data.getString("token");
        } catch (JSONException e) {
            throw new RosefireError("Invalid response!", e);
        }
    }

    /**
     * <p>The authentication token options that will be generated on the server. </p>
     * <p>For more details see <a href="https://github.com/rockwotj/rose-firebase-auth#post-apiauth">
     * https://github.com/rockwotj/rose-firebase-auth#post-apiauth</a></p>
     */
    public static class TokenOptions {
        private Long expires;
        private Long notBefore;
        private Boolean admin;
        private Boolean group;

        /**
         * Create an empty options object
         */
        public TokenOptions() {
            this(null, null, null, null);
        }

        /**
         * Create an options object with the given options.
         *
         * @param admin     If true, then all security rules are disabled for this user.
         *                  This can only be true for the user who the token is registred with.
         * @param expires   A timestamp of when the token is invalid.
         * @param notBefore A timestamp of when the token should start being valid.
         * @param group If true, then 'STUDENT' or 'INSTRUCTOR' group will be looked
         *              up using LDAP.
         */
        public TokenOptions(Long expires, Long notBefore, Boolean admin, Boolean group) {
            this.expires = expires;
            this.notBefore = notBefore;
            this.admin = admin;
            this.group = group;
        }

        public Long getExpires() {
            return expires;
        }

        /**
         * Set when the auth token expires.
         *
         * @param expires A timestamp of when the token is invalid.
         */
        public void setExpires(Long expires) {
            this.expires = expires;
        }

        public Long getNotBefore() {
            return notBefore;
        }

        /**
         * Set when the auth token starts being valid.
         *
         * @param notBefore A timestamp of when the token should start being valid.
         */
        public void setNotBefore(Long notBefore) {
            this.notBefore = notBefore;
        }

        /**
         * Set if the user has all of the firebase rules disabled.
         *
         * @param admin If true, then all security rules are disabled for this user.
         *              This can only be true for the user who the token is registred with.
         */
        public void setAdmin(Boolean admin) {
            this.admin = admin;
        }

        public Boolean isAdmin() {
            return admin;
        }

        /**
         *
         * Set if the payload should include the user's group.
         *
         * @param group If true, then 'STUDENT' or 'INSTRUCTOR' group will be looked
         *              up using LDAP. Please note that is causes the request to be
         *              about four times longer with this set to true.
         */
        public void setGroup(Boolean group) {
            this.group = group;
        }

        public Boolean queryForGroup() {
            return group;
        }
    }

    private String makeRequest(String endpoint, String json) throws RosefireError {
        HttpsURLConnection urlConnection = null;
        String url = mRoseAuthServiceUrl + endpoint + "/";
        if (DEBUG) {
            System.out.println("JSON data for request at " + url + " is: " + json);
        }
        String data = json;
        String result;

        try {
            urlConnection = (HttpsURLConnection) ((new URL(url).openConnection()));

            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setRequestMethod("POST");
            urlConnection.connect();

            OutputStream outputStream = urlConnection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8"));
            writer.write(data);
            writer.close();
            outputStream.close();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));

            String line;
            StringBuilder sb = new StringBuilder();

            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }

            bufferedReader.close();
            result = sb.toString();
        } catch (Exception e) {
            int code = 0;
            try {
                code = urlConnection.getResponseCode();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            if (DEBUG) {
                System.out.println("Error code for " + url + " is: " + code);
            }
            throw new RosefireError(code == 400 ? "Invalid Rose-Hulman Credentials!" : "Network error!");
        }

        return result;
    }

}
