package xyz.mcutils.backend.model.domain.skin;

import java.util.Set;

/**
 * Default Minecraft player skin texture hashes ({@code textures.minecraft.net}).
 * These skins dominate adoption counts and are excluded from trending rankings.
 */
public final class VanillaSkinTextureIds {

    public static final Set<String> ALL = Set.of(
            "31f477eb1a7beee631c2ca64d06f8f68fa93a3386d04452ab27f43acdf1b60cb",
            "a3bd16079f764cd541e072e888fe43885e711f98658323db0f9a6045da91ee7a",
            "46acd06e8483b176e8ea39fc12fe105eb3a2a4970f5100057e9d84d4b60bdfa7",
            "e5cdc3243b2153ab28a159861be643a4fc1e3c17d291cdd3e57a7f370ad676f3",
            "4c05ab9e07b3505dc3ec11370c3bdce5570ad2fb2b562e9b9dd9cf271f81aa44",
            "fece7017b1bb13926d1158864b283b8b930271f80a90482f174cca6a17e88236",
            "d5c4ee5ce20aed9e33e866c66caa37178606234b3721084bf01d13320fb2eb3f",
            "f5dddb41dcafef616e959c2817808e0be741c89ffbfed39134a13e75b811863d",
            "1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b",
            "6c160fbd16adbc4bff2409e70180d911002aebcfa811eb6ec3d1040761aea6dd",
            "90e75cd429ba6331cd210b9bd19399527ee3bab467b5a9f61cb8a27b177f6789",
            "b66bc80f002b10371e2fa23de6f230dd5e2f3affc2e15786f65bc9be4c6eb71a",
            "7cb3ba52ddd5cc82c0b050c3f920f87da36add80165846f479079663805433db",
            "eee522611005acf256dbd152e992c60c0bb7978cb0f3127807700e478ad97664",
            "226c617fde5b1ba569aa08bd2cb6fd84c93337532a872b3eb7bf66bdd5b395f8",
            "6ac6ca262d67bcfb3dbc924ba8215a18195497c780058a5749de674217721892",
            "daf3d88ccb38f11f74814e92053d92f7728ddb1a7955652a60e30cb27ae6659f",
            "1abc803022d8300ab7578b189294cce39622d9a404cdc00d3feacfdf45be6981",
            "dc0fcfaf2aa040a83dc0de4e56058d1bbb2ea40157501f3e7d15dc245e493095",
            "3b60a1f6d562f52aaebbf1434f1de147933a3affe0e764fa49ea057536623cd3"
    );

    public static boolean isVanilla(String textureId) {
        return textureId != null && ALL.contains(textureId);
    }
}
