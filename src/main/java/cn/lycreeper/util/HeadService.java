package cn.lycreeper.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minecraft 玩家头像服务
 * 支持官方 API 获取玩家皮肤和头像
 */
public class HeadService {
    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // 缓存
    private static final Map<String, BufferedImage> HEAD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<Color>> SKIN_COLOR_CACHE = new ConcurrentHashMap<>();

    // 史蒂夫默认 UUID
    private static final String STEVE_UUID = "8667ba71b85a4004af54457a9734eed7";
    private static final String ALEX_UUID = "9c26a132e84b4b0dae3beab0e5c1b23b";

    private static final int HEAD_SIZE = 8; // 头部大小 8x8 像素

    /**
     * 获取玩家正面头像
     * @param uuid 玩家 UUID（可带或不带连字符）
     * @param size 头像大小（8, 16, 32, 64, 128, 256, 512）
     * @param overlay 是否显示装备层（头盔等）
     * @return 头像 BufferedImage
     */
    public static CompletableFuture<BufferedImage> getHeadFrontAsync(String uuid, int size, boolean overlay) {
        String normalizedUuid = normalizeUuid(uuid);
        String cacheKey = normalizedUuid + "_" + size + "_" + overlay;

        // 检查缓存
        if (HEAD_CACHE.containsKey(cacheKey)) {
            return CompletableFuture.completedFuture(HEAD_CACHE.get(cacheKey));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage head = fetchHeadFromOfficial(normalizedUuid, size, overlay);
                if (head == null) {
                    head = fetchHeadFromFallback(normalizedUuid, size);
                }
                if (head == null) {
                    head = getDefaultSteveHead(size);
                }
                HEAD_CACHE.put(cacheKey, head);
                return head;
            } catch (Exception e) {
                e.printStackTrace();
                return getDefaultSteveHead(size);
            }
        });
    }

    /**
     * 获取玩家正面头像（简化版）
     */
    public static CompletableFuture<BufferedImage> getHeadFrontAsync(String uuid, int size) {
        return getHeadFrontAsync(uuid, size, true);
    }

    /**
     * 获取玩家头像（默认大小 64x64）
     */
    public static CompletableFuture<BufferedImage> getHeadFrontAsync(String uuid) {
        return getHeadFrontAsync(uuid, 64, true);
    }

    /**
     * 获取皮肤的 8x8 像素区域颜色网格
     * @param uuid 玩家 UUID
     * @return 包含 64 个颜色的列表（8x8 网格）
     */
    public static CompletableFuture<List<Color>> getSkinColorGridAsync(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        String cacheKey = normalizedUuid + "_colors";

        if (SKIN_COLOR_CACHE.containsKey(cacheKey)) {
            return CompletableFuture.completedFuture(SKIN_COLOR_CACHE.get(cacheKey));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage skin = fetchSkinImage(normalizedUuid);
                if (skin == null) {
                    return getDefaultSteveColors();
                }
                List<Color> colors = extractColorGrid(skin);
                SKIN_COLOR_CACHE.put(cacheKey, colors);
                return colors;
            } catch (Exception e) {
                e.printStackTrace();
                return getDefaultSteveColors();
            }
        });
    }

    /**
     * 使用颜色网格创建像素风格头像
     * @param uuid 玩家 UUID
     * @param pixelSize 每个像素块的大小
     * @return 像素风格头像
     */
    public static CompletableFuture<BufferedImage> createPixelArtHeadAsync(String uuid, int pixelSize) {
        return getSkinColorGridAsync(uuid).thenApply(colors -> {
            if (colors == null || colors.size() != 64) {
                return getDefaultSteveHead(8 * pixelSize);
            }

            int imageSize = 8 * pixelSize;
            BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();

            for (int gridY = 0; gridY < 8; gridY++) {
                for (int gridX = 0; gridX < 8; gridX++) {
                    Color color = colors.get(gridY * 8 + gridX);
                    g.setColor(color);
                    g.fillRect(gridX * pixelSize, gridY * pixelSize, pixelSize, pixelSize);
                }
            }

            g.dispose();
            return image;
        });
    }

    /**
     * 创建纯色头像
     */
    public static BufferedImage createSolidColorHead(int size, Color color) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, size, size);
        g.dispose();
        return image;
    }

    /**
     * 获取默认史蒂夫头像
     */
    public static BufferedImage getDefaultSteveHead(int size) {
        try {
            // 尝试从 Crafatar 获取
            String url = "https://crafatar.com/avatars/" + STEVE_UUID + "?size=" + size;
            BufferedImage image = ImageIO.read(URI.create(url).toURL());
            if (image != null) return image;
        } catch (Exception e) {
            // 忽略，使用内置默认头像
        }
        return createSolidColorHead(size, new Color(156, 124, 84));
    }

    /**
     * 清空缓存
     */
    public static void clearCache() {
        HEAD_CACHE.clear();
        SKIN_COLOR_CACHE.clear();
    }

    /**
     * 获取缓存大小
     */
    public static int getHeadCacheSize() {
        return HEAD_CACHE.size();
    }

    public static int getColorCacheSize() {
        return SKIN_COLOR_CACHE.size();
    }

    // ========== 私有方法 ==========

    /**
     * 标准化 UUID（移除连字符，转为小写）
     */
    private static String normalizeUuid(String uuid) {
        if (uuid == null) return STEVE_UUID;
        return uuid.toLowerCase().replace("-", "");
    }

    /**
     * 从官方 API 获取头像
     */
    private static BufferedImage fetchHeadFromOfficial(String uuid, int size, boolean overlay) {
        try {
            // 1. 获取皮肤 URL
            String skinUrl = getSkinUrl(uuid);
            if (skinUrl == null) return null;

            // 2. 下载皮肤图片
            BufferedImage skin = ImageIO.read(URI.create(skinUrl).toURL());
            if (skin == null) return null;

            // 3. 裁剪头部区域
            // Minecraft 皮肤：头部在 (8,8) 到 (16,16) 区域
            int headSize = 8;
            BufferedImage head = skin.getSubimage(8, 8, headSize, headSize);

            // 4. 叠加装备层（如果皮肤有且需要）
            if (overlay && skin.getHeight() > 32) {
                // 新版皮肤有覆盖层，位置在 (40,8)
                BufferedImage overlayLayer = skin.getSubimage(40, 8, headSize, headSize);
                Graphics2D g = head.createGraphics();
                g.drawImage(overlayLayer, 0, 0, null);
                g.dispose();
            }

            // 5. 缩放到目标大小
            if (size != headSize) {
                BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(head, 0, 0, size, size, null);
                g.dispose();
                head = scaled;
            }

            return head;
        } catch (Exception e) {
            System.err.println("获取官方头像失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取皮肤图片 URL
     */
    private static String getSkinUrl(String uuid) throws Exception {
        String profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(profileUrl))
                .header("User-Agent", "KKMCL/1.0.0")
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("properties")) return null;

        var properties = json.getAsJsonArray("properties");
        if (properties.isEmpty()) return null;

        String base64Value = properties.get(0).getAsJsonObject().get("value").getAsString();
        byte[] decoded = Base64.getDecoder().decode(base64Value);
        String decodedJson = new String(decoded);

        JsonObject textureJson = JsonParser.parseString(decodedJson).getAsJsonObject();
        return textureJson.getAsJsonObject("textures")
                .getAsJsonObject("SKIN")
                .get("url").getAsString();
    }

    /**
     * 获取皮肤图片
     */
    private static BufferedImage fetchSkinImage(String uuid) {
        try {
            String skinUrl = getSkinUrl(uuid);
            if (skinUrl == null) return null;
            return ImageIO.read(URI.create(skinUrl).toURL());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从备选服务获取头像
     */
    private static BufferedImage fetchHeadFromFallback(String uuid, int size) {
        String[] fallbackUrls = {
                "https://crafatar.com/avatars/" + uuid + "?size=" + size + "&overlay",
                "https://minotar.net/helm/" + uuid + "/" + size + ".png",
                "https://mc-heads.net/avatar/" + uuid + "/" + size
        };

        for (String url : fallbackUrls) {
            try {
                BufferedImage image = ImageIO.read(URI.create(url).toURL());
                if (image != null) return image;
            } catch (Exception e) {
                // 忽略，尝试下一个
            }
        }
        return null;
    }

    /**
     * 从皮肤位图中提取 8x8 颜色网格
     */
    private static List<Color> extractColorGrid(BufferedImage skin) {
        List<Color> colors = new ArrayList<>();

        for (int y = 0; y < HEAD_SIZE; y++) {
            for (int x = 0; x < HEAD_SIZE; x++) {
                // 获取该区域的主要颜色
                Color pixelColor = getDominantColorInRegion(skin, x * HEAD_SIZE, y * HEAD_SIZE, HEAD_SIZE, HEAD_SIZE);
                colors.add(pixelColor);
            }
        }

        return colors;
    }

    /**
     * 获取区域内的主要颜色（平均值）
     */
    private static Color getDominantColorInRegion(BufferedImage image, int startX, int startY, int width, int height) {
        long totalR = 0, totalG = 0, totalB = 0, totalA = 0;
        int pixelCount = 0;

        int endX = Math.min(startX + width, image.getWidth());
        int endY = Math.min(startY + height, image.getHeight());

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int rgb = image.getRGB(x, y);
                int a = (rgb >> 24) & 0xFF;

                // 忽略透明像素
                if (a > 0) {
                    totalR += (rgb >> 16) & 0xFF;
                    totalG += (rgb >> 8) & 0xFF;
                    totalB += rgb & 0xFF;
                    totalA += a;
                    pixelCount++;
                }
            }
        }

        if (pixelCount == 0) {
            return new Color(0, 0, 0, 0);
        }

        return new Color(
                (int) (totalR / pixelCount),
                (int) (totalG / pixelCount),
                (int) (totalB / pixelCount),
                (int) (totalA / pixelCount)
        );
    }

    /**
     * 默认史蒂夫皮肤颜色（8x8 网格）
     */
    private static List<Color> getDefaultSteveColors() {
        List<Color> colors = new ArrayList<>();

        // 预设的史蒂夫头部颜色
        Color brown = new Color(78, 56, 40);
        Color skin = new Color(156, 124, 84);
        Color transparent = new Color(0, 0, 0, 0);

        int[][] colorMap = {
                {0,0,1,1,1,1,0,0},
                {0,0,1,1,1,1,0,0},
                {1,1,2,2,2,2,1,1},
                {1,2,2,2,2,2,2,1},
                {1,2,2,2,2,2,2,1},
                {1,2,2,2,2,2,2,1},
                {0,1,1,1,1,1,1,0},
                {0,0,1,1,1,1,0,0}
        };

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                switch (colorMap[y][x]) {
                    case 1: colors.add(brown); break;
                    case 2: colors.add(skin); break;
                    default: colors.add(transparent); break;
                }
            }
        }

        return colors;
    }
}