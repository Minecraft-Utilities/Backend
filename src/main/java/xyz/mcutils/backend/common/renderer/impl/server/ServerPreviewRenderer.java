package xyz.mcutils.backend.common.renderer.impl.server;

import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.*;
import xyz.mcutils.backend.common.color.ColorUtils;
import xyz.mcutils.backend.common.color.HexColorResult;
import xyz.mcutils.backend.common.renderer.Renderer;
import xyz.mcutils.backend.model.server.MinecraftServer;
import xyz.mcutils.backend.model.server.Players;
import xyz.mcutils.backend.model.server.java.JavaMinecraftServer;
import xyz.mcutils.backend.service.ServerService;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Slf4j
public class ServerPreviewRenderer extends Renderer<MinecraftServer> {
    public static final ServerPreviewRenderer INSTANCE = new ServerPreviewRenderer();

    // Minecraft ServerSelectionList.OnlineServerEntry dimensions at 1x
    private static final int SCALE = 3;
    private static final int ROW_WIDTH = 305;
    private static final int ROW_HEIGHT = 32;
    private static final int ICON_SIZE = 32;
    private static final int ICON_TEXT_GAP = 3;
    private static final int STATUS_ICON_WIDTH = 10;
    private static final int STATUS_ICON_HEIGHT = 8;
    private static final int RIGHT_SPACING = 5;

    private static BufferedImage SERVER_BACKGROUND;
    private static BufferedImage PING_ICON;

    static {
        try {
            SERVER_BACKGROUND = ImageIO.read(new ByteArrayInputStream(Main.class.getResourceAsStream("/icons/server_background.png").readAllBytes()));
            PING_ICON = ImageIO.read(new ByteArrayInputStream(Main.class.getResourceAsStream("/icons/ping.png").readAllBytes()));
        } catch (Exception ex) {
            log.error("Failed to load server preview assets", ex);
        }
    }

    private final int width = ROW_WIDTH * SCALE;
    private final int height = ROW_HEIGHT * SCALE;
    private final int iconSize = ICON_SIZE * SCALE;
    private final int iconTextGap = ICON_TEXT_GAP * SCALE;
    private final int statusIconWidth = STATUS_ICON_WIDTH * SCALE;
    private final int statusIconHeight = STATUS_ICON_HEIGHT * SCALE;
    private final int rightSpacing = RIGHT_SPACING * SCALE;

    @Override
    public BufferedImage render(MinecraftServer server, int size) {
        BufferedImage texture = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        BufferedImage favicon = getServerFavicon(server);
        BufferedImage background = SERVER_BACKGROUND;

        Graphics2D graphics = texture.createGraphics();

        // Draw the background
        for (int backgroundX = 0; backgroundX < width + background.getWidth(); backgroundX += background.getWidth()) {
            for (int backgroundY = 0; backgroundY < height + background.getHeight(); backgroundY += background.getHeight()) {
                graphics.drawImage(background, backgroundX, backgroundY, null);
            }
        }

        graphics.setColor(new Color(0, 0, 0, 80));
        graphics.fillRect(0, 0, width, height);

        // Layout (Minecraft OnlineServerEntry at 3x): server name at y+1, MOTD at y+12, y+12+9
        // Minecraft font height is 9 (8px glyph + 1). Our font ascent=7, so baseline = top + ascent*scale.
        int textX = iconSize + iconTextGap;
        int fontAscent = Fonts.MINECRAFT.ascent(); // 7 at 1x
        int motdLine1Top = 12 * SCALE;
        int motdLine2Top = 21 * SCALE; // 12 + 9

        // Draw favicon (96x96)
        BufferedImage faviconScaled = ImageUtils.resize(favicon, (double) iconSize / favicon.getWidth());
        graphics.drawImage(faviconScaled, 0, 0, iconSize, iconSize, null);

        // Draw server hostname (with shadow) - baseline = top + ascent*scale
        graphics.setColor(MinecraftColor.WHITE.toAwtColor());
        GraphicsUtils.drawStringWithStyle(graphics, Fonts.MINECRAFT, server.getHostname(), textX, SCALE + fontAscent * SCALE, true, false, false, SCALE);

        // Draw MOTD - 2 distinct raw lines, no wrapping (Minecraft uses font.split for overflow)
        String[] rawMotd = server.getMotd().raw();
        if (rawMotd != null && rawMotd.length > 0) {
            drawMotdLine(graphics, rawMotd[0], textX, motdLine1Top + fontAscent * SCALE);
            if (rawMotd.length > 1) {
                drawMotdLine(graphics, rawMotd[1], textX, motdLine2Top + fontAscent * SCALE);
            }
        }

        // Status area: ping icon at right, status text (player count) to its left
        int statusIconX = width - statusIconWidth - rightSpacing;
        BufferedImage pingIcon = ImageUtils.resize(PING_ICON, SCALE);
        graphics.drawImage(pingIcon, statusIconX, 0, statusIconWidth, statusIconHeight, null);

        Players players = server.getPlayers();
        String playersOnline = players.online() + "";
        String playersMax = players.max() + "";
        int onlineWidth = GraphicsUtils.stringWidthAtScale(Fonts.MINECRAFT, playersOnline, SCALE)
                + GraphicsUtils.stringWidthAtScale(Fonts.MINECRAFT, "/", SCALE)
                + GraphicsUtils.stringWidthAtScale(Fonts.MINECRAFT, playersMax, SCALE);
        int statusTextX = statusIconX - onlineWidth - rightSpacing;
        int statusTextY = SCALE + fontAscent * SCALE;

        graphics.setColor(MinecraftColor.GRAY.toAwtColor());
        statusTextX = GraphicsUtils.drawStringWithStyle(graphics, Fonts.MINECRAFT, playersOnline, statusTextX, statusTextY, true, false, false, SCALE);
        graphics.setColor(MinecraftColor.DARK_GRAY.toAwtColor());
        statusTextX = GraphicsUtils.drawStringWithStyle(graphics, Fonts.MINECRAFT, "/", statusTextX, statusTextY, true, false, false, SCALE);
        graphics.setColor(MinecraftColor.GRAY.toAwtColor());
        GraphicsUtils.drawStringWithStyle(graphics, Fonts.MINECRAFT, playersMax, statusTextX, statusTextY, true, false, false, SCALE);

        graphics.dispose();
        return ImageUtils.resize(texture, (double) size / width);
    }

    private void drawMotdLine(Graphics2D graphics, String line, int x, int y) {
        graphics.setColor(MinecraftColor.GRAY.toAwtColor()); // Minecraft MOTD default
        int index = 0;
        int drawX = x;
        boolean bold = false;
        boolean italic = false;

        while (index < line.length()) {
            int colorIndex = line.indexOf("§", index);
            if (colorIndex == -1) {
                String remaining = line.substring(index);
                GraphicsUtils.drawStringWithStyle(graphics, Fonts.MINECRAFT, remaining, drawX, y, true, bold, italic, SCALE);
                break;
            }

            String textBeforeColor = line.substring(index, colorIndex);
            drawX = GraphicsUtils.drawStringWithStyle(graphics, Fonts.MINECRAFT, textBeforeColor, drawX, y, true, bold, italic, SCALE);

            // §x§R§R§G§G§B§B or §#RRGGBB (gradient support)
            HexColorResult hexResult = ColorUtils.parseHexColor(line, colorIndex);
            if (hexResult != null) {
                graphics.setColor(hexResult.color());
                index = colorIndex + hexResult.charsConsumed();
            } else if (colorIndex + 1 < line.length()) {
                char colorCode = Character.toLowerCase(line.charAt(colorIndex + 1));
                switch (colorCode) {
                    case 'l' -> bold = true;
                    case 'o' -> italic = true;
                    case 'r' -> {
                        graphics.setColor(MinecraftColor.GRAY.toAwtColor());
                        bold = false;
                        italic = false;
                    }
                    default -> {
                        MinecraftColor mcColor = MinecraftColor.getByCode(colorCode);
                        if (mcColor != null) {
                            graphics.setColor(mcColor.toAwtColor());
                        }
                    }
                }
                index = colorIndex + 2;
            } else {
                index = colorIndex + 1;
            }
        }
    }

    /**
     * Get the favicon of a server.
     *
     * @param server the server to get the favicon of
     * @return the server favicon
     */
    public BufferedImage getServerFavicon(MinecraftServer server) {
        String favicon = null;

        if (server instanceof JavaMinecraftServer javaServer && javaServer.getFavicon() != null) {
            favicon = javaServer.getFavicon().getBase64();
        }

        if (favicon == null) {
            favicon = ServerService.DEFAULT_SERVER_ICON;
        }
        return ImageUtils.base64ToImage(favicon);
    }
}
