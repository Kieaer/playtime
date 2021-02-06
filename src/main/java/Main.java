import arc.ApplicationListener;
import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.gen.Playerc;
import mindustry.mod.Plugin;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class Main extends Plugin {
    Connection conn;
    int tick = 0;

    @Override
    public void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            Core.settings.getDataDirectory().child("mods/playtime/data").mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:./config/mods/playtime/data/player.sqlite3");
            try(PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS `playtime` (uuid TEXT, time TEXT)")) {
                ps.execute();
            }

            Events.on(EventType.Trigger.update.getClass(), e -> {
                if (tick != 60) {
                    tick++;
                } else {
                    for (Playerc p : Groups.player) {
                        try (PreparedStatement pstmt = conn.prepareStatement("SELECT time FROM playtime WHERE uuid=?")){
                            pstmt.setString(1, p.uuid());
                            try(ResultSet rs = pstmt.executeQuery()) {
                                if (rs.next()) {
                                    try(PreparedStatement pstm = conn.prepareStatement("UPDATE playtime SET time=? WHERE uuid=?")) {
                                        pstm.setLong(1, longToDateTime(rs.getLong("time")).plusSeconds(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                                        pstm.setString(2, p.uuid());
                                        pstm.execute();
                                    }
                                }
                            }
                        } catch (Exception ex){
                            ex.printStackTrace();
                        }
                    }
                }
            });

            Events.on(EventType.PlayerJoin.class, e -> {
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT * from playtime WHERE uuid=?")){
                    pstmt.setString(1, e.player.uuid());
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (!rs.next()) {
                            try (PreparedStatement create = conn.prepareStatement("INSERT INTO playtime VALUES(?, ?)")) {
                                create.setString(1, e.player.uuid());
                                create.setLong(2, 0L);
                                create.execute();
                            }
                        }
                    }
                } catch (Exception ex){
                    ex.printStackTrace();
                }
            });

            Core.app.addListener(new ApplicationListener() {
                @Override
                public void dispose() {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("playtime", "<player/uuid>", "Check player playtime", (arg) -> {
            Playerc player = Groups.player.find(p -> p.name.equals(arg[0]));
            String uuid = player != null ? player.uuid() : arg[0];
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT time from playtime WHERE uuid=?")) {
                pstmt.setString(1, uuid);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        Log.info(player != null ? player.name() + longToTime(rs.getLong("time")) : arg[0] + longToTime(rs.getLong("time")));
                    } else {
                        Log.warn("Player/uuid not found!");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Playerc>register("playtime", "Check server playtime", (arg, player) -> {
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT time from playtime WHERE uuid=?")) {
                pstmt.setString(1, player.uuid());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        player.sendMessage(player.name() + "Player playtime is " + longToTime(rs.getLong("time")));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public LocalDateTime longToDateTime(Long mils) {
        return new Timestamp(mils).toLocalDateTime();
    }

    public String longToTime(Long seconds) {
        long min = seconds / 60;
        long hour = min / 60;
        long days = hour / 24;
        return String.format("%d:%02d:%02d:%02d",
                days % 365, hour % 24, min % 60, seconds % 60);
    }
}
