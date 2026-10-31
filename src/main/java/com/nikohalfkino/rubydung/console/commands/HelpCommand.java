package com.nikohalfkino.rubydung.console.commands;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.console.Command;
import com.nikohalfkino.rubydung.console.Console;

import java.util.List;

public class HelpCommand implements Command {

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "Lists commands, or shows help for one: help [command]";
    }

    @Override
    public void execute(RubyDung game, Console console, String[] args) {
        if (args.length > 0) {
            Command command = console.find(args[0]);
            if (command == null) {
                console.println("Unknown command: " + args[0]);
                return;
            }
            for (String line : command.help()) {
                console.println(line);
            }
            return;
        }

        List<Command> commands = console.commands();

        int maxNameLength = 0;
        for (Command command : commands) {
            maxNameLength = Math.max(maxNameLength, command.name().length());
        }

        String formatSpecifier = "%-" + maxNameLength + "s - %s";
        for (Command command : commands) {
            console.println(String.format(formatSpecifier, command.name(), command.description()));
        }
        console.println("Type '<command> help' for details on a command.");
    }
}