package playtime;

import arc.ApplicationListener;
import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.util.CommandHandler;
import jdk.internal.jline.internal.Log;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.plugin.Plugin;

import java.sql.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static mindustry.Vars.player;
import static mindustry.Vars.playerGroup;

public class Main extends Plugin {
    Connection conn;
    @Override
    public void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("config/mdos/playtime/data/player.sqlite3");
            PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS `playtime` (uuid TEXT, time TEXT)");
            ps.execute();
            ps.close();

            Events.on(EventType.Trigger.update, new Runnable() {
                int tick = 0;

                @Override
                public void run() {
                    if (tick != 60) {
                        tick++;
                    } else {
                        for (Player p : playerGroup.all()) {
                            try {
                                PreparedStatement pstmt = conn.prepareStatement("SELECT time FROM playtime WHERE uuid=?");
                                pstmt.setString(1, p.uuid);
                                ResultSet rs = pstmt.executeQuery();
                                if (rs.next()) {
                                    LocalTime lt = LocalTime.parse(rs.getString("time")).plusSeconds(1);
                                    pstmt.close();
                                    rs.close();

                                    PreparedStatement pstm = conn.prepareStatement("UPDATE playtime SET time=? WHERE uuid=?");
                                    pstm.setString(1, lt.toString());
                                    pstm.setString(2, p.uuid);
                                    pstm.execute();
                                    pstm.close();
                                }
                            } catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });

            Events.on(EventType.PlayerJoin.class, event -> {
                try {
                    PreparedStatement pstmt = conn.prepareStatement("SELECT * from playtime WHERE uuid=?");
                    pstmt.setString(1, event.player.uuid);
                    ResultSet rs = pstmt.executeQuery();
                    if (!rs.next()) {
                        PreparedStatement create = conn.prepareStatement("INSERT INTO playtime VALUES(?, ?)");
                        create.setString(1, event.player.uuid);
                        create.setString(2, LocalTime.of(0, 0, 0).toString());
                        create.execute();
                    }
                } catch (Exception e){
                    e.printStackTrace();
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
            try {
                Player player = playerGroup.find(p -> p.name.equals(arg[0]));
                String uuid = player != null ? player.uuid : arg[0];
                PreparedStatement pstmt = conn.prepareStatement("SELECT time from playtime WHERE uuid=?");
                pstmt.setString(1, uuid);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    Log.info(player != null ? player.name : arg[0] + "Player playtime is " + LocalTime.parse(rs.getString("time"), DateTimeFormatter.ofPattern("HH:mm.ss")));
                } else {
                    Log.warn("Player/uuid not found!");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
