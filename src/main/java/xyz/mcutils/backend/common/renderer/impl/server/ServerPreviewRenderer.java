package xyz.mcutils.backend.common.renderer.impl.server;

import lombok.extern.log4j.Log4j2;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.ColorUtils;
import xyz.mcutils.backend.common.Fonts;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.renderer.Renderer;
import xyz.mcutils.backend.model.server.MinecraftServer;
import xyz.mcutils.backend.model.server.Players;
import xyz.mcutils.backend.model.server.java.JavaMinecraftServer;
import xyz.mcutils.backend.service.ServerService;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Log4j2
public class ServerPreviewRenderer extends Renderer<MinecraftServer> {
    public static final ServerPreviewRenderer INSTANCE = new ServerPreviewRenderer();


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

    private final int fontSize = Fonts.MINECRAFT.getSize();
    private final int width = 560;
    private final int height = 64 + 3 + 3;
    private final int padding = 3;

    @Override
    public BufferedImage render(MinecraftServer server, int size) {
        BufferedImage texture = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); // The texture to return
        BufferedImage favicon = getServerFavicon(server);
        BufferedImage background = SERVER_BACKGROUND;

        // Create the graphics for drawing
        Graphics2D graphics = texture.createGraphics();

        // For pixel fonts, use these rendering hints instead
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Set up the font
        graphics.setFont(Fonts.MINECRAFT);

        // Draw the background
        for (int backgroundX = 0; backgroundX < width + background.getWidth(); backgroundX += background.getWidth()) {
            for (int backgroundY = 0; backgroundY < height + background.getHeight(); backgroundY += background.getHeight()) {
                graphics.drawImage(background, backgroundX, backgroundY, null);
            }
        }

        // Add a semi-transparent overlay for more authentic Minecraft look
        graphics.setColor(new Color(0, 0, 0, 80));
        graphics.fillRect(0, 0, width, height);

        int y = fontSize + padding;
        int x = 64 + 8;
        int initialX = x; // Store the initial value of x

        // Draw the favicon
        graphics.drawImage(favicon, padding, padding, null);

        // Draw the server hostname
        graphics.setColor(Color.WHITE);
        graphics.drawString(server.getHostname(), initialX, y);

        // Draw the server motd
        x += 12;
        y += fontSize + (padding * 2) + 2;
        for (String line : server.getMotd().getRaw()) {
            int index = 0;
            int colorIndex = line.indexOf("§");

            while (colorIndex != -1) {
                // Draw text before color code
                String textBeforeColor = line.substring(index, colorIndex);
                graphics.drawString(textBeforeColor, x, y);
                // Calculate width of text before color code
                int textWidth = graphics.getFontMetrics().stringWidth(textBeforeColor);
                // Move x position to after the drawn text
                x += textWidth;
                // Set color based on color code
                char colorCode = Character.toLowerCase(line.charAt(colorIndex + 1));

                // Set the color and font style
                switch (colorCode) {
                    case 'l':
                        graphics.setFont(Fonts.MINECRAFT_BOLD);
                        break;
                    case 'o':
                        graphics.setFont(Fonts.MINECRAFT_ITALIC);
                        break;
                    case 'r': // Reset formatting
                        graphics.setColor(Color.GRAY);
                        graphics.setFont(Fonts.MINECRAFT);
                        break;
                    default: {
                        try {
                            Color color = ColorUtils.getMinecraftColor(colorCode);
                            graphics.setColor(color);
                            graphics.setFont(Fonts.MINECRAFT);
                        } catch (Exception ignored) {
                            // Unknown color, can ignore the error
                        }
                    }
                }

                // Move index to after the color code
                index = colorIndex + 2;
                // Find next color code
                colorIndex = line.indexOf("§", index);
            }
            // Draw remaining text
            String remainingText = line.substring(index);
            graphics.drawString(remainingText, x, y);
            // Move to the next line
            y += fontSize + padding;
            // Reset x position for the next line
            x = initialX + 12; // Reset x
        }

        // Ensure the font is reset
        graphics.setFont(Fonts.MINECRAFT);

        // Render the ping
        BufferedImage pingIcon = ImageUtils.resize(PING_ICON, 2);
        x = width - pingIcon.getWidth() - padding - 2;
        graphics.drawImage(pingIcon, x, padding, null);

        // Reset the y position
        y = fontSize + padding;

        // Render the player count
        Players players = server.getPlayers();
        String playersOnline = players.getOnline() + "";
        String playersMax = players.getMax() + "";

        // Calculate the width of each player count element
        int maxWidth = graphics.getFontMetrics().stringWidth(playersMax);
        int slashWidth = graphics.getFontMetrics().stringWidth("/");
        int onlineWidth = graphics.getFontMetrics().stringWidth(playersOnline);

        // Calculate the total width of the player count string
        int totalWidth = maxWidth + slashWidth + onlineWidth;

        // Calculate the starting x position
        int startX = (width - totalWidth) - pingIcon.getWidth() - 12;

        // Render the player count elements
        graphics.setColor(Color.LIGHT_GRAY);
        graphics.drawString(playersOnline, startX, y);
        startX += onlineWidth;
        graphics.setColor(Color.DARK_GRAY);
        graphics.drawString("/", startX, y);
        startX += slashWidth;
        graphics.setColor(Color.LIGHT_GRAY);
        graphics.drawString(playersMax, startX, y);

        return ImageUtils.resize(texture, (double) size / width);
    }

    /**
     * Get the favicon of a server.
     *
     * @param server the server to get the favicon of
     * @return the server favicon
     */
    public BufferedImage getServerFavicon(MinecraftServer server) {
        String favicon = null;

        // Get the server favicon
        if (server instanceof JavaMinecraftServer javaServer) {
            if (javaServer.getFavicon() != null) {
                favicon = javaServer.getFavicon().getBase64();
            }
        }

        // Fallback to the default server icon
        if (favicon == null) {
            favicon = ServerService.DEFAULT_SERVER_ICON;
        }
        return ImageUtils.base64ToImage(favicon);
    }
}