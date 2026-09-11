package fr.baguettemod;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Config {
    private static final Path CONFIG_DIR = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("baguette-server-bot");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    public static String DISCORD_TOKEN = "";
    public static String CHANNEL_CHAT = "";
    public static String CHANNEL_JOINS = "";
    public static String CHANNEL_LEAVES = "";
    public static String CHANNEL_DEATHS = "";
    public static String CHANNEL_ADVANCEMENTS = "";

    public static Path configDir() {
        return CONFIG_DIR;
    }

    public static void init() {
        try {
            Files.createDirectories(CONFIG_DIR);

            if (!Files.exists(CONFIG_FILE)) {
                createDefault();
            } else {
                BaguetteMod.LOGGER.info("[BaguetteMod] Fichier de config : {}", CONFIG_FILE.toAbsolutePath());
            }
        } catch (IOException e) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Erreur lors de la creation de la config.", e);
        }
    }

    public static void load() {
        try {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(CONFIG_FILE.toFile())) {
                props.load(fis);
            }

            DISCORD_TOKEN = props.getProperty("discord_token", "").trim();
            CHANNEL_CHAT = props.getProperty("channel_chat", "").trim();
            CHANNEL_JOINS = props.getProperty("channel_joins", "").trim();
            CHANNEL_LEAVES = props.getProperty("channel_leaves", "").trim();
            CHANNEL_DEATHS = props.getProperty("channel_deaths", "").trim();
            CHANNEL_ADVANCEMENTS = props.getProperty("channel_advancements", "").trim();

            BaguetteMod.LOGGER.info("[BaguetteMod] Configuration chargee.");

            if (DISCORD_TOKEN.isEmpty()) {
                BaguetteMod.LOGGER.warn("[BaguetteMod] Le token Discord n'est pas configure !");
            }
        } catch (IOException e) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Erreur lors du chargement de la config.", e);
        }
    }

    private static void createDefault() throws IOException {
        Properties props = new Properties();
        props.setProperty("discord_token", "METTRE_TOKEN_ICI");
        props.setProperty("channel_chat", "ID_DU_SALON_CHAT");
        props.setProperty("channel_joins", "ID_DU_SALON_JOINS");
        props.setProperty("channel_leaves", "ID_DU_SALON_LEAVES");
        props.setProperty("channel_deaths", "ID_DU_SALON_DEATHS");
        props.setProperty("channel_advancements", "ID_DU_SALON_ADVANCEMENTS");

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE.toFile())) {
            props.store(fos, "Configuration Baguette Server Bot");
        }

        BaguetteMod.LOGGER.info("[BaguetteMod] Fichier de config cree : {}", CONFIG_FILE.toAbsolutePath());
    }
}
