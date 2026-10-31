package com.nikohalfkino.rubydung.console.commands;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.console.Command;
import com.nikohalfkino.rubydung.console.Console;

import java.util.Arrays;
import java.util.List;
import com.nikohalfkino.rubydung.player.Player;

public class TeleportCommand implements Command {

    @Override
    public String name() {
        return "tp";
    }

    @Override
    public String description() {
        return "Teleports you";
    }

    @Override
    public List<String> help() {
        return Arrays.asList(
                "Usage: tp <x> <y> <z>",
                "  Use ~ for your current coordinate, or ~<offset> for relative,",
                "  e.g. tp ~ ~10 ~ goes 10 blocks up.");
    }

    @Override
    public void execute(RubyDung game, Console console, String[] args) {
        if (args.length != 3) {
            console.println("Usage: tp <x> <y> <z>");
            return;
        }

        Player player = game.getPlayer();

        try {
            double x = parseCoordinate(args[0], player.x);
            double y = parseCoordinate(args[1], player.y);
            double z = parseCoordinate(args[2], player.z);

            player.setPositionAndRotation(x, y, z, player.yRotation, player.xRotation);
            console.println("Teleported to " + x + ", " + y + ", " + z);
        } catch (NumberFormatException e) {
            console.println("Coordinates must be numbers (or ~ / ~<offset> for relative)");
        }
    }

    private double parseCoordinate(String arg, double current) {
        if (arg.startsWith("~")) {
            String offset = arg.substring(1);
            return offset.isEmpty() ? current : current + Double.parseDouble(offset);
        }

        return Double.parseDouble(arg);
    }
}