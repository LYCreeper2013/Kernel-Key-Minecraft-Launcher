package cn.lycreeper.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Desktop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class MicrosoftAuthenticator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CLIENT_ID = "8cdd8deb-b94e-444f-a348-acba988da0bd";

    private final LoginCallback callback;
    private volatile boolean cancelled = false;

    public interface LoginCallback {
        void onLog(String message);
        void onWaitingForCode(String userCode, String verificationUri);
        void onSuccess(MicrosoftAccount account);
        void onError(String error);
    }

    public static class MicrosoftAccount {
        public String username;
        public String uuid;
        public String accessToken;
        public String refreshToken;
        public Instant expiryTime;

        public boolean isTokenExpired() {
            return Instant.now().isAfter(expiryTime);
        }
    }

    private static class DeviceCodeResponse {
        String device_code;
        String user_code;
        String verification_uri;
        int expires_in;
        int interval;
    }

    private static class TokenResponse {
        String access_token;
        String refresh_token;
        int expires_in;
    }

    private static class XboxLiveResponse {
        String Token;
        DisplayClaims DisplayClaims;
    }

    private static class DisplayClaims {
        XuiClaim[] xui;
    }

    private static class XuiClaim {
        String uhs;
    }

    private static class XstsResponse {
        String Token;
    }

    private static class MinecraftLoginResponse {
        String access_token;
        int expires_in;
    }

    private static class MinecraftProfileResponse {
        String id;
        String name;
    }

    public MicrosoftAuthenticator(LoginCallback callback) {
        this.callback = callback;
    }

    public void cancel() {
        cancelled = true;
    }

    public void startLogin() {
        cancelled = false;
        new Thread(() -> {
            try {
                doLogin();
            } catch (Exception e) {
                callback.onError("登录失败: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void doLogin() throws Exception {
        // 1. 获取设备代码
        callback.onLog("正在获取设备代码...");
        DeviceCodeResponse deviceCode = getDeviceCode();
        if (deviceCode == null) {
            callback.onError("获取设备代码失败");
            return;
        }

        callback.onLog("设备代码: " + deviceCode.user_code);
        callback.onWaitingForCode(deviceCode.user_code, deviceCode.verification_uri);

        // 自动打开浏览器
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(deviceCode.verification_uri));
                callback.onLog("已自动打开浏览器，请输入验证码: " + deviceCode.user_code);
            }
        } catch (Exception e) {
            callback.onLog("无法自动打开浏览器，请手动访问: " + deviceCode.verification_uri);
        }

        // 2. 轮询获取令牌
        callback.onLog("等待用户授权...");
        TokenResponse token = pollForToken(deviceCode);
        if (token == null) {
            if (cancelled) {
                callback.onError("登录已取消");
            } else {
                callback.onError("授权超时或失败");
            }
            return;
        }

        callback.onLog("获取微软令牌成功");

        // 3. XBox Live 验证
        callback.onLog("正在验证 Xbox Live...");
        XboxLiveResponse xboxResponse = authenticateXboxLive(token.access_token);
        if (xboxResponse == null) {
            callback.onError("Xbox Live 验证失败");
            return;
        }

        // 4. XSTS 验证
        callback.onLog("正在验证 XSTS...");
        XstsResponse xstsResponse = authenticateXsts(xboxResponse.Token);
        if (xstsResponse == null) {
            callback.onError("XSTS 验证失败");
            return;
        }

        // 5. 获取 Minecraft 令牌
        callback.onLog("正在获取 Minecraft 令牌...");
        String uhs = xboxResponse.DisplayClaims.xui[0].uhs;
        MinecraftLoginResponse minecraftLogin = loginWithXbox(uhs, xstsResponse.Token);
        if (minecraftLogin == null) {
            callback.onError("获取 Minecraft 令牌失败");
            return;
        }

        // 6. 获取玩家档案
        callback.onLog("正在获取玩家档案...");
        MinecraftProfileResponse profile = getMinecraftProfile(minecraftLogin.access_token);
        if (profile == null) {
            callback.onError("未购买 Minecraft 或获取档案失败");
            return;
        }

        // 7. 构建账户信息
        MicrosoftAccount account = new MicrosoftAccount();
        account.username = profile.name;
        account.uuid = profile.id;
        account.accessToken = minecraftLogin.access_token;
        account.refreshToken = token.refresh_token;
        account.expiryTime = Instant.now().plusSeconds(minecraftLogin.expires_in);

        callback.onLog("登录成功: " + profile.name);
        callback.onSuccess(account);
    }

    private DeviceCodeResponse getDeviceCode() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        String body = "client_id=" + CLIENT_ID +
                "&scope=XboxLive.signin%20offline_access";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return GSON.fromJson(response.body(), DeviceCodeResponse.class);
        } else {
            callback.onLog("获取设备代码失败: " + response.statusCode());
            return null;
        }
    }

    private TokenResponse pollForToken(DeviceCodeResponse deviceCode) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        int attempts = 0;
        int maxAttempts = deviceCode.expires_in / deviceCode.interval;

        while (attempts < maxAttempts && !cancelled) {
            Thread.sleep(deviceCode.interval * 1000L);

            String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                    "&client_id=" + CLIENT_ID +
                    "&device_code=" + deviceCode.device_code;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return GSON.fromJson(response.body(), TokenResponse.class);
            } else {
                String error = "";
                try {
                    JsonObject errorObj = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (errorObj.has("error")) {
                        error = errorObj.get("error").getAsString();
                    }
                } catch (Exception e) {
                    error = "unknown";
                }

                if ("authorization_pending".equals(error)) {
                    attempts++;
                    callback.onLog("等待授权中... (" + attempts + "/" + maxAttempts + ")");
                } else if ("slow_down".equals(error)) {
                    Thread.sleep(5000);
                    attempts++;
                } else {
                    callback.onLog("获取令牌失败: " + error);
                    return null;
                }
            }
        }
        return null;
    }

    private XboxLiveResponse authenticateXboxLive(String microsoftToken) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + microsoftToken);

        JsonObject payload = new JsonObject();
        payload.add("Properties", properties);
        payload.addProperty("RelyingParty", "http://auth.xboxlive.com");
        payload.addProperty("TokenType", "JWT");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

            XboxLiveResponse result = new XboxLiveResponse();
            result.Token = json.get("Token").getAsString();

            if (json.has("DisplayClaims")) {
                JsonObject displayClaims = json.getAsJsonObject("DisplayClaims");
                if (displayClaims != null && displayClaims.has("xui")) {
                    JsonArray xuiArray = displayClaims.getAsJsonArray("xui");
                    if (xuiArray != null && xuiArray.size() > 0) {
                        JsonObject xui = xuiArray.get(0).getAsJsonObject();
                        DisplayClaims claims = new DisplayClaims();
                        XuiClaim claim = new XuiClaim();
                        claim.uhs = xui.get("uhs").getAsString();
                        claims.xui = new XuiClaim[]{claim};
                        result.DisplayClaims = claims;
                    }
                }
            }
            return result;
        } else {
            callback.onLog("Xbox Live 验证失败: " + response.statusCode());
            return null;
        }
    }

    private XstsResponse authenticateXsts(String xboxToken) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        JsonArray userTokens = new JsonArray();
        userTokens.add(xboxToken);

        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        properties.add("UserTokens", userTokens);

        JsonObject payload = new JsonObject();
        payload.add("Properties", properties);
        payload.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        payload.addProperty("TokenType", "JWT");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            XstsResponse result = new XstsResponse();
            result.Token = json.get("Token").getAsString();
            return result;
        } else {
            callback.onLog("XSTS 验证失败: " + response.statusCode());
            return null;
        }
    }

    private MinecraftLoginResponse loginWithXbox(String uhs, String xstsToken) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        JsonObject payload = new JsonObject();
        payload.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return GSON.fromJson(response.body(), MinecraftLoginResponse.class);
        } else {
            callback.onLog("Minecraft 登录失败: " + response.statusCode());
            return null;
        }
    }

    private MinecraftProfileResponse getMinecraftProfile(String accessToken) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return GSON.fromJson(response.body(), MinecraftProfileResponse.class);
        } else {
            callback.onLog("获取玩家档案失败: " + response.statusCode());
            return null;
        }
    }
}