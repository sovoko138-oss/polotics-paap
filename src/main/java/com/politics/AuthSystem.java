package com.politics;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = PoliticsMod.MODID)
public class AuthSystem {
    private static final int LOGIN_TIMEOUT_TICKS = 60 * 20;
    private static final int HASH_ITERATIONS = 120000;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<UUID, Long> deadline = new HashMap<>();
    private static final Map<UUID, double[]> loginPos = new HashMap<>();
    private static final Map<UUID, Boolean> authenticated = new HashMap<>();

    private static class PasswordRecord { String salt; String hash; }
    private static class AuthData extends SavedData {
        final Map<UUID, PasswordRecord> records = new HashMap<>();
        static AuthData load(CompoundTag tag) {
            AuthData d = new AuthData();
            CompoundTag users = tag.getCompound("Users");
            for (String key : users.getAllKeys()) {
                CompoundTag t = users.getCompound(key);
                PasswordRecord r = new PasswordRecord(); r.salt = t.getString("Salt"); r.hash = t.getString("Hash");
                try { d.records.put(UUID.fromString(key), r); } catch (IllegalArgumentException ignored) { }
            }
            return d;
        }
        @Override public CompoundTag save(CompoundTag tag) {
            CompoundTag users = new CompoundTag();
            for (Map.Entry<UUID, PasswordRecord> e : records.entrySet()) {
                CompoundTag t = new CompoundTag(); t.putString("Salt", e.getValue().salt); t.putString("Hash", e.getValue().hash);
                users.put(e.getKey().toString(), t);
            }
            tag.put("Users", users); return tag;
        }
    }
    private static AuthData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(AuthData::load, AuthData::new, "politics_auth");
    }
    private static String hash(String password, String salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), HASH_ITERATIONS, HASH_BITS);
        byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword(); return Base64.getEncoder().encodeToString(bytes);
    }
    private static String newSalt() { byte[] b = new byte[16]; RANDOM.nextBytes(b); return Base64.getEncoder().encodeToString(b); }
    private static boolean validPassword(String p) { return p.length() >= 4 && p.length() <= 64; }
    private static void register(ServerPlayer p, String pass, String confirm) {
        if (!pass.equals(confirm)) { p.sendSystemMessage(Component.literal("Пароли не совпадают.")); return; }
        if (!validPassword(pass)) { p.sendSystemMessage(Component.literal("Пароль должен содержать от 4 до 64 символов.")); return; }
        AuthData d = data(p.getServer());
        if (d.records.containsKey(p.getUUID())) { p.sendSystemMessage(Component.literal("Вы уже зарегистрированы. Используйте /login.")); return; }
        try { PasswordRecord r = new PasswordRecord(); r.salt = newSalt(); r.hash = hash(pass, r.salt); d.records.put(p.getUUID(), r); d.setDirty(); finish(p); }
        catch (Exception ex) { p.sendSystemMessage(Component.literal("Ошибка регистрации пароля.")); }
    }
    private static void login(ServerPlayer p, String pass) {
        PasswordRecord r = data(p.getServer()).records.get(p.getUUID());
        if (r == null) { p.sendSystemMessage(Component.literal("Сначала зарегистрируйтесь: /register пароль пароль")); return; }
        try {
            byte[] expected = Base64.getDecoder().decode(r.hash), actual = Base64.getDecoder().decode(hash(pass, r.salt));
            if (MessageDigest.isEqual(expected, actual)) finish(p);
            else p.sendSystemMessage(Component.literal("Неверный пароль."));
        } catch (Exception ex) { p.sendSystemMessage(Component.literal("Ошибка проверки пароля.")); }
    }
    private static void finish(ServerPlayer p) {
        authenticated.put(p.getUUID(), true); deadline.remove(p.getUUID()); loginPos.remove(p.getUUID());
        p.sendSystemMessage(Component.literal("Вы успешно авторизовались."));
    }
    public static boolean isAuthenticated(ServerPlayer p) { return authenticated.getOrDefault(p.getUUID(), false); }

    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("register")
                .then(Commands.argument("password", StringArgumentType.word())
                        .then(Commands.argument("repeat", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException(); register(p, StringArgumentType.getString(c,"password"), StringArgumentType.getString(c,"repeat")); return 1;
                        }))));
        e.getDispatcher().register(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException(); login(p, StringArgumentType.getString(c,"password")); return 1;
                })));
        e.getDispatcher().register(Commands.literal("changepassword")
                .then(Commands.argument("old", StringArgumentType.word()).then(Commands.argument("new", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!isAuthenticated(p)) { p.sendSystemMessage(Component.literal("Сначала авторизуйтесь.")); return 0; }
                    PasswordRecord r = data(p.getServer()).records.get(p.getUUID());
                    try {
                        if (r == null || !MessageDigest.isEqual(Base64.getDecoder().decode(r.hash), Base64.getDecoder().decode(hash(StringArgumentType.getString(c,"old"), r.salt)))) { p.sendSystemMessage(Component.literal("Старый пароль неверен.")); return 0; }
                        String next = StringArgumentType.getString(c,"new"); if (!validPassword(next)) { p.sendSystemMessage(Component.literal("Новый пароль: 4–64 символа.")); return 0; }
                        r.salt = newSalt(); r.hash = hash(next,r.salt); data(p.getServer()).setDirty(); p.sendSystemMessage(Component.literal("Пароль изменён.")); return 1;
                    } catch (Exception ex) { p.sendSystemMessage(Component.literal("Ошибка смены пароля.")); return 0; }
                }))));
    }
    @SubscribeEvent public static void loginEvent(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        authenticated.put(p.getUUID(), false); deadline.put(p.getUUID(), p.serverLevel().getGameTime() + LOGIN_TIMEOUT_TICKS);
        loginPos.put(p.getUUID(), new double[]{p.getX(),p.getY(),p.getZ()});
        if (data(p.getServer()).records.containsKey(p.getUUID())) p.sendSystemMessage(Component.literal("Войдите: /login пароль"));
        else p.sendSystemMessage(Component.literal("Зарегистрируйте пароль: /register пароль пароль"));
    }
    @SubscribeEvent public static void logoutEvent(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id=e.getEntity().getUUID(); authenticated.remove(id); deadline.remove(id); loginPos.remove(id);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || isAuthenticated(p)) return;
        Long end=deadline.get(p.getUUID()); if (end==null) return;
        if (p.serverLevel().getGameTime() > end) { p.connection.disconnect(Component.literal("Время авторизации истекло.")); return; }
        double[] pos=loginPos.get(p.getUUID()); if (pos!=null) { p.setDeltaMovement(0,0,0); p.teleportTo(pos[0],pos[1],pos[2]); }
    }
    @SubscribeEvent public static void commandGate(CommandEvent e) {
        if (!(e.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p) || isAuthenticated(p)) return;
        String raw=e.getParseResults().getReader().getString().trim().toLowerCase();
        if (!(raw.startsWith("register ") || raw.equals("register") || raw.startsWith("login ") || raw.equals("login"))) {
            e.setCanceled(true); p.sendSystemMessage(Component.literal("Сначала авторизуйтесь командой /login или /register."));
        }
    }
    @SubscribeEvent public static void chatGate(ServerChatEvent e) {
        if (!isAuthenticated(e.getPlayer())) { e.setCanceled(true); e.getPlayer().sendSystemMessage(Component.literal("Сначала авторизуйтесь.")); }
    }
    @SubscribeEvent public static void attackGate(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !isAuthenticated(p)) e.setCanceled(true);
    }
    @SubscribeEvent public static void interactionGate(PlayerInteractEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !isAuthenticated(p)) e.setCanceled(true);
    }
    @SubscribeEvent public static void breakGate(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p && !isAuthenticated(p)) e.setCanceled(true);
    }
    @SubscribeEvent public static void placeGate(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !isAuthenticated(p)) e.setCanceled(true);
    }
    @SubscribeEvent public static void travelGate(EntityTravelToDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!isAuthenticated(p)) { e.setCanceled(true); return; }
        if (Config.DISABLE_NETHER.get() && e.getDimension() == net.minecraft.world.level.Level.NETHER) {
            e.setCanceled(true); p.sendSystemMessage(Component.literal("Незер отключён администратором."));
        }
        if (Config.DISABLE_END.get() && e.getDimension() == net.minecraft.world.level.Level.END) {
            e.setCanceled(true); p.sendSystemMessage(Component.literal("Энд отключён администратором."));
        }
    }
}
