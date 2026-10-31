package com.nikohalfkino.rubydung.console;

import com.nikohalfkino.rubydung.RubyDung;

import java.util.Collections;
import java.util.List;

public interface Command {

    String name();

    String description();

    default List<String> help() {
        return Collections.singletonList(description());
    }

    void execute(RubyDung game, Console console, String[] args);
}