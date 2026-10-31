package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.render.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChoiceRow<T> extends Row {

    private final float totalWidth;
    private final float buttonHeight;
    private final float gap;

    private final List<Button> buttons = new ArrayList<>();
    private final List<T> values = new ArrayList<>();

    private T selected;
    private Consumer<T> onChange;

    public ChoiceRow(float totalWidth, float buttonHeight, float gap) {
        super(gap);
        this.totalWidth = totalWidth;
        this.buttonHeight = buttonHeight;
        this.gap = gap;
        setSize(totalWidth, buttonHeight);
    }

    public ChoiceRow<T> option(String label, T value) {
        Button button = new Button(label, 0.0f, this.buttonHeight, true);
        button.onClick(() -> {
            select(value);
            if (this.onChange != null) {
                this.onChange.accept(value);
            }
        });
        this.buttons.add(button);
        this.values.add(value);
        add(button);
        return this;
    }

    public ChoiceRow<T> onChange(Consumer<T> onChange) {
        this.onChange = onChange;
        return this;
    }

    public void select(T value) {
        this.selected = value;
        for (int i = 0; i < this.buttons.size(); i++) {
            this.buttons.get(i).setSelected(this.values.get(i).equals(value));
        }
    }

    public T getSelected() {
        return this.selected;
    }

    @Override
    public void measure(Font font) {
        int count = this.buttons.size();
        if (count > 0) {
            float buttonWidth = (this.totalWidth - this.gap * (count - 1)) / count;
            for (Button button : this.buttons) {
                button.setSize(buttonWidth, this.buttonHeight);
            }
        }
        super.measure(font);
    }
}
